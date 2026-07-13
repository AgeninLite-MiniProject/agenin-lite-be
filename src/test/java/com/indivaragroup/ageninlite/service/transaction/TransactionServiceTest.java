package com.indivaragroup.ageninlite.service.transaction;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.TransactionDetailResponse;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
                .phoneNumber("+628111")
                .passwordHash("h")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .referredBy(uplineId)
                .build();

        passiveSeller = MstUser.builder()
                .userId(sellerId)
                .userName("Seller")
                .phoneNumber("+628111")
                .passwordHash("h")
                .role("AGENT")
                .userStatus("PASSIVE")
                .isDeleted(false)
                .referredBy(null)
                .build();

        upline = MstUser.builder()
                .userId(uplineId)
                .userName("Upline")
                .phoneNumber("+628222")
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
        when(trxCommissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
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
        when(trxCommissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
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
        when(trxCommissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
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
        when(trxCommissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
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
        when(trxCommissionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
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
        doThrow(new DataIntegrityViolationException("dup")).when(trxCommissionRepository).saveAll(any());

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
        CreateTransactionRequest overLimitRequest = CreateTransactionRequest.builder()
                .items(List.of(item(productId, 100_001)))
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
    void createTransaction_WhenBoundaryQuantity100000_ShouldPass() {
        // Arrange
        CreateTransactionRequest boundaryRequest = CreateTransactionRequest.builder()
                .items(List.of(item(productId, 100_000)))
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
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
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
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
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
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);

        var result = transactionService.listTransactions(sellerId, "SELLER", "COMPLETED", 0, 10);

        assertEquals(7L, result.getCompletedCount());
        verify(trxTransactionRepository, never()).countByUserIdAndTrxStatus(any(), any());
    }

    @Test
    void list_WhenStatusFilterIsNotCompleted_ShouldCallCountByUserIdAndTrxStatus() {
        Page<TrxTransaction> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserIdAndTrxStatus(eq(sellerId), eq("PENDING"), any())).thenReturn(page);
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(3L);

        var result = transactionService.listTransactions(sellerId, "SELLER", "PENDING", 0, 10);

        assertEquals(3L, result.getCompletedCount());
        verify(trxTransactionRepository).countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"));
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
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
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
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);

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
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        verify(trxCommissionRepository, never()).findAllByItemIdIn(any());
    }

    @Test
    void list_WhenRoleIsNull_ShouldDefaultToSeller() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, null, null, 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }

    @Test
    void list_WhenSellerRoleExplicitlyUppercase_ShouldUseAsIs() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
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
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countCompletedTransactionsBenefitingUser(eq(sellerId))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "BENEFICIARY", null, 0, 10);

        assertEquals(1, result.getTransactions().size());
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getTransactions().get(0).getSuperAgentFeeAmount()));
    }

    @Test
    void list_WhenStatusIsBlank_ShouldDefaultToNullStatus() {
        Page<TrxTransaction> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(trxTransactionRepository.findByUserId(eq(sellerId), any())).thenReturn(page);
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(eq(sellerId), any())).thenReturn(BigDecimal.ZERO);
        when(trxTransactionRepository.countByUserIdAndTrxStatus(eq(sellerId), eq("COMPLETED"))).thenReturn(0L);

        var result = transactionService.listTransactions(sellerId, "SELLER", "", 0, 10);

        assertNotNull(result);
        verify(trxTransactionRepository).findByUserId(eq(sellerId), any());
    }
}
