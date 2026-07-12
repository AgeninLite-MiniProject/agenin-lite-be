package com.indivaragroup.ageninlite.service.transaction;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.entity.TrxCommission;
import com.indivaragroup.ageninlite.entity.TrxItem;
import com.indivaragroup.ageninlite.entity.TrxTransaction;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxCommissionRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxItemRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_QUANTITY_PER_LINE = 100_000;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String USER_STATUS_PASSIVE = "PASSIVE";
    private static final String USER_STATUS_ACTIVE = "ACTIVE";
    private static final String COMMISSION_TYPE_AGENT = "AGENT_FEE";
    private static final String COMMISSION_TYPE_SUPER_AGENT = "SUPER_AGENT_FEE";

    private final TrxTransactionRepository trxTransactionRepository;
    private final TrxItemRepository trxItemRepository;
    private final ProductRepository productRepository;
    private final TrxCommissionRepository trxCommissionRepository;
    private final UserRepository userRepository;

    @Transactional
    public CompleteTransactionResponse completeTransaction(UUID requesterId, UUID trxId) {
        log.info("completeTransaction started trxId={} requesterId={}", trxId, requesterId);

        //find transaction by id
        TrxTransaction trx = trxTransactionRepository.findById(trxId)
                .orElseThrow(() -> new AppException(TransactionErrorCode.TRX_0010));

        //check if the requester is the seller
        if (!trx.getUserId().equals(requesterId)) {
            throw new AppException(TransactionErrorCode.TRX_0012);
        }

        //status must be pending
        if (!STATUS_PENDING.equals(trx.getTrxStatus())) {
            throw new AppException(TransactionErrorCode.TRX_0011);
        }

        List<TrxItem> items = trxItemRepository.findByTrxId(trxId);
        if (items.isEmpty()) {
            throw new AppException(TransactionErrorCode.TRX_0011);
        }

        //get id from each product in the trx item
        List<UUID> productIds = items.stream()
                .map(TrxItem::getProductId)
                .distinct()
                .toList();

        //get the product details
        List<MstProduct> products = productRepository.findAllById(productIds);
        Map<UUID, MstProduct> productById = products.stream()
                .collect(Collectors.toMap(MstProduct::getProductId, p -> p));

        //check each item product if its actually there and if its active
        for (TrxItem item : items) {
            MstProduct product = productById.get(item.getProductId());
            if (product == null || !USER_STATUS_ACTIVE.equals(product.getProductStatus())) {
                throw new AppException(TransactionErrorCode.TRX_0013);
            }
        }

        //check if seller actually a user
        MstUser seller = userRepository.findById(trx.getUserId())
                .orElseThrow(() -> new AppException(TransactionErrorCode.TRX_0010));

        boolean isFirstCompletion = isFirstCompletedTransaction(seller);

        //check if seller has an upline
        UUID uplineId = seller.getReferredBy();
        String superAgentName = null;
        if (uplineId != null) {
            MstUser upline = userRepository.findById(uplineId).orElse(null);
            if (upline != null) {
                superAgentName = upline.getUserName();
            } else {
                uplineId = null;
            }
        }

        List<TrxCommission> commissionsToSave = new ArrayList<>();
        List<CompleteTransactionResponse.LineCommission> lineResponses = new ArrayList<>();
        int totalRowsCreated = 0;

        for (TrxItem item : items) {
            MstProduct product = productById.get(item.getProductId());

            /*
            * for each item in transaction, calculate agent fee and super agent fee
            * insert into a list
            * the list is used later to insert altogether into commissions table
            * not one-by-one
            * */
            BigDecimal agentFeeAmount = calculateCommissionAmount(item.getProfit(), product.getAgentFee());
            commissionsToSave.add(TrxCommission.builder()
                    .itemId(item.getItemId())
                    .beneficiaryId(seller.getUserId())
                    .sourceUserId(seller.getUserId())
                    .commissionType(COMMISSION_TYPE_AGENT)
                    .feePercentage(product.getAgentFee())
                    .commissionAmount(agentFeeAmount)
                    .build());
            totalRowsCreated++;

            BigDecimal superAgentFeeAmount = BigDecimal.ZERO;
            if (uplineId != null) {
                superAgentFeeAmount = calculateCommissionAmount(item.getProfit(), product.getSuperAgentFee());
                commissionsToSave.add(TrxCommission.builder()
                        .itemId(item.getItemId())
                        .beneficiaryId(uplineId)
                        .sourceUserId(seller.getUserId())
                        .commissionType(COMMISSION_TYPE_SUPER_AGENT)
                        .feePercentage(product.getSuperAgentFee())
                        .commissionAmount(superAgentFeeAmount)
                        .build());
                totalRowsCreated++;
            }

            //this is for the json response, each item have their own response
            lineResponses.add(CompleteTransactionResponse.LineCommission.builder()
                    .itemId(item.getItemId())
                    .productName(product.getProductName())
                    .profit(item.getProfit())
                    .agentFeePercentage(product.getAgentFee())
                    .agentFeeAmount(agentFeeAmount)
                    .superAgentFeePercentage(product.getSuperAgentFee())
                    .superAgentFeeAmount(superAgentFeeAmount)
                    .build());
        }

        //insert into table trx commissions
        try {
            trxCommissionRepository.saveAll(commissionsToSave);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("TRX_9999 commission integrity violation trxId={}", trxId, e);
            throw new AppException(TransactionErrorCode.TRX_9999);
        }

        trx.setTrxStatus(STATUS_COMPLETED);
        trx.setCompletedAt(java.time.LocalDateTime.now());
        TrxTransaction savedTrx = trxTransactionRepository.save(trx);

        if (isFirstCompletion) {
            log.info("seller activated on first completion sellerId={} trxId={}", seller.getUserId(), trxId);
            seller.setUserStatus(USER_STATUS_ACTIVE);
            userRepository.save(seller);
        }

        // TODO: audit — action="TRANSACTION_COMPLETE", entityType="TRX_TRANSACTION",
        //       entityId=savedTrx.getTrxId(), payload={ before: "PENDING", after: "COMPLETED" }
        //       AND for each commission row: action="COMMISSION_PAYOUT",
        //       entityType="TRX_COMMISSION", entityId=commissionId,
        //       payload={ beneficiaryId, type, amount }

        log.info("completeTransaction succeeded trxId={} commissionsCreated={}", savedTrx.getTrxId(), totalRowsCreated);
        return CompleteTransactionResponse.builder()
                .transactionId(savedTrx.getTrxId())
                .trxStatus(savedTrx.getTrxStatus())
                .completedAt(savedTrx.getCompletedAt())
                .productName(productById.get(items.getFirst().getProductId()).getProductName())
                .amount(savedTrx.getTotalAmount())
                .profit(savedTrx.getTotalProfit())
                .commissionsCreated(totalRowsCreated)
                .superAgentName(superAgentName)
                .commissions(lineResponses)
                .build();
    }

    private BigDecimal calculateCommissionAmount(BigDecimal profit, BigDecimal feePercentage) {
        return profit
                .multiply(feePercentage)
                .setScale(2, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private boolean isFirstCompletedTransaction(MstUser seller) {
        return USER_STATUS_PASSIVE.equals(seller.getUserStatus());
    }

    public CreateTransactionResponse createTransaction(UUID sellerId, CreateTransactionRequest request) {
        log.info("createTransaction started sellerId={} itemCount={}", sellerId, request.getItems().size());

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

        log.info("createTransaction succeeded trxId={} sellerId={} totalAmount={} totalProfit={}", trxId, sellerId, savedHeader.getTotalAmount(), savedHeader.getTotalProfit());
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
