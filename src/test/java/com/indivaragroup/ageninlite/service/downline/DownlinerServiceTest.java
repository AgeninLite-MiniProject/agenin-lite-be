package com.indivaragroup.ageninlite.service.downline;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.DownlinerErrorCode;
import com.indivaragroup.ageninlite.dto.downline.DownlineDetailResponseDto;
import com.indivaragroup.ageninlite.dto.downline.DownlineTransactionHistoryDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownlinerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TrxTransactionRepository trxTransactionRepository;

    @Mock
    private TrxCommissionRepository trxCommissionRepository;

    @Mock
    private TrxItemRepository trxItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DownlinerService downlinerService;

    private UUID requesterId;
    private UUID downlinerId;
    private UUID trxId;
    private UUID itemId;
    private UUID productId;
    private MstUser downliner;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        downlinerId = UUID.randomUUID();
        trxId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        productId = UUID.randomUUID();

        downliner = MstUser.builder()
                .userId(downlinerId)
                .userName("Downliner")
                .phoneNumber("08123456789")
                .email("downliner@example.com")
                .referralCode("DWN123")
                .userStatus("ACTIVE")
                .referredBy(requesterId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getDownlineDetail_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);

        when(userRepository.findById(downlinerId)).thenReturn(Optional.of(downliner));
        when(trxTransactionRepository.findLastTransactionDateByUserId(downlinerId))
                .thenReturn(LocalDateTime.now());
        
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndSourceUserIdAndCommissionType(
                requesterId, downlinerId, "SUPER_AGENT_FEE"))
                .thenReturn(new BigDecimal("15000"));

        TrxTransaction trx = TrxTransaction.builder()
                .trxId(trxId)
                .userId(downlinerId)
                .totalAmount(new BigDecimal("100000"))
                .trxStatus("COMPLETED")
                .completedAt(LocalDateTime.now())
                .build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx));
        when(trxTransactionRepository.findByUserIdOrderByCreatedAtDesc(downlinerId, pageable)).thenReturn(page);

        TrxItem item = TrxItem.builder()
                .itemId(itemId)
                .trxId(trxId)
                .productId(productId)
                .quantity(2)
                .build();
        when(trxItemRepository.findByTrxIdIn(List.of(trxId))).thenReturn(List.of(item));

        MstProduct product = MstProduct.builder()
                .productId(productId)
                .productName("Product A")
                .build();
        when(productRepository.findAllById(List.of(productId))).thenReturn(List.of(product));

        TrxCommission commission = TrxCommission.builder()
                .commissionId(UUID.randomUUID())
                .itemId(itemId)
                .commissionAmount(new BigDecimal("5000"))
                .build();
        when(trxCommissionRepository.findByBeneficiaryIdAndItemIdInAndCommissionType(
                eq(requesterId), anyCollection(), eq("SUPER_AGENT_FEE")))
                .thenReturn(List.of(commission));

        // Act
        DownlineDetailResponseDto response = downlinerService.getDownlineDetail(requesterId, downlinerId, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(downlinerId, response.getAgentDetail().getUserId());
        assertEquals(new BigDecimal("15000"), response.getProfitIncomeFromAgent());
        assertEquals(1, response.getContent().size());
        assertEquals("Product A", response.getContent().get(0).getProductName());
        assertEquals(2, response.getContent().get(0).getQuantity());
        assertEquals(new BigDecimal("5000"), response.getContent().get(0).getCommissionEarned());
        
        verify(userRepository).findById(downlinerId);
    }

    @Test
    void getDownlineDetail_UserNotFound_ThrowsException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findById(downlinerId)).thenReturn(Optional.empty());

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                downlinerService.getDownlineDetail(requesterId, downlinerId, pageable));
        assertEquals(DownlinerErrorCode.DWN_0001, ex.getErrorCode());
        
        verifyNoInteractions(trxTransactionRepository, trxCommissionRepository);
    }

    @Test
    void getDownlineDetail_NotDirectDownliner_ThrowsException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        downliner.setReferredBy(UUID.randomUUID()); // Different referrer
        when(userRepository.findById(downlinerId)).thenReturn(Optional.of(downliner));

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                downlinerService.getDownlineDetail(requesterId, downlinerId, pageable));
        assertEquals(DownlinerErrorCode.DWN_0002, ex.getErrorCode());
        
        verifyNoInteractions(trxTransactionRepository, trxCommissionRepository);
    }

    @Test
    void getDownlineDetail_ReferredByNull_ThrowsException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        downliner.setReferredBy(null);
        when(userRepository.findById(downlinerId)).thenReturn(Optional.of(downliner));

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () ->
                downlinerService.getDownlineDetail(requesterId, downlinerId, pageable));
        assertEquals(DownlinerErrorCode.DWN_0002, ex.getErrorCode());
    }

    @Test
    void getDownlineDetail_NoTransactions_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findById(downlinerId)).thenReturn(Optional.of(downliner));
        when(trxTransactionRepository.findLastTransactionDateByUserId(downlinerId)).thenReturn(null);
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndSourceUserIdAndCommissionType(
                requesterId, downlinerId, "SUPER_AGENT_FEE"))
                .thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.findByUserIdOrderByCreatedAtDesc(downlinerId, pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act
        DownlineDetailResponseDto response = downlinerService.getDownlineDetail(requesterId, downlinerId, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getContent().size());
        verifyNoInteractions(trxItemRepository, productRepository);
    }

    @Test
    void getDownlineDetail_TransactionWithMultipleItems_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);

        when(userRepository.findById(downlinerId)).thenReturn(Optional.of(downliner));
        when(trxTransactionRepository.findLastTransactionDateByUserId(downlinerId)).thenReturn(LocalDateTime.now());
        
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndSourceUserIdAndCommissionType(
                requesterId, downlinerId, "SUPER_AGENT_FEE"))
                .thenReturn(new BigDecimal("15000"));

        TrxTransaction trx1 = TrxTransaction.builder()
                .trxId(trxId)
                .userId(downlinerId)
                .totalAmount(new BigDecimal("100000"))
                .trxStatus("COMPLETED")
                .completedAt(LocalDateTime.now())
                .build();
                
        TrxTransaction trx2 = TrxTransaction.builder() // Transaction with NO items
                .trxId(UUID.randomUUID())
                .userId(downlinerId)
                .totalAmount(new BigDecimal("0"))
                .trxStatus("COMPLETED")
                .completedAt(LocalDateTime.now())
                .build();
                
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx1, trx2));
        when(trxTransactionRepository.findByUserIdOrderByCreatedAtDesc(downlinerId, pageable)).thenReturn(page);

        // Multiple items for trx1
        UUID itemId2 = UUID.randomUUID();
        TrxItem item1 = TrxItem.builder()
                .itemId(itemId)
                .trxId(trxId)
                .productId(productId)
                .quantity(2)
                .build();
        TrxItem item2 = TrxItem.builder()
                .itemId(itemId2)
                .trxId(trxId)
                .productId(UUID.randomUUID()) // different product
                .quantity(1)
                .build();
                
        when(trxItemRepository.findByTrxIdIn(anyCollection())).thenReturn(List.of(item1, item2));

        MstProduct product1 = MstProduct.builder()
                .productId(productId)
                .productName("Product A")
                .build();
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1));

        TrxCommission commission = TrxCommission.builder()
                .commissionId(UUID.randomUUID())
                .itemId(itemId)
                .commissionAmount(new BigDecimal("5000"))
                .build();
        when(trxCommissionRepository.findByBeneficiaryIdAndItemIdInAndCommissionType(
                eq(requesterId), anyCollection(), eq("SUPER_AGENT_FEE")))
                .thenReturn(List.of(commission));

        // Act
        DownlineDetailResponseDto response = downlinerService.getDownlineDetail(requesterId, downlinerId, pageable);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getContent().size());
        
        // Assert trx1 (has multiple items)
        DownlineTransactionHistoryDto history1 = response.getContent().get(0);
        assertEquals("Product A dan 1 lainnya", history1.getProductName());
        assertEquals(3, history1.getQuantity()); // 2 + 1
        assertEquals(new BigDecimal("5000"), history1.getSuperAgentFeeAmount());

        // Assert trx2 (has NO items) — superAgentFeeAmount must be ZERO, not null
        DownlineTransactionHistoryDto history2 = response.getContent().get(1);
        assertEquals("No Item", history2.getProductName());
        assertEquals(0, history2.getQuantity());
        assertEquals(BigDecimal.ZERO, history2.getCommissionEarned());
        assertEquals(BigDecimal.ZERO, history2.getSuperAgentFeeAmount());
    }

    @Test
    void getDownlineDetail_PopulatesSuperAgentFeeAmountPerRow() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID itemA = UUID.randomUUID();
        BigDecimal expectedFee = new BigDecimal("5000");

        when(userRepository.findById(downlinerId)).thenReturn(Optional.of(downliner));
        when(trxTransactionRepository.findLastTransactionDateByUserId(downlinerId))
                .thenReturn(LocalDateTime.now());
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndSourceUserIdAndCommissionType(
                requesterId, downlinerId, "SUPER_AGENT_FEE"))
                .thenReturn(new BigDecimal("15000"));

        TrxTransaction trx = TrxTransaction.builder()
                .trxId(trxId).userId(downlinerId)
                .totalAmount(new BigDecimal("100000"))
                .trxStatus("COMPLETED")
                .completedAt(LocalDateTime.now())
                .build();
        when(trxTransactionRepository.findByUserIdOrderByCreatedAtDesc(downlinerId, pageable))
                .thenReturn(new PageImpl<>(List.of(trx)));

        TrxItem item = TrxItem.builder()
                .itemId(itemA).trxId(trxId).productId(productId).quantity(2)
                .build();
        when(trxItemRepository.findByTrxIdIn(List.of(trxId))).thenReturn(List.of(item));

        MstProduct product = MstProduct.builder()
                .productId(productId).productName("Product A").build();
        when(productRepository.findAllById(List.of(productId))).thenReturn(List.of(product));

        TrxCommission commission = TrxCommission.builder()
                .commissionId(UUID.randomUUID())
                .itemId(itemA)
                .commissionAmount(expectedFee)
                .build();
        when(trxCommissionRepository.findByBeneficiaryIdAndItemIdInAndCommissionType(
                eq(requesterId), anyCollection(), eq("SUPER_AGENT_FEE")))
                .thenReturn(List.of(commission));

        DownlineDetailResponseDto response = downlinerService.getDownlineDetail(
                requesterId, downlinerId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        DownlineTransactionHistoryDto row = response.getContent().get(0);
        assertEquals(expectedFee, row.getCommissionEarned(),
                "Pre-existing field must still match");
        assertEquals(expectedFee, row.getSuperAgentFeeAmount(),
                "New field must equal the pre-existing field (same data, new name)");
    }

    @Test
    void getDownlineDetail_TransactionWithNoSuperAgentFee_ZeroesSuperAgentFeeAmount() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID itemA = UUID.randomUUID();

        when(userRepository.findById(downlinerId)).thenReturn(Optional.of(downliner));
        when(trxTransactionRepository.findLastTransactionDateByUserId(downlinerId))
                .thenReturn(LocalDateTime.now());
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndSourceUserIdAndCommissionType(
                requesterId, downlinerId, "SUPER_AGENT_FEE"))
                .thenReturn(BigDecimal.ZERO);

        TrxTransaction trx = TrxTransaction.builder()
                .trxId(trxId).userId(downlinerId)
                .totalAmount(new BigDecimal("100000"))
                .trxStatus("COMPLETED")
                .completedAt(LocalDateTime.now())
                .build();
        when(trxTransactionRepository.findByUserIdOrderByCreatedAtDesc(downlinerId, pageable))
                .thenReturn(new PageImpl<>(List.of(trx)));

        TrxItem item = TrxItem.builder()
                .itemId(itemA).trxId(trxId).productId(productId).quantity(2)
                .build();
        when(trxItemRepository.findByTrxIdIn(List.of(trxId))).thenReturn(List.of(item));

        MstProduct product = MstProduct.builder()
                .productId(productId).productName("Product A").build();
        when(productRepository.findAllById(List.of(productId))).thenReturn(List.of(product));

        when(trxCommissionRepository.findByBeneficiaryIdAndItemIdInAndCommissionType(
                eq(requesterId), anyCollection(), eq("SUPER_AGENT_FEE")))
                .thenReturn(Collections.emptyList());

        DownlineDetailResponseDto response = downlinerService.getDownlineDetail(
                requesterId, downlinerId, pageable);

        DownlineTransactionHistoryDto row = response.getContent().get(0);
        assertEquals(BigDecimal.ZERO, row.getCommissionEarned());
        assertEquals(BigDecimal.ZERO, row.getSuperAgentFeeAmount(),
                "No-commission case must zero the new field, not return null");
    }
}
