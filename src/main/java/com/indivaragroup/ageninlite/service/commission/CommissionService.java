package com.indivaragroup.ageninlite.service.commission;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse.LineCommission;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.entity.TrxCommission;
import com.indivaragroup.ageninlite.entity.TrxItem;
import com.indivaragroup.ageninlite.repository.transaction.TrxCommissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    public static final String COMMISSION_TYPE_AGENT = "AGENT_FEE";
    public static final String COMMISSION_TYPE_SUPER_AGENT = "SUPER_AGENT_FEE";

    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_BENEFICIARY = "BENEFICIARY";

    private final TrxCommissionRepository trxCommissionRepository;
    private final CommissionCalculator commissionCalculator;

    public CalculationResult calculate(
            List<TrxItem> items,
            Map<UUID, MstProduct> productById,
            MstUser seller,
            MstUser upline) {

        List<TrxCommission> rows = new ArrayList<>();
        List<LineCommission> lineResponses = new ArrayList<>();

        for (TrxItem item : items) {
            MstProduct product = productById.get(item.getProductId());

            BigDecimal agentFeeAmount =
                    commissionCalculator.calculateCommissionAmount(item.getProfit(), product.getAgentFee());
            rows.add(TrxCommission.builder()
                    .itemId(item.getItemId())
                    .beneficiaryId(seller.getUserId())
                    .sourceUserId(seller.getUserId())
                    .commissionType(COMMISSION_TYPE_AGENT)
                    .feePercentage(product.getAgentFee())
                    .commissionAmount(agentFeeAmount)
                    .build());

            BigDecimal superAgentFeeAmount = BigDecimal.ZERO;
            if (upline != null) {
                superAgentFeeAmount = commissionCalculator.calculateCommissionAmount(
                        item.getProfit(), product.getSuperAgentFee());
                rows.add(TrxCommission.builder()
                        .itemId(item.getItemId())
                        .beneficiaryId(upline.getUserId())
                        .sourceUserId(seller.getUserId())
                        .commissionType(COMMISSION_TYPE_SUPER_AGENT)
                        .feePercentage(product.getSuperAgentFee())
                        .commissionAmount(superAgentFeeAmount)
                        .build());
            }

            lineResponses.add(LineCommission.builder()
                    .itemId(item.getItemId())
                    .productName(product.getProductName())
                    .profit(item.getProfit())
                    .agentFeePercentage(product.getAgentFee())
                    .agentFeeAmount(agentFeeAmount)
                    .superAgentFeePercentage(product.getSuperAgentFee())
                    .superAgentFeeAmount(superAgentFeeAmount)
                    .build());
        }

        return new CalculationResult(rows, lineResponses);
    }

    public List<TrxCommission> saveAll(List<TrxCommission> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        try {
            return trxCommissionRepository.saveAll(rows);
        } catch (DataIntegrityViolationException e) {
            log.warn("TRX_9999 commission integrity violation rows={}", rows.size(), e);
            throw new AppException(TransactionErrorCode.TRX_9999);
        }
    }

    public BigDecimal sumForViewer(
            List<TrxCommission> commissions,
            Set<UUID> itemIds,
            String commissionType,
            UUID viewerId,
            String viewerRole) {

        return commissions.stream()
                .filter(c -> itemIds.contains(c.getItemId()))
                .filter(c -> commissionType.equals(c.getCommissionType()))
                .filter(c -> ROLE_SELLER.equals(viewerRole)
                        ? viewerId.equals(c.getSourceUserId())
                        : viewerId.equals(c.getBeneficiaryId()))
                .map(TrxCommission::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal sumAgentFeeFor(UUID beneficiaryId) {
        return trxCommissionRepository
                .sumCommissionAmountByBeneficiaryIdAndCommissionType(
                        beneficiaryId, COMMISSION_TYPE_AGENT);
    }

    public BigDecimal sumSuperAgentFeeFor(UUID beneficiaryId) {
        return trxCommissionRepository
                .sumCommissionAmountByBeneficiaryIdAndCommissionType(
                        beneficiaryId, COMMISSION_TYPE_SUPER_AGENT);
    }

    public record CalculationResult(
            List<TrxCommission> rowsToSave,
            List<LineCommission> lineResponses) {
    }
}
