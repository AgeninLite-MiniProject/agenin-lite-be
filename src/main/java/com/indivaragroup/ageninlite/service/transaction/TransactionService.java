package com.indivaragroup.ageninlite.service.transaction;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionDetailResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListItemDto;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionStatusUpdateResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_QUANTITY_PER_LINE = 100_000;
    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_BENEFICIARY = "BENEFICIARY";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_FAILED = "FAILED";
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

    @Transactional
    public TransactionStatusUpdateResponse cancelTransaction(UUID requesterId, UUID trxId) {
        return terminateTransaction(requesterId, trxId, STATUS_CANCELLED, "Transaction cancelled", "transaction cancelled");
    }

    @Transactional
    public TransactionStatusUpdateResponse failTransaction(UUID requesterId, UUID trxId) {
        return terminateTransaction(requesterId, trxId, STATUS_FAILED, "Transaction failed", "transaction failed");
    }

    private TransactionListItemDto buildListItem(
            TrxTransaction trx,
            UUID viewerId,
            String viewerRole,
            List<TrxItem> items,
            Map<UUID, MstProduct> productById,
            List<TrxCommission> commissions) {

        if (items.isEmpty()) {
            return TransactionListItemDto.builder()
                    .id(trx.getTrxId())
                    .amount(trx.getTotalAmount())
                    .profit(trx.getTotalProfit())
                    .status(trx.getTrxStatus())
                    .createdAt(trx.getCreatedAt())
                    .completedAt(trx.getCompletedAt())
                    .agentFeeAmount(BigDecimal.ZERO)
                    .superAgentFeeAmount(BigDecimal.ZERO)
                    .build();
        }

        TrxItem firstItem = items.getFirst();
        MstProduct firstProduct = productById.get(firstItem.getProductId());

        BigDecimal agentFeeAmount = commissions.stream()
                .filter(c -> COMMISSION_TYPE_AGENT.equals(c.getCommissionType()))
                .filter(c -> ROLE_SELLER.equals(viewerRole)
                        ? viewerId.equals(c.getSourceUserId())
                        : viewerId.equals(c.getBeneficiaryId()))
                .map(TrxCommission::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal superAgentFeeAmount = commissions.stream()
                .filter(c -> COMMISSION_TYPE_SUPER_AGENT.equals(c.getCommissionType()))
                .filter(c -> ROLE_SELLER.equals(viewerRole)
                        ? viewerId.equals(c.getSourceUserId())
                        : viewerId.equals(c.getBeneficiaryId()))
                .map(TrxCommission::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TransactionListItemDto.builder()
                .id(trx.getTrxId())
                .productId(firstItem.getProductId())
                .productName(firstProduct != null ? firstProduct.getProductName() : null)
                .quantity(firstItem.getQuantity())
                .amount(trx.getTotalAmount())
                .profit(trx.getTotalProfit())
                .agentFeeAmount(agentFeeAmount)
                .superAgentFeeAmount(superAgentFeeAmount)
                .status(trx.getTrxStatus())
                .createdAt(trx.getCreatedAt())
                .completedAt(trx.getCompletedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public TransactionListResponse listTransactions(
            UUID requesterId, String role, String status, int page, int size) {

        String effectiveRole = (role == null || role.isBlank()) ? ROLE_SELLER : role.toUpperCase();
        if (!ROLE_SELLER.equals(effectiveRole) && !ROLE_BENEFICIARY.equals(effectiveRole)) {
            throw new AppException(TransactionErrorCode.TRX_0015);
        }
        if (size > MAX_PAGE_SIZE) {
            throw new AppException(TransactionErrorCode.TRX_0015);
        }
        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }
        if (page < 0) {
            page = 0;
        }

        String effectiveStatus = (status == null || status.isBlank()) ? null : status;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<TrxTransaction> trxPage;
        if (ROLE_SELLER.equals(effectiveRole)) {
            trxPage = (effectiveStatus == null)
                    ? trxTransactionRepository.findByUserId(requesterId, pageable)
                    : trxTransactionRepository.findByUserIdAndTrxStatus(requesterId, effectiveStatus, pageable);
        } else {
            trxPage = trxTransactionRepository.findTransactionsBenefitingUser(requesterId, effectiveStatus, pageable);
        }

        List<TrxTransaction> pageTrxs = trxPage.getContent();
        List<TransactionListItemDto> items;
        if (pageTrxs.isEmpty()) {
            items = List.of();
        } else {
            List<UUID> trxIds = pageTrxs.stream().map(TrxTransaction::getTrxId).toList();

            List<TrxItem> allItems = trxItemRepository.findByTrxIdIn(trxIds);
            Map<UUID, List<TrxItem>> itemsByTrxId = allItems.stream()
                    .collect(Collectors.groupingBy(TrxItem::getTrxId));

            List<UUID> productIds = allItems.stream()
                    .map(TrxItem::getProductId)
                    .distinct()
                    .toList();
            Map<UUID, MstProduct> productById = byId(
                    productRepository.findAllById(productIds), MstProduct::getProductId);

            List<UUID> itemIds = allItems.stream().map(TrxItem::getItemId).toList();
            List<TrxCommission> allCommissions = itemIds.isEmpty()
                    ? List.of()
                    : trxCommissionRepository.findAllByItemIdIn(itemIds);

            items = pageTrxs.stream()
                    .map(trx -> buildListItem(
                            trx,
                            requesterId,
                            effectiveRole,
                            itemsByTrxId.getOrDefault(trx.getTrxId(), List.of()),
                            productById,
                            allCommissions))
                    .toList();
        }

        BigDecimal totalAgentFee = trxCommissionRepository
                .sumCommissionAmountByBeneficiaryIdAndCommissionType(requesterId, COMMISSION_TYPE_AGENT);
        BigDecimal totalSuperAgentFee = trxCommissionRepository
                .sumCommissionAmountByBeneficiaryIdAndCommissionType(requesterId, COMMISSION_TYPE_SUPER_AGENT);
        BigDecimal totalCommission = (totalAgentFee != null ? totalAgentFee : BigDecimal.ZERO)
                .add(totalSuperAgentFee != null ? totalSuperAgentFee : BigDecimal.ZERO);

        long completedCount;
        if (ROLE_SELLER.equals(effectiveRole)) {
            if (STATUS_COMPLETED.equals(effectiveStatus)) {
                completedCount = trxPage.getTotalElements();
            } else {
                completedCount = trxTransactionRepository
                        .countByUserIdAndTrxStatus(requesterId, STATUS_COMPLETED);
            }
        } else {
            if (STATUS_COMPLETED.equals(effectiveStatus)) {
                completedCount = trxPage.getTotalElements();
            } else {
                completedCount = trxTransactionRepository
                        .countCompletedTransactionsBenefitingUser(requesterId);
            }
        }

        return TransactionListResponse.builder()
                .transactions(items)
                .totalCommission(totalCommission)
                .completedCount(completedCount)
                .page(page)
                .size(size)
                .totalElements(trxPage.getTotalElements())
                .totalPages(trxPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionDetail(UUID requesterId, UUID trxId) {
        TrxTransaction trx = trxTransactionRepository.findById(trxId)
                .orElseThrow(() -> new AppException(TransactionErrorCode.TRX_0010));

        boolean isSeller = trx.getUserId().equals(requesterId);
        boolean isBeneficiary = !isSeller && trxCommissionRepository
                .existsByBeneficiaryIdAndSourceUserId(requesterId, trx.getUserId());
        if (!isSeller && !isBeneficiary) {
            throw new AppException(TransactionErrorCode.TRX_0014);
        }

        List<TrxItem> items = trxItemRepository.findByTrxId(trxId);

        Map<UUID, MstProduct> productById;
        if (items.isEmpty()) {
            productById = Map.of();
        } else {
            List<UUID> productIds = items.stream()
                    .map(TrxItem::getProductId)
                    .distinct()
                    .toList();
            productById = byId(productRepository.findAllById(productIds), MstProduct::getProductId);
        }

        List<UUID> itemIds = items.stream().map(TrxItem::getItemId).toList();
        List<TrxCommission> commissions = itemIds.isEmpty()
                ? List.of()
                : trxCommissionRepository.findAllByItemIdIn(itemIds);

        TransactionListItemDto base = buildListItem(
                trx, requesterId, isSeller ? ROLE_SELLER : ROLE_BENEFICIARY,
                items, productById, commissions);

        MstUser seller = userRepository.findById(trx.getUserId())
                .orElseThrow(() -> new AppException(TransactionErrorCode.TRX_0010));

        List<CreateTransactionResponse.TransactionItemResponse> itemResponses = items.stream()
                .map(item -> {
                    MstProduct product = productById.get(item.getProductId());
                    return CreateTransactionResponse.TransactionItemResponse.builder()
                            .itemId(item.getItemId())
                            .productId(item.getProductId())
                            .productName(product != null ? product.getProductName() : null)
                            .quantity(item.getQuantity())
                            .itemAmount(item.getItemAmount())
                            .profit(item.getProfit())
                            .build();
                })
                .toList();

        return TransactionDetailResponse.builder()
                .id(base.getId())
                .productId(base.getProductId())
                .productName(base.getProductName())
                .quantity(base.getQuantity())
                .amount(base.getAmount())
                .profit(base.getProfit())
                .agentFeeAmount(base.getAgentFeeAmount())
                .superAgentFeeAmount(base.getSuperAgentFeeAmount())
                .status(base.getStatus())
                .createdAt(base.getCreatedAt())
                .completedAt(base.getCompletedAt())
                .description(trx.getDescription())
                .sellerId(trx.getUserId())
                .sellerName(seller.getUserName())
                .items(itemResponses)
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

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> keyFn) {
        return rows.stream().collect(Collectors.toMap(keyFn, x -> x));
    }

    private TransactionStatusUpdateResponse terminateTransaction(
            UUID requesterId, UUID trxId, String newStatus,
            String responseMessage, String logMessage) {
        TrxTransaction trx = trxTransactionRepository.findById(trxId)
                .orElseThrow(() -> new AppException(TransactionErrorCode.TRX_0010));

        if (!trx.getUserId().equals(requesterId)) {
            throw new AppException(TransactionErrorCode.TRX_0012);
        }

        if (!STATUS_PENDING.equals(trx.getTrxStatus())) {
            throw new AppException(TransactionErrorCode.TRX_0011);
        }

        trx.setTrxStatus(newStatus);
        trxTransactionRepository.save(trx);

        log.info("{} trxId={} requesterId={}", logMessage, trxId, requesterId);

        return TransactionStatusUpdateResponse.builder()
                .trxId(trxId)
                .trxStatus(newStatus)
                .message(responseMessage)
                .build();
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
        Map<UUID, MstProduct> productById = byId(products, MstProduct::getProductId);

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
