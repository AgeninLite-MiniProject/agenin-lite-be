package com.indivaragroup.ageninlite.service.transaction;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionDetailResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionListResponseV2;
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
import com.indivaragroup.ageninlite.service.commission.CommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TrxTransactionRepository trxTransactionRepository;

    @Mock
    private TrxItemRepository trxItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TrxCommissionRepository trxCommissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommissionService commissionService;

    @Mock
    private com.indivaragroup.ageninlite.service.audit.AuditService auditService;

    @InjectMocks
    private TransactionService transactionService;

    private UUID sellerId;
    private UUID requesterId;
    private UUID trxId;
    private UUID uplineId;
    private UUID productId;
    private MstUser seller;
    private MstUser passiveSeller;
    private MstUser upline;
    private MstProduct product;
    private TrxTransaction trx;
    private TrxItem item;
    private CreateTransactionRequest request;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        trxId = UUID.randomUUID();
        uplineId = UUID.randomUUID();
        productId = UUID.randomUUID();

        seller = MstUser.builder()
                .userId(sellerId)
                .userName("Seller")
                .phoneNumber("+628111111111")
                .passwordHash("h")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .referredBy(uplineId)
                .build();

        passiveSeller = MstUser.builder()
                .userId(sellerId)
                .userName("Seller")
                .phoneNumber("+628111111111")
                .passwordHash("h")
                .role("AGENT")
                .userStatus("PASSIVE")
                .isDeleted(false)
                .referredBy(null)
                .build();

        upline = MstUser.builder()
                .userId(uplineId)
                .userName("Upline")
                .phoneNumber("+628222222222")
                .passwordHash("h")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .build();

        product = MstProduct.builder()
                .productId(productId)
                .productName("Pulsa 50k")
                .costPrice(new BigDecimal("45000.00"))
                .sellingPrice(new BigDecimal("50000.00"))
                .agentFee(new BigDecimal("10.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .productStatus("ACTIVE")
                .build();

        trx = TrxTransaction.builder()
                .trxId(trxId)
                .userId(sellerId)
                .totalAmount(new BigDecimal("50000.00"))
                .totalProfit(new BigDecimal("5000.00"))
                .trxStatus("PENDING")
                .description("test")
                .build();

        item = TrxItem.builder()
                .itemId(UUID.randomUUID())
                .trxId(trxId)
                .productId(productId)
                .quantity(1)
                .itemAmount(new BigDecimal("50000.00"))
                .profit(new BigDecimal("5000.00"))
                .build();

        request = CreateTransactionRequest.builder()
                .description("lunch sale")
                .items(List.of(
                        CreateTransactionRequest.CreateTransactionItem.builder()
                                .productId(productId)
                                .quantity(1)
                                .build()
                ))
                .build();

        lenient().when(commissionService.sumForViewer(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<TrxCommission> commissions = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    Set<UUID> itemIds = invocation.getArgument(1);
                    String commissionType = invocation.getArgument(2);
                    UUID viewerId = invocation.getArgument(3);
                    String viewerRole = invocation.getArgument(4);
                    return commissions.stream()
                            .filter(c -> itemIds.contains(c.getItemId()))
                            .filter(c -> commissionType.equals(c.getCommissionType()))
                            .filter(c -> "SELLER".equals(viewerRole)
                                    ? viewerId.equals(c.getSourceUserId())
                                    : viewerId.equals(c.getBeneficiaryId()))
                            .map(TrxCommission::getCommissionAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                });
    }

    private CreateTransactionRequest.CreateTransactionItem item(UUID productId, int quantity) {
        return CreateTransactionRequest.CreateTransactionItem.builder()
                .productId(productId)
                .quantity(quantity)
                .build();
    }

    // ==================== Group 1: completeTransaction() ====================

    @Test
    void completeTransaction_WhenHappyPathWithUpline_ShouldCompleteAndBuildResponse() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(userRepository.findById(uplineId)).thenReturn(Optional.of(upline));
        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class), mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder().build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        when(commissionService.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CompleteTransactionResponse result = transactionService.completeTransaction(sellerId, trxId);

        // Assert
        assertNotNull(result);
        assertEquals(trxId, result.getTransactionId());
        assertEquals("COMPLETED", result.getTrxStatus());
        assertNotNull(result.getCompletedAt());
        assertEquals(2, result.getCommissionsCreated());
        assertEquals("Upline", result.getSuperAgentName());
        verify(userRepository, never()).save(any(MstUser.class));
    }

    @Test
    void completeTransaction_WhenTrxNotFound_ShouldThrowTrx0010() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.empty());

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(sellerId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0010, ex.getErrorCode());
        verify(trxItemRepository, never()).findByTrxId(any());
    }

    @Test
    void completeTransaction_WhenRequesterMismatch_ShouldThrowTrx0012() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(requesterId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0012, ex.getErrorCode());
        verify(trxItemRepository, never()).findByTrxId(any());
    }

    @Test
    void completeTransaction_WhenStatusNotPending_ShouldThrowTrx0011() {
        // Arrange
        trx.setTrxStatus("COMPLETED");
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(sellerId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0011, ex.getErrorCode());
    }

    @Test
    void completeTransaction_WhenNoItems_ShouldThrowTrx0011() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of());

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(sellerId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0011, ex.getErrorCode());
    }

    @Test
    void completeTransaction_WhenProductMissing_ShouldThrowTrx0013() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of());

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(sellerId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0013, ex.getErrorCode());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void completeTransaction_WhenProductNotActive_ShouldThrowTrx0013() {
        // Arrange
        product.setProductStatus("INACTIVE");
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(sellerId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0013, ex.getErrorCode());
    }

    @Test
    void completeTransaction_WhenSellerMissing_ShouldThrowTrx0010() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.empty());

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(sellerId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0010, ex.getErrorCode());
    }

    @Test
    void completeTransaction_WhenHappyPathUplineButInactiveSeller_ShouldNotWriteUser() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(userRepository.findById(uplineId)).thenReturn(Optional.of(upline));
        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class), mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder().build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        when(commissionService.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CompleteTransactionResponse result = transactionService.completeTransaction(sellerId, trxId);

        // Assert
        assertEquals(2, result.getCommissionsCreated());
        verify(userRepository, never()).save(any(MstUser.class));
    }

    @Test
    void completeTransaction_WhenFirstCompletionWithUpline_ShouldPromoteSellerToActive() {
        // Arrange
        passiveSeller.setReferredBy(uplineId);
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(passiveSeller));
        when(userRepository.findById(uplineId)).thenReturn(Optional.of(upline));
        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class), mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder().build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        when(commissionService.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(MstUser.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CompleteTransactionResponse result = transactionService.completeTransaction(sellerId, trxId);

        // Assert
        assertEquals(2, result.getCommissionsCreated());
        ArgumentCaptor<MstUser> userCaptor = ArgumentCaptor.forClass(MstUser.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("ACTIVE", userCaptor.getValue().getUserStatus());
    }

    @Test
    void completeTransaction_WhenUplineIdSetButUplineNotFound_ShouldNullUplineAndContinue() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(userRepository.findById(uplineId)).thenReturn(Optional.empty());
        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder().build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        when(commissionService.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CompleteTransactionResponse result = transactionService.completeTransaction(sellerId, trxId);

        // Assert
        assertEquals(1, result.getCommissionsCreated());
        assertNull(result.getSuperAgentName());
    }

    @Test
    void completeTransaction_WhenNoUpline_ShouldBuildResponseWithoutSuperAgent() {
        // Arrange
        seller.setReferredBy(null);
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder().build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        when(commissionService.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CompleteTransactionResponse result = transactionService.completeTransaction(sellerId, trxId);

        // Assert
        assertEquals(1, result.getCommissionsCreated());
        assertNull(result.getSuperAgentName());
        verify(userRepository, never()).findById(uplineId);
    }

    @Test
    void completeTransaction_WhenSaveAllThrowsDataIntegrityViolation_ShouldThrowTrx9999() {
        // Arrange
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(userRepository.findById(uplineId)).thenReturn(Optional.of(upline));
        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class), mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder().build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        doThrow(new AppException(TransactionErrorCode.TRX_9999)).when(commissionService).saveAll(any());

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.completeTransaction(sellerId, trxId));

        // Assert
        assertEquals(TransactionErrorCode.TRX_9999, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any(TrxTransaction.class));
    }

    // ==================== Group 2: createTransaction() ====================

    @Test
    void createTransaction_WhenHappyPath_ShouldPersistHeaderAndItems() {
        // Arrange
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> {
            TrxTransaction t = inv.getArgument(0);
            t.setTrxId(trxId);
            return t;
        });
        when(trxItemRepository.saveAll(any())).thenAnswer(inv -> {
            List<TrxItem> list = inv.getArgument(0);
            list.forEach(it -> it.setItemId(UUID.randomUUID()));
            return list;
        });

        // Act
        CreateTransactionResponse result = transactionService.createTransaction(sellerId, request);

        // Assert
        assertNotNull(result);
        assertEquals(trxId, result.getTrxId());
        assertEquals(sellerId, result.getUserId());
        assertEquals("PENDING", result.getTrxStatus());
        assertEquals(1, result.getItems().size());
        assertEquals(0, new BigDecimal("50000.00").compareTo(result.getTotalAmount()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.getTotalProfit()));
        assertEquals("lunch sale", result.getDescription());
    }

    @Test
    void createTransaction_WhenDuplicateProductId_ShouldThrowTrx0005() {
        // Arrange
        CreateTransactionRequest dupRequest = CreateTransactionRequest.builder()
                .items(List.of(item(productId, 1), item(productId, 1)))
                .build();

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.createTransaction(sellerId, dupRequest));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0005, ex.getErrorCode());
        verify(productRepository, never()).findAllById(any());
    }

    @Test
    void createTransaction_WhenQuantityExceedsMax_ShouldThrowTrx0006() {
        // Arrange
        // MAX_QUANTITY_PER_LINE = 10 (see TransactionService line 49). Use 11 so this
        // test stays one above the limit if MAX is ever tuned to 20/50/etc.
        CreateTransactionRequest overLimitRequest = CreateTransactionRequest.builder()
                .items(List.of(item(productId, 11)))
                .build();

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.createTransaction(sellerId, overLimitRequest));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0006, ex.getErrorCode());
        verify(productRepository, never()).findAllById(any());
    }

    @Test
    void createTransaction_WhenProductNotFound_ShouldThrowTrx0001() {
        // Arrange
        when(productRepository.findAllById(any())).thenReturn(List.of());

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.createTransaction(sellerId, request));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0001, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_WhenProductNotActive_ShouldThrowTrx0002() {
        // Arrange
        product.setProductStatus("INACTIVE");
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        // Act
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.createTransaction(sellerId, request));

        // Assert
        assertEquals(TransactionErrorCode.TRX_0002, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_WhenMultipleItems_ShouldAccumulateTotalsCorrectly() {
        // Arrange
        MstProduct product2 = MstProduct.builder()
                .productId(UUID.randomUUID())
                .productName("Pulsa 100k")
                .costPrice(new BigDecimal("45000.00"))
                .sellingPrice(new BigDecimal("50000.00"))
                .agentFee(new BigDecimal("10.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .productStatus("ACTIVE")
                .build();

        CreateTransactionRequest multiRequest = CreateTransactionRequest.builder()
                .description("multi")
                .items(List.of(item(productId, 2), item(product2.getProductId(), 3)))
                .build();

        when(productRepository.findAllById(any())).thenReturn(List.of(product, product2));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> {
            TrxTransaction t = inv.getArgument(0);
            t.setTrxId(UUID.randomUUID());
            return t;
        });
        when(trxItemRepository.saveAll(any())).thenAnswer(inv -> {
            List<TrxItem> list = inv.getArgument(0);
            list.forEach(it -> it.setItemId(UUID.randomUUID()));
            return list;
        });

        // Act
        CreateTransactionResponse result = transactionService.createTransaction(sellerId, multiRequest);

        // Assert
        assertEquals(2, result.getItems().size());
        assertEquals(0, new BigDecimal("250000.00").compareTo(result.getTotalAmount()));
        assertEquals(0, new BigDecimal("25000.00").compareTo(result.getTotalProfit()));
        assertEquals("multi", result.getDescription());
    }

    @Test
    void createTransaction_WhenNullDescription_ShouldStillPersist() {
        // Arrange
        CreateTransactionRequest nullDescRequest = CreateTransactionRequest.builder()
                .description(null)
                .items(List.of(item(productId, 1)))
                .build();

        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> {
            TrxTransaction t = inv.getArgument(0);
            t.setTrxId(trxId);
            return t;
        });
        when(trxItemRepository.saveAll(any())).thenAnswer(inv -> {
            List<TrxItem> list = inv.getArgument(0);
            list.forEach(it -> it.setItemId(UUID.randomUUID()));
            return list;
        });

        // Act
        CreateTransactionResponse result = transactionService.createTransaction(sellerId, nullDescRequest);

        // Assert
        assertNotNull(result);
        assertNull(result.getDescription());
        ArgumentCaptor<TrxTransaction> headerCaptor = ArgumentCaptor.forClass(TrxTransaction.class);
        verify(trxTransactionRepository).save(headerCaptor.capture());
        assertNull(headerCaptor.getValue().getDescription());
    }

    @Test
    void createTransaction_WhenQuantityAtMax_ShouldPass() {
        // Arrange
        // MAX_QUANTITY_PER_LINE = 10 (see TransactionService line 49). 10 is the
        // upper-bound success sentinel for the createTransaction guard.
        CreateTransactionRequest boundaryRequest = CreateTransactionRequest.builder()
                .items(List.of(item(productId, 10)))
                .build();

        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> {
            TrxTransaction t = inv.getArgument(0);
            t.setTrxId(trxId);
            return t;
        });
        when(trxItemRepository.saveAll(any())).thenAnswer(inv -> {
            List<TrxItem> list = inv.getArgument(0);
            list.forEach(it -> it.setItemId(UUID.randomUUID()));
            return list;
        });

        // Act
        CreateTransactionResponse result = transactionService.createTransaction(sellerId, boundaryRequest);

        // Assert
        assertNotNull(result);
        verify(productRepository).findAllById(any());
    }

    // ==================== Group 3: cancelTransaction() ====================

    @Test
    void cancelTransaction_WhenHappyPath_ShouldUpdateStatusToCancelled() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxTransactionRepository.save(any(TrxTransaction.class))).thenAnswer(i -> i.getArguments()[0]);

        var result = transactionService.cancelTransaction(sellerId, trxId);

        assertNotNull(result);
        assertEquals(trxId, result.getTrxId());
        assertEquals("CANCELLED", result.getTrxStatus());
        ArgumentCaptor<TrxTransaction> captor = ArgumentCaptor.forClass(TrxTransaction.class);
        verify(trxTransactionRepository).save(captor.capture());
        assertEquals("CANCELLED", captor.getValue().getTrxStatus());
    }

    @Test
    void cancelTransaction_WhenTrxNotFound_ShouldThrowTrx0010() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.cancelTransaction(sellerId, trxId));
        assertEquals(TransactionErrorCode.TRX_0010, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any(TrxTransaction.class));
    }

    @Test
    void cancelTransaction_WhenRequesterMismatch_ShouldThrowTrx0012() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.cancelTransaction(requesterId, trxId));
        assertEquals(TransactionErrorCode.TRX_0012, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any(TrxTransaction.class));
    }

    @Test
    void cancelTransaction_WhenStatusNotPending_ShouldThrowTrx0011() {
        trx.setTrxStatus("COMPLETED");
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.cancelTransaction(sellerId, trxId));
        assertEquals(TransactionErrorCode.TRX_0011, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any(TrxTransaction.class));
    }

    // ==================== Group 4: failTransaction() ====================

    @Test
    void failTransaction_WhenHappyPath_ShouldUpdateStatusToFailed() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxTransactionRepository.save(any(TrxTransaction.class))).thenAnswer(i -> i.getArguments()[0]);

        var result = transactionService.failTransaction(sellerId, trxId);

        assertNotNull(result);
        assertEquals(trxId, result.getTrxId());
        assertEquals("FAILED", result.getTrxStatus());
        ArgumentCaptor<TrxTransaction> captor = ArgumentCaptor.forClass(TrxTransaction.class);
        verify(trxTransactionRepository).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getTrxStatus());
    }

    @Test
    void failTransaction_WhenTrxNotFound_ShouldThrowTrx0010() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.failTransaction(sellerId, trxId));
        assertEquals(TransactionErrorCode.TRX_0010, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any(TrxTransaction.class));
    }

    @Test
    void failTransaction_WhenRequesterMismatch_ShouldThrowTrx0012() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.failTransaction(requesterId, trxId));
        assertEquals(TransactionErrorCode.TRX_0012, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any(TrxTransaction.class));
    }

    @Test
    void failTransaction_WhenStatusNotPending_ShouldThrowTrx0011() {
        trx.setTrxStatus("CANCELLED");
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.failTransaction(sellerId, trxId));
        assertEquals(TransactionErrorCode.TRX_0011, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any(TrxTransaction.class));
    }

    // ==================== Group 5: listTransactions() ====================

    @Test
    void list_WhenSellerRoleNoStatusFilter_ShouldCallFindByUserId() {
        TrxTransaction trxInPage = trx;
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(trxInPage), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }

    @Test
    void list_WhenSellerRoleWithStatus_ShouldCallFindByUserIdAndTrxStatus() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserIdAndTrxStatus(eq(sellerId), eq("PENDING"), any())).thenReturn(page);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", "PENDING", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserIdAndTrxStatus(eq(sellerId), eq("PENDING"), any());
    }

    @Test
    void list_WhenBeneficiaryRole_ShouldCallFindTransactionsBenefitingUser() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findTransactionsBenefitingUser(eq(sellerId), eq("PENDING"), any())).thenReturn(page);
        when(trxTransactionRepository.countCompletedTransactionsBenefitingUser(eq(sellerId))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "BENEFICIARY", "PENDING", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findTransactionsBenefitingUser(eq(sellerId), eq("PENDING"), any());
    }

    @Test
    void list_WhenInvalidRole_ShouldThrowTrx0015() {
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.listTransactions(sellerId, "INVALID", null, 0, 10));
        assertEquals(TransactionErrorCode.TRX_0015, ex.getErrorCode());
    }

    @Test
    void list_WhenRoleIsBlank_ShouldDefaultToSeller() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "", null, 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }

    @Test
    void list_WhenRoleIsMixedCase_ShouldNormalizeToUpper() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "seller", null, 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }

    @Test
    void list_WhenSizeExceeds50_ShouldThrowTrx0015() {
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.listTransactions(sellerId, "SELLER", null, 0, 51));
        assertEquals(TransactionErrorCode.TRX_0015, ex.getErrorCode());
    }

    @Test
    void list_WhenSizeIsZeroOrNegative_ShouldNormalizeTo20() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        transactionService.listTransactions(sellerId, "SELLER", null, 0, 0);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), pageableCaptor.capture());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void list_WhenPageIsNegative_ShouldNormalizeTo0() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        transactionService.listTransactions(sellerId, "SELLER", null, -5, 10);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    @Test
    void list_WhenEmptyResultPage_ShouldReturnEmptyItemsAndZeroTotals() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", null, 0, 10);

        assertEquals(0, result.getTransactions().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalCommission()));
        verify(trxItemRepository, never()).findByTrxIdIn(any());
        verify(productRepository, never()).findAllById(any());
    }

    @Test
    void list_WhenStatusFilterIsCompletedAndSellerRole_ShouldUseTotalElementsForCount() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 7);
        when(trxTransactionRepository.findByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);

        var result = transactionService.listTransactions(sellerId, "SELLER", "COMPLETED", 0, 10);

        assertEquals(7L, result.getCompletedCount());
        verify(trxTransactionRepository, never()).countByUserIdAndTrxStatus(any(), any());
    }

    @Test
    void list_WhenStatusFilterIsNotCompleted_ShouldCallCountByUserIdAndTrxStatus() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserIdAndTrxStatus(eq(sellerId), eq("PENDING"), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(3L);

        var result = transactionService.listTransactions(sellerId, "SELLER", "PENDING", 0, 10);

        assertEquals(3L, result.getCompletedCount());
        verify(trxTransactionRepository).countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"));
    }

    @Test
    void list_WhenMultipleCompletedTrxForSameSeller_ShouldShowPerRowCommissionNotPageSum() {
        UUID productIdA = UUID.randomUUID();
        UUID productIdB = UUID.randomUUID();

        MstProduct productA = MstProduct.builder()
                .productId(productIdA)
                .productName("Paket Kopi")
                .costPrice(new BigDecimal("250000.00"))
                .sellingPrice(new BigDecimal("350000.00"))
                .agentFee(new BigDecimal("15.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .productStatus("ACTIVE")
                .build();
        MstProduct productB = MstProduct.builder()
                .productId(productIdB)
                .productName("Paket Susu")
                .costPrice(new BigDecimal("160000.00"))
                .sellingPrice(new BigDecimal("240000.00"))
                .agentFee(new BigDecimal("15.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .productStatus("ACTIVE")
                .build();

        UUID trxAId = UUID.randomUUID();
        UUID trxBId = UUID.randomUUID();
        TrxTransaction trxA = TrxTransaction.builder()
                .trxId(trxAId).userId(sellerId).trxStatus("COMPLETED")
                .totalAmount(new BigDecimal("350000"))
                .totalProfit(new BigDecimal("100000"))
                .build();
        TrxTransaction trxB = TrxTransaction.builder()
                .trxId(trxBId).userId(sellerId).trxStatus("COMPLETED")
                .totalAmount(new BigDecimal("240000"))
                .totalProfit(new BigDecimal("80000"))
                .build();
        Page<TrxTransaction> page = new PageImpl<>(
                List.of(trxA, trxB), PageRequest.of(0, 20), 2);

        when(trxTransactionRepository.findByUserId(eq(sellerId), any(Pageable.class)))
                .thenReturn(page);

        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        TrxItem itemARow = TrxItem.builder().itemId(itemA).trxId(trxAId)
                .productId(productIdA).quantity(1)
                .itemAmount(new BigDecimal("350000")).profit(new BigDecimal("100000"))
                .build();
        TrxItem itemBRow = TrxItem.builder().itemId(itemB).trxId(trxBId)
                .productId(productIdB).quantity(1)
                .itemAmount(new BigDecimal("240000")).profit(new BigDecimal("80000"))
                .build();
        when(trxItemRepository.findByTrxIdIn(List.of(trxAId, trxBId)))
                .thenReturn(List.of(itemARow, itemBRow));

        when(productRepository.findAllById(any())).thenReturn(List.of(productA, productB));

        TrxCommission commA = TrxCommission.builder()
                .itemId(itemA).beneficiaryId(sellerId).sourceUserId(sellerId)
                .commissionType("AGENT_FEE")
                .commissionAmount(new BigDecimal("15000"))
                .build();
        TrxCommission commB = TrxCommission.builder()
                .itemId(itemB).beneficiaryId(sellerId).sourceUserId(sellerId)
                .commissionType("AGENT_FEE")
                .commissionAmount(new BigDecimal("12000"))
                .build();
        when(trxCommissionRepository.findAllByItemIdIn(List.of(itemA, itemB)))
                .thenReturn(List.of(commA, commB));
        when(commissionService.sumAgentFeeFor(eq(sellerId)))
                .thenReturn(new BigDecimal("27000"));

        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED")))
                .thenReturn(2L);

        TransactionListResponse result = transactionService.listTransactions(
                sellerId, "SELLER", null, 0, 20);

        assertEquals(2, result.getTransactions().size());
        assertEquals(new BigDecimal("15000"), result.getTransactions().get(0).getAgentFeeAmount());
        assertEquals(new BigDecimal("12000"), result.getTransactions().get(1).getAgentFeeAmount());
        assertEquals(new BigDecimal("27000"), result.getTotalCommission());
        assertNotEquals(result.getTotalCommission(), result.getTransactions().get(0).getAgentFeeAmount());
    }

    // ==================== Group 6: getTransactionDetail() ====================

    @Test
    void getDetail_WhenRequesterIsSeller_ShouldReturnDetail() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        var result = transactionService.getTransactionDetail(sellerId, trxId);

        assertNotNull(result);
        assertEquals(trxId, result.getId());
        assertEquals(sellerId, result.getSellerId());
        assertEquals("Seller", result.getSellerName());
        assertEquals(1, result.getItems().size());
        assertEquals("Pulsa 50k", result.getProductName());
    }

    @Test
    void getDetail_WhenRequesterIsBeneficiary_ShouldReturnDetail() {
        UUID beneficiaryId = UUID.randomUUID();
        trx.setUserId(sellerId);
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxCommissionRepository.existsByBeneficiaryIdAndSourceUserId(eq(beneficiaryId), eq(sellerId))).thenReturn(true);
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        var result = transactionService.getTransactionDetail(beneficiaryId, trxId);

        assertNotNull(result);
        assertEquals(sellerId, result.getSellerId());
        assertEquals("Seller", result.getSellerName());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAgentFeeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getSuperAgentFeeAmount()));
    }

    @Test
    void getDetail_WhenTrxNotFound_ShouldThrowTrx0010() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.getTransactionDetail(sellerId, trxId));
        assertEquals(TransactionErrorCode.TRX_0010, ex.getErrorCode());
    }

    @Test
    void getDetail_WhenRequesterIsNeitherSellerNorBeneficiary_ShouldThrowTrx0014() {
        UUID randomUserId = UUID.randomUUID();
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxCommissionRepository.existsByBeneficiaryIdAndSourceUserId(eq(randomUserId), eq(sellerId))).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.getTransactionDetail(randomUserId, trxId));
        assertEquals(TransactionErrorCode.TRX_0014, ex.getErrorCode());
        verify(trxItemRepository, never()).findByTrxId(any());
    }

    @Test
    void getDetail_WhenItemsEmpty_ShouldStillReturnWithZeroFees() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of());
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        var result = transactionService.getTransactionDetail(sellerId, trxId);

        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAgentFeeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getSuperAgentFeeAmount()));
        verify(productRepository, never()).findAllById(any());
    }

    @Test
    void getDetail_WhenSellerUserNotFound_ShouldThrowTrx0010() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(userRepository.findById(sellerId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.getTransactionDetail(sellerId, trxId));
        assertEquals(TransactionErrorCode.TRX_0010, ex.getErrorCode());
    }

    @Test
    void getDetail_WhenBeneficiaryHasCommissions_ShouldPopulateAgentAndSuperAgentFees() {
        UUID beneficiaryId = UUID.randomUUID();
        TrxCommission agentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(beneficiaryId)
                .sourceUserId(sellerId)
                .commissionType("AGENT_FEE")
                .feePercentage(new BigDecimal("10.00"))
                .commissionAmount(new BigDecimal("500.00"))
                .build();
        TrxCommission superAgentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(beneficiaryId)
                .sourceUserId(sellerId)
                .commissionType("SUPER_AGENT_FEE")
                .feePercentage(new BigDecimal("5.00"))
                .commissionAmount(new BigDecimal("250.00"))
                .build();

        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxCommissionRepository.existsByBeneficiaryIdAndSourceUserId(eq(beneficiaryId), eq(sellerId))).thenReturn(true);
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(agentCommission, superAgentCommission));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        var result = transactionService.getTransactionDetail(beneficiaryId, trxId);

        assertEquals(0, new BigDecimal("500.00").compareTo(result.getAgentFeeAmount()));
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getSuperAgentFeeAmount()));
    }

    // ==================== Group 7: Additional branch coverage ====================

    @Test
    void list_WhenSellerRoleAndNonEmptyCommissions_ShouldTriggerFilterPredicates() {
        TrxTransaction trxInPage = trx;
        TrxCommission agentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(sellerId)
                .sourceUserId(sellerId)
                .commissionType("AGENT_FEE")
                .feePercentage(new BigDecimal("10.00"))
                .commissionAmount(new BigDecimal("500.00"))
                .build();
        TrxCommission superAgentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(sellerId)
                .sourceUserId(sellerId)
                .commissionType("SUPER_AGENT_FEE")
                .feePercentage(new BigDecimal("5.00"))
                .commissionAmount(new BigDecimal("250.00"))
                .build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trxInPage), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(agentCommission, superAgentCommission));
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getTransactions().get(0).getAgentFeeAmount()));
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getTransactions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void getDetail_WhenProductMissingInBatch_ShouldReturnNullProductName() {
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        var result = transactionService.getTransactionDetail(sellerId, trxId);

        assertNotNull(result);
        assertNull(result.getProductName());
        assertNull(result.getItems().get(0).getProductName());
    }

    @Test
    void list_WhenBeneficiaryRoleAndCompletedStatus_ShouldUseTotalElementsForCount() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 5);
        when(trxTransactionRepository.findTransactionsBenefitingUser(eq(sellerId), eq("COMPLETED"), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);

        var result = transactionService.listTransactions(sellerId, "BENEFICIARY", "COMPLETED", 0, 10);

        assertEquals(5L, result.getCompletedCount());
        verify(trxTransactionRepository, never()).countCompletedTransactionsBenefitingUser(any());
    }

    @Test
    void list_WhenPageHasItemsButItemsRepoReturnsEmpty_ShouldSkipCommissionFetch() {
        TrxTransaction trxInPage = trx;
        Page<TrxTransaction> page = new PageImpl<>(List.of(trxInPage), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of());
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        verify(trxCommissionRepository, never()).findAllByItemIdIn(any());
    }

    @Test
    void list_WhenRoleIsNull_ShouldDefaultToSeller() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, null, null, 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }

    @Test
    void list_WhenSellerRoleExplicitlyUppercase_ShouldUseAsIs() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", null, 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }

    @Test
    void list_WhenBeneficiaryRoleAndNonEmptyCommissions_ShouldFilterCorrectly() {
        TrxTransaction trxInPage = trx;
        TrxCommission superAgentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(sellerId)
                .sourceUserId(sellerId)
                .commissionType("SUPER_AGENT_FEE")
                .feePercentage(new BigDecimal("5.00"))
                .commissionAmount(new BigDecimal("250.00"))
                .build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trxInPage), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findTransactionsBenefitingUser(eq(sellerId), any(), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(superAgentCommission));
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countCompletedTransactionsBenefitingUser(eq(sellerId))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "BENEFICIARY", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getTransactions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void list_WhenStatusIsBlank_ShouldDefaultToNullStatus() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", "", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }

    // ==================== Group 8: listTransactionsV2() ====================

    @Test
    void listV2_WhenSellerRoleNoStatusFilter_ShouldCallFindByUserId() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        TransactionListResponseV2 result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTransactions().size());
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
        // V2 must NOT route to the V1 status-filtered repo methods either
        verify(trxTransactionRepository, never()).findByUserIdAndTrxStatus(any(), any(), any());
    }

    @Test
    void listV2_WhenSellerRoleWithStatus_ShouldCallFindByUserIdAndTrxStatus() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserIdAndTrxStatus(eq(sellerId), eq("PENDING"), any())).thenReturn(page);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", "PENDING", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserIdAndTrxStatus(eq(sellerId), eq("PENDING"), any());
    }

    @Test
    void listV2_WhenBeneficiaryRole_ShouldCallFindTransactionsBenefitingUser() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findTransactionsBenefitingUser(eq(sellerId), eq("PENDING"), any())).thenReturn(page);
        when(trxTransactionRepository.countCompletedTransactionsBenefitingUser(eq(sellerId))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "BENEFICIARY", "PENDING", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findTransactionsBenefitingUser(eq(sellerId), eq("PENDING"), any());
    }

    @Test
    void listV2_WhenInvalidRole_ShouldThrowTrx0015() {
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.listTransactionsV2(sellerId, "INVALID", null, 0, 10));
        assertEquals(TransactionErrorCode.TRX_0015, ex.getErrorCode());
    }

    @Test
    void listV2_WhenSizeExceeds50_ShouldThrowTrx0015() {
        AppException ex = assertThrows(AppException.class,
                () -> transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 51));
        assertEquals(TransactionErrorCode.TRX_0015, ex.getErrorCode());
    }

    @Test
    void listV2_WhenSizeIsZeroOrNegative_ShouldNormalizeTo20() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 0);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), pageableCaptor.capture());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void listV2_WhenPageIsNegative_ShouldNormalizeTo0() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        transactionService.listTransactionsV2(sellerId, "SELLER", null, -5, 10);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    @Test
    void listV2_WhenEmptyResultPage_ShouldReturnEmptyItemsAndZeroTotals() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        assertEquals(0, result.getTransactions().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalCommission()));
        verify(trxItemRepository, never()).findByTrxIdIn(any());
        verify(productRepository, never()).findAllById(any());
    }

    @Test
    void listV2_WhenStatusFilterIsCompletedAndSellerRole_ShouldUseTotalElementsForCount() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 7);
        when(trxTransactionRepository.findByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", "COMPLETED", 0, 10);

        assertEquals(7L, result.getCompletedCount());
        verify(trxTransactionRepository, never()).countByUserIdAndTrxStatus(any(), any());
    }

    @Test
    void listV2_WhenBeneficiaryRoleAndCompletedStatus_ShouldUseTotalElementsForCount() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 5);
        when(trxTransactionRepository.findTransactionsBenefitingUser(eq(sellerId), eq("COMPLETED"), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);

        var result = transactionService.listTransactionsV2(sellerId, "BENEFICIARY", "COMPLETED", 0, 10);

        assertEquals(5L, result.getCompletedCount());
        verify(trxTransactionRepository, never()).countCompletedTransactionsBenefitingUser(any());
    }

    @Test
    void listV2_WhenPageHasItemsButItemsRepoReturnsEmpty_ShouldSkipCommissionFetch() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of());
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        verify(trxCommissionRepository, never()).findAllByItemIdIn(any());
    }

    @Test
    void listV2_WhenMultipleItemsPerTrx_ShouldAggregateTotalQuantity() {
        // S8: totalQuantity is the sum of item quantities across all lines
        UUID itemAId = UUID.randomUUID();
        UUID itemBId = UUID.randomUUID();
        TrxItem itemA = TrxItem.builder().itemId(itemAId).trxId(trxId).productId(productId)
                .quantity(2).itemAmount(new BigDecimal("100000.00")).profit(new BigDecimal("10000.00")).build();
        TrxItem itemB = TrxItem.builder().itemId(itemBId).trxId(trxId).productId(productId)
                .quantity(3).itemAmount(new BigDecimal("150000.00")).profit(new BigDecimal("15000.00")).build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(itemA, itemB));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        assertEquals(5, result.getTransactions().get(0).getTotalQuantity());
        assertEquals(2, result.getTransactions().get(0).getItems().size());
        assertEquals(2, result.getTransactions().get(0).getItems().get(0).getQuantity());
        assertEquals(3, result.getTransactions().get(0).getItems().get(1).getQuantity());
    }

    @Test
    void listV2_WhenItemsEmptyForTrx_ShouldReturnZeroQuantityAndEmptyItems() {
        // S9: V2 DTO empty branch (items.isEmpty()) — same shape as V1 but on V2 DTO
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of());
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        assertEquals(0, result.getTransactions().get(0).getTotalQuantity());
        assertEquals(0, result.getTransactions().get(0).getItems().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTransactions().get(0).getAgentFeeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTransactions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void listV2_WhenBeneficiaryRoleAndNonEmptyCommissions_ShouldFilterCorrectly() {
        TrxCommission superAgentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(sellerId)
                .sourceUserId(sellerId)
                .commissionType("SUPER_AGENT_FEE")
                .feePercentage(new BigDecimal("5.00"))
                .commissionAmount(new BigDecimal("250.00"))
                .build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findTransactionsBenefitingUser(eq(sellerId), any(), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(superAgentCommission));
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countCompletedTransactionsBenefitingUser(eq(sellerId))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "BENEFICIARY", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getTransactions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void listV2_WhenBeneficiaryRole_ShouldMatchAgentFeeByBeneficiaryId() {
        // Covers line 507-509 false branch: role=BENEFICIARY → match c.getBeneficiaryId()
        // Includes an AGENT_FEE commission with a NON-matching sourceUserId and matching beneficiaryId
        UUID otherSellerId = UUID.randomUUID(); // different from sellerId/viewerId
        TrxCommission agentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(sellerId)        // matches viewer
                .sourceUserId(otherSellerId)    // does NOT match viewer
                .commissionType("AGENT_FEE")
                .feePercentage(new BigDecimal("10.00"))
                .commissionAmount(new BigDecimal("500.00"))
                .build();
        TrxCommission agentCommissionExcluded = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(otherSellerId)   // does NOT match viewer
                .sourceUserId(otherSellerId)
                .commissionType("AGENT_FEE")
                .feePercentage(new BigDecimal("10.00"))
                .commissionAmount(new BigDecimal("999.00"))
                .build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findTransactionsBenefitingUser(eq(sellerId), any(), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(agentCommission, agentCommissionExcluded));
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countCompletedTransactionsBenefitingUser(eq(sellerId))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "BENEFICIARY", null, 0, 10);

        // Only the matching beneficiary row contributes
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getTransactions().get(0).getAgentFeeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTransactions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void listV2_WhenSellerRole_ShouldMatchAgentFeeBySourceUserId() {
        // Covers line 507-509 true branch: role=SELLER → match c.getSourceUserId()
        // Includes an AGENT_FEE commission with a NON-matching beneficiaryId and matching sourceUserId
        UUID otherSellerId = UUID.randomUUID(); // different from sellerId/viewerId
        TrxCommission agentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(otherSellerId)   // does NOT match viewer
                .sourceUserId(sellerId)         // matches viewer (seller)
                .commissionType("AGENT_FEE")
                .feePercentage(new BigDecimal("10.00"))
                .commissionAmount(new BigDecimal("500.00"))
                .build();
        TrxCommission agentCommissionExcluded = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(otherSellerId)
                .sourceUserId(otherSellerId)    // does NOT match viewer
                .commissionType("AGENT_FEE")
                .feePercentage(new BigDecimal("10.00"))
                .commissionAmount(new BigDecimal("999.00"))
                .build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(agentCommission, agentCommissionExcluded));
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        // Only the matching sourceUserId row contributes
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getTransactions().get(0).getAgentFeeAmount()));
    }

    @Test
    void listV2_WhenSellerRole_ShouldMatchSuperAgentFeeBySourceUserId() {
        // Covers line 516-517 true branch: role=SELLER → match c.getSourceUserId() for SUPER_AGENT_FEE
        UUID otherSellerId = UUID.randomUUID();
        TrxCommission superAgentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(otherSellerId)   // does NOT match viewer
                .sourceUserId(sellerId)         // matches viewer (seller)
                .commissionType("SUPER_AGENT_FEE")
                .feePercentage(new BigDecimal("5.00"))
                .commissionAmount(new BigDecimal("250.00"))
                .build();
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(superAgentCommission));
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        assertEquals(0, new BigDecimal("250.00").compareTo(result.getTransactions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void listV2_WhenRoleIsBlankOrNull_ShouldDefaultToSeller() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var blankResult = transactionService.listTransactionsV2(sellerId, "", null, 0, 10);
        assertNotNull(blankResult);

        var nullResult = transactionService.listTransactionsV2(sellerId, null, null, 0, 10);
        assertNotNull(nullResult);

        verify(trxTransactionRepository, times(2)).findByUserId(eq(sellerId), any());
    }

    @Test
    void listV2_WhenStatusIsBlank_ShouldCallFindByUserIdNotFindByUserIdAndTrxStatus() {
        // S6 stricter version for V2: blank status must route to the no-status repo method
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", "", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
        verify(trxTransactionRepository, never()).findByUserIdAndTrxStatus(any(), any(), any());
    }

    // ==================== Group 9: Commission math (S4 + S5) ====================

    @Test
    void completeTransaction_WhenHappyPath_ShouldSnapshotCorrectCommissionAmounts() {
        // S4: verify commission calculation delegates to CommissionService
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(userRepository.findById(uplineId)).thenReturn(Optional.of(upline));

        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class), mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder()
                        .itemId(item.getItemId())
                        .productName(product.getProductName())
                        .profit(item.getProfit())
                        .agentFeePercentage(product.getAgentFee())
                        .agentFeeAmount(new BigDecimal("500.00"))
                        .superAgentFeePercentage(product.getSuperAgentFee())
                        .superAgentFeeAmount(new BigDecimal("250.00"))
                        .build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        when(commissionService.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompleteTransactionResponse result = transactionService.completeTransaction(sellerId, trxId);

        verify(commissionService).calculate(any(), any(), any(), any());
        assertEquals(2, result.getCommissionsCreated());
        assertEquals(1, result.getCommissions().size());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getCommissions().get(0).getAgentFeeAmount()));
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getCommissions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void completeTransaction_WhenItemQuantityIsLarge_ShouldMultiplyAndRoundCommissions() {
        // S5: verify commission delegation works with multi-qty item
        TrxItem multiQtyItem = TrxItem.builder()
                .itemId(UUID.randomUUID())
                .trxId(trxId)
                .productId(productId)
                .quantity(7)
                .itemAmount(new BigDecimal("350000.00"))
                .profit(new BigDecimal("35000.00"))
                .build();
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(multiQtyItem));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));
        when(userRepository.findById(uplineId)).thenReturn(Optional.of(upline));

        List<TrxCommission> expectedRows = List.of(mock(TrxCommission.class), mock(TrxCommission.class));
        List<CompleteTransactionResponse.LineCommission> expectedLineResponses = List.of(
                CompleteTransactionResponse.LineCommission.builder()
                        .itemId(multiQtyItem.getItemId())
                        .productName(product.getProductName())
                        .profit(multiQtyItem.getProfit())
                        .agentFeePercentage(product.getAgentFee())
                        .agentFeeAmount(new BigDecimal("3500.00"))
                        .superAgentFeePercentage(product.getSuperAgentFee())
                        .superAgentFeeAmount(new BigDecimal("1750.00"))
                        .build());
        when(commissionService.calculate(any(), any(), any(), any()))
                .thenReturn(new CommissionService.CalculationResult(expectedRows, expectedLineResponses));
        when(commissionService.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompleteTransactionResponse result = transactionService.completeTransaction(sellerId, trxId);

        verify(commissionService).calculate(any(), any(), any(), any());
        assertEquals(2, result.getCommissionsCreated());
        assertEquals(1, result.getCommissions().size());
        assertEquals(0, new BigDecimal("3500.00").compareTo(result.getCommissions().get(0).getAgentFeeAmount()));
        assertEquals(0, new BigDecimal("1750.00").compareTo(result.getCommissions().get(0).getSuperAgentFeeAmount()));
    }

    // ==================== Group 10: create edge cases (S2 + S3) ====================

    @Test
    void createTransaction_WhenMultiItem_ShouldPopulateItemIdsAndProductNames() {
        // S2: the response items must have non-null itemId + productName
        MstProduct product2 = MstProduct.builder()
                .productId(UUID.randomUUID())
                .productName("Pulsa 100k")
                .costPrice(new BigDecimal("45000.00"))
                .sellingPrice(new BigDecimal("50000.00"))
                .agentFee(new BigDecimal("10.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .productStatus("ACTIVE")
                .build();
        CreateTransactionRequest multiRequest = CreateTransactionRequest.builder()
                .description("multi")
                .items(List.of(item(productId, 2), item(product2.getProductId(), 3)))
                .build();
        when(productRepository.findAllById(any())).thenReturn(List.of(product, product2));
        when(trxTransactionRepository.save(any())).thenAnswer(inv -> {
            TrxTransaction t = inv.getArgument(0);
            t.setTrxId(trxId);
            return t;
        });
        when(trxItemRepository.saveAll(any())).thenAnswer(inv -> {
            List<TrxItem> list = inv.getArgument(0);
            list.forEach(it -> it.setItemId(UUID.randomUUID()));
            return list;
        });

        CreateTransactionResponse result = transactionService.createTransaction(sellerId, multiRequest);

        assertEquals(2, result.getItems().size());
        assertNotNull(result.getItems().get(0).getItemId());
        assertNotNull(result.getItems().get(1).getItemId());
        assertEquals("Pulsa 50k", result.getItems().get(0).getProductName());
        assertEquals("Pulsa 100k", result.getItems().get(1).getProductName());
    }

    @Test
    void createTransaction_WhenFetchedProductsDontCoverAllRequestItems_ShouldThrowTrx0001() {
        // S3: race condition — request has 2 productIds, DB returns only 1
        MstProduct product2 = MstProduct.builder()
                .productId(UUID.randomUUID())
                .productName("Pulsa 100k")
                .costPrice(new BigDecimal("45000.00"))
                .sellingPrice(new BigDecimal("50000.00"))
                .agentFee(new BigDecimal("10.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .productStatus("ACTIVE")
                .build();
        CreateTransactionRequest multiRequest = CreateTransactionRequest.builder()
                .items(List.of(item(productId, 1), item(product2.getProductId(), 1)))
                .build();
        when(productRepository.findAllById(any())).thenReturn(List.of(product)); // only first product

        AppException ex = assertThrows(AppException.class,
                () -> transactionService.createTransaction(sellerId, multiRequest));
        assertEquals(TransactionErrorCode.TRX_0001, ex.getErrorCode());
        verify(trxTransactionRepository, never()).save(any());
    }

    @Test
    void list_WhenStatusIsBlank_ShouldCallFindByUserIdNotFindByUserIdAndTrxStatus() {
        // S6 stricter version for V1
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", "", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
        verify(trxTransactionRepository, never()).findByUserIdAndTrxStatus(any(), any(), any());
    }

    @Test
    void getDetail_WhenRequesterIsBeneficiaryAndItemsEmpty_ShouldReturnZeroFees() {
        // S7: beneficiary branch + empty items
        UUID beneficiaryId = UUID.randomUUID();
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxCommissionRepository.existsByBeneficiaryIdAndSourceUserId(eq(beneficiaryId), eq(sellerId))).thenReturn(true);
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of());
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        var result = transactionService.getTransactionDetail(beneficiaryId, trxId);

        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAgentFeeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getSuperAgentFeeAmount()));
        assertEquals(sellerId, result.getSellerId());
    }

    @Test
    void getDetail_WhenBeneficiaryHasCommissions_ShouldPopulateItemsListShape() {
        // S10: also assert items list shape (productName, quantity) when beneficiary path active
        UUID beneficiaryId = UUID.randomUUID();
        TrxCommission agentCommission = TrxCommission.builder()
                .itemId(item.getItemId())
                .beneficiaryId(beneficiaryId)
                .sourceUserId(sellerId)
                .commissionType("AGENT_FEE")
                .feePercentage(new BigDecimal("10.00"))
                .commissionAmount(new BigDecimal("500.00"))
                .build();
        when(trxTransactionRepository.findById(trxId)).thenReturn(Optional.of(trx));
        when(trxCommissionRepository.existsByBeneficiaryIdAndSourceUserId(eq(beneficiaryId), eq(sellerId))).thenReturn(true);
        when(trxItemRepository.findByTrxId(trxId)).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of(agentCommission));
        when(userRepository.findById(sellerId)).thenReturn(Optional.of(seller));

        var result = transactionService.getTransactionDetail(beneficiaryId, trxId);

        assertEquals(1, result.getItems().size());
        assertEquals("Pulsa 50k", result.getItems().get(0).getProductName());
        assertEquals(1, result.getItems().get(0).getQuantity());
        // item's stored amount/profit come from the TrxItem fixture (50000.00 / 5000.00), not the commission amount
        assertEquals(0, new BigDecimal("50000.00").compareTo(result.getItems().get(0).getItemAmount()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.getItems().get(0).getProfit()));
    }

    @Test
    void listV2_WhenProductMissingInBatch_ShouldReturnNullProductNameInLines() {
        // S14: V2 list path — product missing from fetched batch (race condition)
        Page<TrxTransaction> page = new PageImpl<>(List.of(trx), PageRequest.of(0, 10), 1);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxItemRepository.findByTrxIdIn(any())).thenReturn(List.of(item));
        when(productRepository.findAllById(any())).thenReturn(List.of()); // product not returned
        when(trxCommissionRepository.findAllByItemIdIn(any())).thenReturn(List.of());
        when(commissionService.sumAgentFeeFor(eq(sellerId))).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactionsV2(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        assertEquals(1, result.getTransactions().get(0).getItems().size());
        assertNull(result.getTransactions().get(0).getItems().get(0).getProductName());
    }
}
