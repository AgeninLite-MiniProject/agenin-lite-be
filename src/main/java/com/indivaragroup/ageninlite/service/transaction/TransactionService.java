package com.indivaragroup.ageninlite.service.transaction;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.entity.TrxItem;
import com.indivaragroup.ageninlite.entity.TrxTransaction;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxItemRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_QUANTITY_PER_LINE = 100_000;

    private final TrxTransactionRepository trxTransactionRepository;
    private final TrxItemRepository trxItemRepository;
    private final ProductRepository productRepository;

    public CreateTransactionResponse create(UUID sellerId, CreateTransactionRequest request) {

        // === Step 2: duplicate productId check (Set-based; rejects, doesn't sum) ===
        Set<UUID> seenProductIds = new HashSet<>();
        for (var item : request.getItems()) {
            if (!seenProductIds.add(item.getProductId())) {
                throw new AppException(TransactionErrorCode.TRX_0005);
            }
        }

        // === Step 3: quantity range check (per-line max) ===
        for (var item : request.getItems()) {
            if (item.getQuantity() > MAX_QUANTITY_PER_LINE) {
                throw new AppException(TransactionErrorCode.TRX_0006);
            }
        }

        // === Step 4: fetch all products in one query (avoids N+1) ===
        List<UUID> productIds = request.getItems().stream()
                .map(CreateTransactionRequest.CreateTransactionItem::getProductId)
                .toList();
        List<MstProduct> products = productRepository.findAllById(productIds);
        Map<UUID, MstProduct> productById = products.stream()
                .collect(Collectors.toMap(MstProduct::getProductId, p -> p));

        // === Step 5: per-item existence + ACTIVE check, build line items, accumulate totals ===
        List<TrxItem> itemsToSave = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        for (var reqItem : request.getItems()) {
            MstProduct product = productById.get(reqItem.getProductId());

            if (product == null) {
                throw new AppException(TransactionErrorCode.TRX_0001);
            }
            if (!"ACTIVE".equals(product.getProductStatus())) {
                throw new AppException(TransactionErrorCode.TRX_0002);
            }

            BigDecimal itemAmount = product.getSellingPrice()
                    .multiply(BigDecimal.valueOf(reqItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal profit = product.getSellingPrice()
                    .subtract(product.getCostPrice())
                    .multiply(BigDecimal.valueOf(reqItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            totalAmount = totalAmount.add(itemAmount);
            totalProfit = totalProfit.add(profit);

            TrxItem item = TrxItem.builder()
                    .productId(product.getProductId())
                    .quantity(reqItem.getQuantity())
                    .itemAmount(itemAmount)
                    .profit(profit)
                    .build();
            itemsToSave.add(item);
        }

        // === Step 6: save header ONCE with the final totals ===
        TrxTransaction header = TrxTransaction.builder()
                .userId(sellerId)
                .totalAmount(totalAmount)
                .totalProfit(totalProfit)
                .trxStatus("PENDING")
                .description(request.getDescription())
                .build();
        TrxTransaction savedHeader = trxTransactionRepository.save(header);
        UUID trxId = savedHeader.getTrxId();

        // === Step 7: save all line items with the header's trxId ===
        for (TrxItem item : itemsToSave) {
            item.setTrxId(trxId);
        }
        List<TrxItem> savedItems = trxItemRepository.saveAll(itemsToSave);

        // === Step 8: build response ===
        // TODO: audit — action="TRANSACTION_CREATE", entityType="TRX_TRANSACTION",
        //       entityId=trxId, payload={ userId, totalAmount, itemCount }
        // Will be implemented when AuditService exists.

        List<CreateTransactionResponse.TransactionItemResponse> itemResponses =
                savedItems.stream()
                        .map(item -> {
                            MstProduct product = productById.get(item.getProductId());
                            return CreateTransactionResponse.TransactionItemResponse.builder()
                                    .itemId(item.getItemId())
                                    .productId(item.getProductId())
                                    .productName(product.getProductName())
                                    .quantity(item.getQuantity())
                                    .itemAmount(item.getItemAmount())
                                    .profit(item.getProfit())
                                    .build();
                        })
                        .toList();

        return CreateTransactionResponse.builder()
                .trxId(trxId)
                .userId(sellerId)
                .totalAmount(savedHeader.getTotalAmount())
                .totalProfit(savedHeader.getTotalProfit())
                .trxStatus(savedHeader.getTrxStatus())
                .description(savedHeader.getDescription())
                .createdAt(savedHeader.getCreatedAt())
                .items(itemResponses)
                .build();
    }


}
