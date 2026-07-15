package com.indivaragroup.ageninlite.service.downline;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.DownlinerErrorCode;
import com.indivaragroup.ageninlite.dto.downline.AgentDetailDto;
import com.indivaragroup.ageninlite.dto.downline.DownlineDetailResponseDto;
import com.indivaragroup.ageninlite.dto.downline.DownlineTransactionHistoryDto;
import com.indivaragroup.ageninlite.entity.*;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxCommissionRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxItemRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownlinerService {

    private final UserRepository userRepository;
    private final TrxTransactionRepository trxTransactionRepository;
    private final TrxCommissionRepository trxCommissionRepository;
    private final TrxItemRepository trxItemRepository;
    private final ProductRepository productRepository;

    public DownlineDetailResponseDto getDownlineDetail(UUID requesterId, UUID downlinerId, Pageable pageable) {
        log.info("Fetching downline detail for requester: {}, downliner: {}", requesterId, downlinerId);
        // validasi keberadaan downliner
        MstUser downliner = userRepository.findById(downlinerId)
                .orElseThrow(() -> new AppException(DownlinerErrorCode.DWN_0001));

        // bukan bawahan langsung
        if (downliner.getReferredBy() == null || !downliner.getReferredBy().equals(requesterId)) {
            log.warn("Access denied. User {} is not a direct downliner of {}", downlinerId, requesterId);
            throw new AppException(DownlinerErrorCode.DWN_0002);
        }

        //build object
        LocalDateTime lastTranssactionAt = trxTransactionRepository.findLastTransactionDateByUserId(downlinerId);
        AgentDetailDto agentDetail = AgentDetailDto.builder()
                .userId(downliner.getUserId())
                .userName(downliner.getUserName())
                .phoneNumber(downliner.getPhoneNumber())
                .email(downliner.getEmail())
                .referralCode(downliner.getReferralCode())
                .joinedAt(downliner.getCreatedAt().toLocalDate().toString())
                .lastTransactionAt(lastTranssactionAt != null ? lastTranssactionAt.toString() : null)
                .status(downliner.getUserStatus())
                .build();

        // total profit agen
        BigDecimal profitIncome = trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndSourceUserIdAndCommissionType(requesterId, downlinerId, "SUPER_AGENT_FEE");
        log.debug("Total profit income from downliner {}: {}", downlinerId, profitIncome);

        // paginate transaction
        Page<TrxTransaction> transactionsPage = trxTransactionRepository.findByUserIdOrderByCreatedAtDesc(downlinerId, pageable);
        List<TrxTransaction> transactions = transactionsPage.getContent();
        List<DownlineTransactionHistoryDto> historyList = new ArrayList<>();

        if (!transactions.isEmpty()) {
            List<UUID> trxIds = transactions.stream().map(TrxTransaction::getTrxId).collect(Collectors.toList());

            // batch fetch item
            List<TrxItem> allItems = trxItemRepository.findByTrxIdIn(trxIds);
            Map<UUID, List<TrxItem>> itemsByTrx = allItems.stream().collect(Collectors.groupingBy(TrxItem::getTrxId));

            List<UUID> itemIds = allItems.stream().map(TrxItem::getItemId).collect(Collectors.toList());

            // fetch product
            List<UUID> productIds = allItems.stream().map(TrxItem::getProductId).distinct().collect(Collectors.toList());
            Map<UUID, String> productNames = productRepository.findAllById(productIds).stream().collect(Collectors.toMap(MstProduct::getProductId, MstProduct::getProductName));

            // fetch commission
            List<TrxCommission> commissions = trxCommissionRepository.findByBeneficiaryIdAndItemIdInAndCommissionType(requesterId, itemIds, "SUPER_AGENT_FEE");
            Map<UUID, BigDecimal> commissionsMap = commissions.stream().collect(Collectors.groupingBy(TrxCommission::getItemId, Collectors.reducing(BigDecimal.ZERO, TrxCommission::getCommissionAmount, BigDecimal::add)));

            // build dto
            for (TrxTransaction trx : transactions) {
                List<TrxItem> trxItems = itemsByTrx.getOrDefault(trx.getTrxId(), new ArrayList<>());

                int totalQty = trxItems.stream().mapToInt(TrxItem::getQuantity).sum();

                String displayProductName = "No Item";
                if (!trxItems.isEmpty()) {
                    displayProductName = productNames.getOrDefault(trxItems.get(0).getProductId(), "Unknown");
                    if (trxItems.size() > 1) {
                        displayProductName += " dan " + (trxItems.size() - 1) + " lainnya";
                    }
                }
                BigDecimal commissionEarned = trxItems.stream()
                        .map(i -> commissionsMap.getOrDefault(i.getItemId(), BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                historyList.add(DownlineTransactionHistoryDto.builder()
                        .trxId(trx.getTrxId())
                        .productName(displayProductName)
                        .quantity(totalQty)
                        .amount(trx.getTotalAmount())
                        .status(trx.getTrxStatus())
                        .completedAt(trx.getCompletedAt())
                        .commissionEarned(commissionEarned)
                        .superAgentFeeAmount(commissionEarned)
                        .build());
            }
        }
        return DownlineDetailResponseDto.builder()
                .agentDetail(agentDetail)
                .profitIncomeFromAgent(profitIncome)
                .content(historyList)
                .totalElements(transactionsPage.getTotalElements())
                .totalPages(transactionsPage.getTotalPages())
                .build();
    }
}
