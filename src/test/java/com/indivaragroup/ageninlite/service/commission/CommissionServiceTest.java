package com.indivaragroup.ageninlite.service.commission;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse.LineCommission;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.entity.TrxCommission;
import com.indivaragroup.ageninlite.entity.TrxItem;
import com.indivaragroup.ageninlite.repository.transaction.TrxCommissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock
    private TrxCommissionRepository trxCommissionRepository;

    private CommissionCalculator commissionCalculator;
    private CommissionService commissionService;

    private UUID sellerId;
    private UUID uplineId;
    private UUID productId;
    private UUID itemId;
    private MstUser seller;
    private MstUser upline;
    private MstProduct product;
    private TrxItem item;

    @BeforeEach
    void setUp() {
        commissionCalculator = new CommissionCalculator();
        commissionService = new CommissionService(trxCommissionRepository, commissionCalculator);

        sellerId = UUID.randomUUID();
        uplineId = UUID.randomUUID();
        productId = UUID.randomUUID();
        itemId = UUID.randomUUID();

        seller = MstUser.builder()
                .userId(sellerId)
                .userName("Seller")
                .build();

        upline = MstUser.builder()
                .userId(uplineId)
                .userName("Upline")
                .build();

        product = MstProduct.builder()
                .productId(productId)
                .productName("Pulsa 50k")
                .agentFee(new BigDecimal("10.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .build();

        item = TrxItem.builder()
                .itemId(itemId)
                .productId(productId)
                .profit(new BigDecimal("5000.00"))
                .build();
    }

    @Test
    void calculateAgentFeeOnly_whenNoUpline() {
        CommissionService.CalculationResult result = commissionService.calculate(
                List.of(item), Map.of(productId, product), seller, null);

        assertEquals(1, result.rowsToSave().size());
        assertEquals(1, result.lineResponses().size());

        TrxCommission row = result.rowsToSave().get(0);
        assertEquals("AGENT_FEE", row.getCommissionType());
        assertEquals(sellerId, row.getBeneficiaryId());
        assertEquals(sellerId, row.getSourceUserId());
        assertEquals(itemId, row.getItemId());
        assertEquals(0, new BigDecimal("10.00").compareTo(row.getFeePercentage()));
        assertEquals(0, new BigDecimal("500.00").compareTo(row.getCommissionAmount()));

        LineCommission line = result.lineResponses().get(0);
        assertEquals(itemId, line.getItemId());
        assertEquals(0, new BigDecimal("500.00").compareTo(line.getAgentFeeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getSuperAgentFeeAmount()));
    }

    @Test
    void calculateAgentAndSuperAgentFee_whenUplineExists() {
        CommissionService.CalculationResult result = commissionService.calculate(
                List.of(item), Map.of(productId, product), seller, upline);

        assertEquals(2, result.rowsToSave().size());
        assertEquals(1, result.lineResponses().size());

        TrxCommission agentRow = result.rowsToSave().stream()
                .filter(r -> "AGENT_FEE".equals(r.getCommissionType()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(agentRow.getCommissionAmount()));
        assertEquals(sellerId, agentRow.getBeneficiaryId());

        TrxCommission superAgentRow = result.rowsToSave().stream()
                .filter(r -> "SUPER_AGENT_FEE".equals(r.getCommissionType()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("250.00").compareTo(superAgentRow.getCommissionAmount()));
        assertEquals(uplineId, superAgentRow.getBeneficiaryId());

        LineCommission line = result.lineResponses().get(0);
        assertEquals(0, new BigDecimal("500.00").compareTo(line.getAgentFeeAmount()));
        assertEquals(0, new BigDecimal("250.00").compareTo(line.getSuperAgentFeeAmount()));
    }

    @Test
    void sumForViewer_sellerRoleMatchesSourceUser() {
        TrxCommission agentComm = TrxCommission.builder()
                .itemId(itemId)
                .commissionType("AGENT_FEE")
                .sourceUserId(sellerId)
                .beneficiaryId(UUID.randomUUID())
                .commissionAmount(new BigDecimal("500.00"))
                .build();
        TrxCommission superAgentComm = TrxCommission.builder()
                .itemId(itemId)
                .commissionType("SUPER_AGENT_FEE")
                .sourceUserId(sellerId)
                .beneficiaryId(UUID.randomUUID())
                .commissionAmount(new BigDecimal("250.00"))
                .build();
        TrxCommission otherComm = TrxCommission.builder()
                .itemId(itemId)
                .commissionType("AGENT_FEE")
                .sourceUserId(UUID.randomUUID())
                .beneficiaryId(UUID.randomUUID())
                .commissionAmount(new BigDecimal("999.00"))
                .build();

        List<TrxCommission> commissions = List.of(agentComm, superAgentComm, otherComm);
        Set<UUID> itemIds = Set.of(itemId);

        BigDecimal agentSum = commissionService.sumForViewer(
                commissions, itemIds, "AGENT_FEE", sellerId, "SELLER");
        BigDecimal superAgentSum = commissionService.sumForViewer(
                commissions, itemIds, "SUPER_AGENT_FEE", sellerId, "SELLER");

        assertEquals(0, new BigDecimal("500.00").compareTo(agentSum));
        assertEquals(0, new BigDecimal("250.00").compareTo(superAgentSum));
    }

    @Test
    void sumForViewer_beneficiaryRoleMatchesBeneficiary() {
        TrxCommission agentComm = TrxCommission.builder()
                .itemId(itemId)
                .commissionType("AGENT_FEE")
                .sourceUserId(UUID.randomUUID())
                .beneficiaryId(sellerId)
                .commissionAmount(new BigDecimal("500.00"))
                .build();
        TrxCommission superAgentComm = TrxCommission.builder()
                .itemId(itemId)
                .commissionType("SUPER_AGENT_FEE")
                .sourceUserId(UUID.randomUUID())
                .beneficiaryId(sellerId)
                .commissionAmount(new BigDecimal("250.00"))
                .build();
        TrxCommission otherComm = TrxCommission.builder()
                .itemId(itemId)
                .commissionType("AGENT_FEE")
                .sourceUserId(UUID.randomUUID())
                .beneficiaryId(UUID.randomUUID())
                .commissionAmount(new BigDecimal("999.00"))
                .build();

        List<TrxCommission> commissions = List.of(agentComm, superAgentComm, otherComm);
        Set<UUID> itemIds = Set.of(itemId);

        BigDecimal agentSum = commissionService.sumForViewer(
                commissions, itemIds, "AGENT_FEE", sellerId, "BENEFICIARY");
        BigDecimal superAgentSum = commissionService.sumForViewer(
                commissions, itemIds, "SUPER_AGENT_FEE", sellerId, "BENEFICIARY");

        assertEquals(0, new BigDecimal("500.00").compareTo(agentSum));
        assertEquals(0, new BigDecimal("250.00").compareTo(superAgentSum));
    }

    @Test
    void saveAll_emptyList_returnsEmptyList() {
        List<TrxCommission> result = commissionService.saveAll(List.of());
        assertTrue(result.isEmpty());
        verify(trxCommissionRepository, never()).saveAll(any());
    }

    @Test
    void saveAll_withRows_delegatesToRepo() {
        TrxCommission row = TrxCommission.builder().build();
        when(trxCommissionRepository.saveAll(any())).thenReturn(List.of(row));

        List<TrxCommission> result = commissionService.saveAll(List.of(row));

        assertEquals(1, result.size());
        verify(trxCommissionRepository).saveAll(any());
    }

    @Test
    void saveAll_whenDataIntegrityViolation_throwsAppException() {
        TrxCommission row = TrxCommission.builder().build();
        when(trxCommissionRepository.saveAll(any())).thenThrow(
                new DataIntegrityViolationException("dup"));

        AppException ex = assertThrows(AppException.class,
                () -> commissionService.saveAll(List.of(row)));
        assertEquals(TransactionErrorCode.TRX_9999, ex.getErrorCode());
    }

    @Test
    void sumAgentFeeFor_delegatesToRepo() {
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(
                sellerId, "AGENT_FEE")).thenReturn(new BigDecimal("1500.00"));

        BigDecimal result = commissionService.sumAgentFeeFor(sellerId);

        assertEquals(0, new BigDecimal("1500.00").compareTo(result));
    }

    @Test
    void sumSuperAgentFeeFor_delegatesToRepo() {
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(
                sellerId, "SUPER_AGENT_FEE")).thenReturn(new BigDecimal("750.00"));

        BigDecimal result = commissionService.sumSuperAgentFeeFor(sellerId);

        assertEquals(0, new BigDecimal("750.00").compareTo(result));
    }
}
