package com.indivaragroup.ageninlite.service.commission;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.common.enums.AuditOutcome;
import com.indivaragroup.ageninlite.common.enums.CommissionType;
import com.indivaragroup.ageninlite.common.enums.ViewerRole;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse.LineCommission;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.entity.TrxCommission;
import com.indivaragroup.ageninlite.entity.TrxItem;
import com.indivaragroup.ageninlite.repository.transaction.TrxCommissionRepository;
import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.EntityType;
import com.indivaragroup.ageninlite.service.audit.AuditService;
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

    private final TrxCommissionRepository trxCommissionRepository;
    private final CommissionCalculator commissionCalculator;
    private final AuditService auditService;

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
                    .commissionType(CommissionType.AGENT_FEE.name())
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
                        .commissionType(CommissionType.SUPER_AGENT_FEE.name())
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
            List<TrxCommission> saved = trxCommissionRepository.saveAll(rows);
            for (TrxCommission comm : saved) {
                auditService.saveLog(
                    comm.getBeneficiaryId(), 
                    AuditAction.COMMISSION_PAYOUT, 
                    EntityType.COMMISSION, 
                    comm.getCommissionId(), 
                    "Commission payout of type " + comm.getCommissionType() + " with amount " + comm.getCommissionAmount(), 
                    AuditOutcome.SUCCESS.name(),
                    null, 
                    null
                );
            }
            return saved;
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
                .filter(c -> ViewerRole.SELLER.name().equals(viewerRole)
                        ? viewerId.equals(c.getSourceUserId())
                        : viewerId.equals(c.getBeneficiaryId()))
                .map(TrxCommission::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal sumAgentFeeFor(UUID beneficiaryId) {
        return trxCommissionRepository
                .sumCommissionAmountByBeneficiaryIdAndCommissionType(
                        beneficiaryId, CommissionType.AGENT_FEE.name());
    }

    public BigDecimal sumSuperAgentFeeFor(UUID beneficiaryId) {
        return trxCommissionRepository
                .sumCommissionAmountByBeneficiaryIdAndCommissionType(
                        beneficiaryId, CommissionType.SUPER_AGENT_FEE.name());
    }

    public record CalculationResult(
            List<TrxCommission> rowsToSave,
            List<LineCommission> lineResponses) {
    }
}
