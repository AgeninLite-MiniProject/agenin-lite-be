package com.indivaragroup.ageninlite.controller.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.common.exception.code.TransactionErrorCode;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.service.transaction.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionController controller;

    private UUID sellerId;
    private UUID trxId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        trxId = UUID.randomUUID();
        productId = UUID.randomUUID();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        setJwtPrincipal(sellerId.toString());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setJwtPrincipal(String principal) {
        SecurityContextHolder.clearContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CreateTransactionRequest validRequest() {
        return CreateTransactionRequest.builder()
                .description("test")
                .items(List.of(
                        CreateTransactionRequest.CreateTransactionItem.builder()
                                .productId(productId)
                                .quantity(1)
                                .build()
                ))
                .build();
    }

    private CreateTransactionResponse stubCreateResponse() {
        return CreateTransactionResponse.builder()
                .trxId(trxId)
                .userId(sellerId)
                .totalAmount(new BigDecimal("50000.00"))
                .totalProfit(new BigDecimal("5000.00"))
                .trxStatus("PENDING")
                .description("test")
                .createdAt(LocalDateTime.now())
                .items(List.of())
                .build();
    }

    private CompleteTransactionResponse stubCompleteResponse() {
        return CompleteTransactionResponse.builder()
                .transactionId(trxId)
                .trxStatus("COMPLETED")
                .completedAt(LocalDateTime.now())
                .amount(new BigDecimal("50000.00"))
                .profit(new BigDecimal("5000.00"))
                .commissionsCreated(2)
                .superAgentName("Upline")
                .commissions(List.of())
                .build();
    }

    // ==================== Group 1: create (POST /api/transactions) ====================

    @Test
    void create_WithValidBody_ShouldReturn201WithBody() throws Exception {
        // Arrange
        when(transactionService.createTransaction(any(), any())).thenReturn(stubCreateResponse());

        // Act & Assert
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction created"))
                .andExpect(jsonPath("$.data.trxId").value(trxId.toString()))
                .andExpect(jsonPath("$.data.userId").value(sellerId.toString()))
                .andExpect(jsonPath("$.data.trxStatus").value("PENDING"));
    }

    @Test
    void create_WithEmptyItems_ShouldReturn400() throws Exception {
        // Arrange
        String body = "{\"items\": []}";

        // Act & Assert
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("TRX_0004: Missing or invalid required field"));
        verify(transactionService, never()).createTransaction(any(), any());
    }

    @Test
    void create_WithNullProductId_ShouldReturn400() throws Exception {
        // Arrange
        String body = "{\"items\": [{\"productId\": null, \"quantity\": 1}]}";

        // Act & Assert
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("TRX_0004: Missing or invalid required field"));
        verify(transactionService, never()).createTransaction(any(), any());
    }

    @Test
    void create_WhenServiceThrowsTrx0001_ShouldReturn404() throws Exception {
        // Arrange
        when(transactionService.createTransaction(any(), any()))
                .thenThrow(new AppException(TransactionErrorCode.TRX_0001));

        // Act & Assert
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("TRX_0001: Product not found"));
    }

    @Test
    void create_WhenJwtPrincipalIsInvalidUuid_ShouldReturn400() throws Exception {
        // Arrange
        setJwtPrincipal("not-a-uuid");

        // Act & Assert
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).createTransaction(any(), any());
    }

    // ==================== Group 2: complete (POST /api/transactions/{id}/complete) ====================

    @Test
    void complete_WithValidPath_ShouldReturn200WithBody() throws Exception {
        // Arrange
        when(transactionService.completeTransaction(any(), any())).thenReturn(stubCompleteResponse());

        // Act & Assert
        mockMvc.perform(post("/api/transactions/{id}/complete", trxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction completed"))
                .andExpect(jsonPath("$.data.transactionId").value(trxId.toString()))
                .andExpect(jsonPath("$.data.trxStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.commissionsCreated").value(2));
    }

    @Test
    void complete_WhenServiceThrowsTrx0010_ShouldReturn404() throws Exception {
        // Arrange
        when(transactionService.completeTransaction(any(), any()))
                .thenThrow(new AppException(TransactionErrorCode.TRX_0010));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/{id}/complete", trxId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("TRX_0010: Transaction not found"));
    }

    @Test
    void complete_WhenServiceThrowsTrx0012_ShouldReturn403() throws Exception {
        // Arrange
        when(transactionService.completeTransaction(any(), any()))
                .thenThrow(new AppException(TransactionErrorCode.TRX_0012));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/{id}/complete", trxId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("TRX_0012: You can only complete your own transactions"));
    }

    @Test
    void complete_WithMalformedPathUuid_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/not-a-uuid/complete"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).completeTransaction(any(), any());
    }

    // ==================== Group 3: cancel (POST /api/transactions/{id}/cancel) ====================

    @Test
    void cancel_WithValidPath_ShouldReturn200WithBody() throws Exception {
        var response = com.indivaragroup.ageninlite.dto.transaction.TransactionStatusUpdateResponse.builder()
                .trxId(trxId)
                .trxStatus("CANCELLED")
                .message("Transaction cancelled")
                .build();
        when(transactionService.cancelTransaction(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/transactions/{id}/cancel", trxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction cancelled"))
                .andExpect(jsonPath("$.data.trxId").value(trxId.toString()))
                .andExpect(jsonPath("$.data.trxStatus").value("CANCELLED"));
    }

    @Test
    void cancel_WhenServiceThrowsTrx0010_ShouldReturn404() throws Exception {
        when(transactionService.cancelTransaction(any(), any()))
                .thenThrow(new AppException(TransactionErrorCode.TRX_0010));

        mockMvc.perform(post("/api/transactions/{id}/cancel", trxId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("TRX_0010: Transaction not found"));
    }

    @Test
    void cancel_WhenServiceThrowsTrx0012_ShouldReturn403() throws Exception {
        when(transactionService.cancelTransaction(any(), any()))
                .thenThrow(new AppException(TransactionErrorCode.TRX_0012));

        mockMvc.perform(post("/api/transactions/{id}/cancel", trxId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("TRX_0012: You can only complete your own transactions"));
    }

    @Test
    void cancel_WithMalformedPathUuid_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/transactions/not-a-uuid/cancel"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).cancelTransaction(any(), any());
    }

    // ==================== Group 4: fail (POST /api/transactions/{id}/fail) ====================

    @Test
    void fail_WithValidPath_ShouldReturn200WithBody() throws Exception {
        var response = com.indivaragroup.ageninlite.dto.transaction.TransactionStatusUpdateResponse.builder()
                .trxId(trxId)
                .trxStatus("FAILED")
                .message("Transaction failed")
                .build();
        when(transactionService.failTransaction(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/transactions/{id}/fail", trxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction failed"))
                .andExpect(jsonPath("$.data.trxStatus").value("FAILED"));
    }

    @Test
    void fail_WhenServiceThrowsTrx0010_ShouldReturn404() throws Exception {
        when(transactionService.failTransaction(any(), any()))
                .thenThrow(new AppException(TransactionErrorCode.TRX_0010));

        mockMvc.perform(post("/api/transactions/{id}/fail", trxId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("TRX_0010: Transaction not found"));
    }

    @Test
    void fail_WithMalformedPathUuid_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/transactions/not-a-uuid/fail"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).failTransaction(any(), any());
    }

    // ==================== Group 5: list (GET /api/transactions) ====================

    @Test
    void list_WithDefaultQueryParams_ShouldReturn200WithBody() throws Exception {
        var response = com.indivaragroup.ageninlite.dto.transaction.TransactionListResponse.builder()
                .transactions(List.of())
                .totalCommission(BigDecimal.ZERO)
                .completedCount(0L)
                .page(0)
                .size(20)
                .totalElements(0L)
                .totalPages(0)
                .build();
        when(transactionService.listTransactions(any(), any(), any(), anyInt(), anyInt())).thenReturn(response);

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transactions fetched"))
                .andExpect(jsonPath("$.data.totalCommission").value(0))
                .andExpect(jsonPath("$.data.completedCount").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void list_WhenSizeExceeds50_ShouldReturn400WithTrx0015() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("TRX_0015: Invalid view mode, must be SELLER or BENEFICIARY"));
        verify(transactionService, never()).listTransactions(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void list_WithMalformedJwtPrincipal_ShouldReturn400() throws Exception {
        setJwtPrincipal("not-a-uuid");

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).listTransactions(any(), any(), any(), anyInt(), anyInt());
    }

    // ==================== Group 6: getDetail (GET /api/transactions/{id}) ====================

    @Test
    void getDetail_WithValidPath_ShouldReturn200WithBody() throws Exception {
        var response = com.indivaragroup.ageninlite.dto.transaction.TransactionDetailResponse.builder()
                .id(trxId)
                .productId(productId)
                .productName("Pulsa 50k")
                .quantity(1)
                .amount(new BigDecimal("50000.00"))
                .profit(new BigDecimal("5000.00"))
                .agentFeeAmount(new BigDecimal("500.00"))
                .superAgentFeeAmount(new BigDecimal("250.00"))
                .status("COMPLETED")
                .sellerId(sellerId)
                .sellerName("Seller")
                .items(List.of())
                .build();
        when(transactionService.getTransactionDetail(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/transactions/{id}", trxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction detail fetched"))
                .andExpect(jsonPath("$.data.id").value(trxId.toString()))
                .andExpect(jsonPath("$.data.sellerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void getDetail_WhenServiceThrowsTrx0010_ShouldReturn404() throws Exception {
        when(transactionService.getTransactionDetail(any(), any()))
                .thenThrow(new AppException(TransactionErrorCode.TRX_0010));

        mockMvc.perform(get("/api/transactions/{id}", trxId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("TRX_0010: Transaction not found"));
    }

    @Test
    void getDetail_WithMalformedPathUuid_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/transactions/not-a-uuid"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).getTransactionDetail(any(), any());
    }
}
