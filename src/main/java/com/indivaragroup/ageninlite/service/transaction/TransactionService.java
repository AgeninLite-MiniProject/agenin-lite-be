package com.indivaragroup.ageninlite.service.transaction;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionDetailResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionItemLineDto;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListItemDto;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListItemV2Dto;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListResponseV2;
import com.indivaragroup.ageninlite.dto.transaction.TransactionStatusUpdateResponse;
import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.EntityType;
import com.indivaragroup.ageninlite.service.audit.AuditService;
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
import com.indivaragroup.ageninlite.service.commission.CommissionService;
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

    private static final int MAX_QUANTITY_PER_LINE = 10;
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
    private final TrxTransactionRepository trxTransactionRepository;
    private final TrxItemRepository trxItemRepository;
    private final TrxCommissionRepository trxCommissionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CommissionService commissionService;
    private final AuditService auditService;

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

        //check if seller has an upline (caller resolves — option A)
        UUID uplineId = seller.getReferredBy();
        MstUser upline = null;
        String superAgentName = null;
        if (uplineId != null) {
            upline = userRepository.findById(uplineId).orElse(null);
            if (upline != null) {
                superAgentName = upline.getUserName();
            } else {
                uplineId = null;
            }
        }

        // Delegate the entire commission block
        CommissionService.CalculationResult calc =
                commissionService.calculate(items, productById, seller, upline);
        List<TrxCommission> savedCommissions = commissionService.saveAll(calc.rowsToSave());
        int totalRowsCreated = savedCommissions.size();
        List<CompleteTransactionResponse.LineCommission> lineResponses = calc.lineResponses();

        trx.setTrxStatus(STATUS_COMPLETED);
        trx.setCompletedAt(java.time.LocalDateTime.now());
        TrxTransaction savedTrx = trxTransactionRepository.save(trx);

        if (isFirstCompletion) {
            log.info("seller activated on first completion sellerId={} trxId={}", seller.getUserId(), trxId);
            seller.setUserStatus(USER_STATUS_ACTIVE);
            userRepository.save(seller);
        }

        auditService.saveLog(requesterId, AuditAction.TRANSACTION_COMPLETE, EntityType.TRANSACTION, savedTrx.getTrxId(), "Transaction completed", "SUCCESS", null, null);

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
        return terminateTransaction(requesterId, trxId, STATUS_CANCELLED, "Transaction cancelled", "transaction cancelled", AuditAction.TRANSACTION_CANCELLED, "SUCCESS");
    }

    @Transactional
    public TransactionStatusUpdateResponse failTransaction(UUID requesterId, UUID trxId) {
        return terminateTransaction(requesterId, trxId, STATUS_FAILED, "Transaction failed", "transaction failed", AuditAction.TRANSACTION_FAILED, "FAILURE");
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
        Set<UUID> itemIds = items.stream()
                .map(TrxItem::getItemId)
                .collect(Collectors.toSet());

        BigDecimal agentFeeAmount = commissionService.sumForViewer(
                commissions, itemIds, CommissionService.COMMISSION_TYPE_AGENT,
                viewerId, viewerRole);

        BigDecimal superAgentFeeAmount = commissionService.sumForViewer(
                commissions, itemIds, CommissionService.COMMISSION_TYPE_SUPER_AGENT,
                viewerId, viewerRole);

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

        BigDecimal totalAgentFee = commissionService.sumAgentFeeFor(requesterId);
        BigDecimal totalCommission = totalAgentFee != null ? totalAgentFee : BigDecimal.ZERO;

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
    public TransactionListResponseV2 listTransactionsV2(
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
        List<TransactionListItemV2Dto> v2Items;
        if (pageTrxs.isEmpty()) {
            v2Items = List.of();
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

            v2Items = pageTrxs.stream()
                    .map(trx -> buildListItemV2(
                            trx,
                            requesterId,
                            effectiveRole,
                            itemsByTrxId.getOrDefault(trx.getTrxId(), List.of()),
                            productById,
                            allCommissions))
                    .toList();
        }

        BigDecimal totalAgentFee = commissionService.sumAgentFeeFor(requesterId);
        BigDecimal totalCommission = totalAgentFee != null ? totalAgentFee : BigDecimal.ZERO;

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

        return TransactionListResponseV2.builder()
                .transactions(v2Items)
                .totalCommission(totalCommission)
                .completedCount(completedCount)
                .page(page)
                .size(size)
                .totalElements(trxPage.getTotalElements())
                .totalPages(trxPage.getTotalPages())
                .build();
    }

    private TransactionListItemV2Dto buildListItemV2(
            TrxTransaction trx,
            UUID viewerId,
            String viewerRole,
            List<TrxItem> items,
            Map<UUID, MstProduct> productById,
            List<TrxCommission> commissions) {

        if (items.isEmpty()) {
            return TransactionListItemV2Dto.builder()
                    .id(trx.getTrxId())
                    .amount(trx.getTotalAmount())
                    .profit(trx.getTotalProfit())
                    .status(trx.getTrxStatus())
                    .createdAt(trx.getCreatedAt())
                    .completedAt(trx.getCompletedAt())
                    .agentFeeAmount(BigDecimal.ZERO)
                    .superAgentFeeAmount(BigDecimal.ZERO)
                    .totalQuantity(0)
                    .items(List.of())
                    .build();
        }

        Set<UUID> itemIds = items.stream()
                .map(TrxItem::getItemId)
                .collect(Collectors.toSet());

        BigDecimal agentFeeAmount = commissionService.sumForViewer(
                commissions, itemIds, CommissionService.COMMISSION_TYPE_AGENT,
                viewerId, viewerRole);

        BigDecimal superAgentFeeAmount = commissionService.sumForViewer(
                commissions, itemIds, CommissionService.COMMISSION_TYPE_SUPER_AGENT,
                viewerId, viewerRole);

        int totalQuantity = items.stream()
                .mapToInt(TrxItem::getQuantity)
                .sum();

        List<TransactionItemLineDto> lines = items.stream()
                .map(item -> {
                    MstProduct product = productById.get(item.getProductId());
                    return TransactionItemLineDto.builder()
                            .itemId(item.getItemId())
                            .productId(item.getProductId())
                            .productName(product != null ? product.getProductName() : null)
                            .quantity(item.getQuantity())
                            .itemAmount(item.getItemAmount())
                            .profit(item.getProfit())
                            .build();
                })
                .toList();

        return TransactionListItemV2Dto.builder()
                .id(trx.getTrxId())
                .status(trx.getTrxStatus())
                .createdAt(trx.getCreatedAt())
                .completedAt(trx.getCompletedAt())
                .amount(trx.getTotalAmount())
                .profit(trx.getTotalProfit())
                .agentFeeAmount(agentFeeAmount)
                .superAgentFeeAmount(superAgentFeeAmount)
                .totalQuantity(totalQuantity)
                .items(lines)
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

    private boolean isFirstCompletedTransaction(MstUser seller) {
        return USER_STATUS_PASSIVE.equals(seller.getUserStatus());
    }

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> keyFn) {
        return rows.stream().collect(Collectors.toMap(keyFn, x -> x));
    }

    private TransactionStatusUpdateResponse terminateTransaction(
            UUID requesterId, UUID trxId, String newStatus,
            String responseMessage, String logMessage, AuditAction auditAction, String auditStatus) {
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
        auditService.saveLog(requesterId, auditAction, EntityType.TRANSACTION, trxId, logMessage, auditStatus, null, null);

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
        auditService.saveLog(sellerId, AuditAction.TRANSACTION_CREATE, EntityType.TRANSACTION, trxId, "Transaction created with " + savedItems.size() + " items", "SUCCESS", null, null);

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
