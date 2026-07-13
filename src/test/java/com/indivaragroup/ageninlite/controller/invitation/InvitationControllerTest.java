package com.indivaragroup.ageninlite.controller.invitation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.common.exception.code.InvitationErrorCode;
import com.indivaragroup.ageninlite.dto.invitation.AcceptInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.CancelInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.DeclineInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.InvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.ReceivedInvitationListResponse;
import com.indivaragroup.ageninlite.dto.invitation.SendInvitationRequest;
import com.indivaragroup.ageninlite.dto.invitation.SentInvitationListResponse;
import com.indivaragroup.ageninlite.service.invitation.InvitationService;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvitationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private InvitationService invitationService;

    @InjectMocks
    private InvitationController controller;

    private UUID inviterId;
    private UUID inviteeId;

    @BeforeEach
    void setUp() {
        inviterId = UUID.randomUUID();
        inviteeId = UUID.randomUUID();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                inviterId.toString(),
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setJwtPrincipal(UUID newPrincipal) {
        SecurityContextHolder.clearContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                newPrincipal.toString(),
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setJwtPrincipal(String rawPrincipal) {
        SecurityContextHolder.clearContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                rawPrincipal,
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ===== Group 1: send (POST /api/invitations) =====

    @Test
    void send_WithValidBody_ShouldReturn201WithBody() throws Exception {
        // Arrange
        SendInvitationRequest request = new SendInvitationRequest(inviteeId);
        InvitationResponse response = InvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .inviteeName("Invitee Name")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .message("Invitation sent successfully")
                .build();
        when(invitationService.sendInvitation(any(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Invitation sent successfully"))
                .andExpect(jsonPath("$.data.inviterId").value(inviterId.toString()))
                .andExpect(jsonPath("$.data.inviteeId").value(inviteeId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void send_WithMissingInviteeId_ShouldReturn400() throws Exception {
        // Arrange
        String body = "{}";

        // Act & Assert
        mockMvc.perform(post("/api/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("TRX_0004: Missing or invalid required field"));
        verify(invitationService, never()).sendInvitation(any(), any());
    }

    @Test
    void send_WithNullInviteeId_ShouldReturn400() throws Exception {
        // Arrange
        String body = "{\"inviteeId\": null}";

        // Act & Assert
        mockMvc.perform(post("/api/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("TRX_0004: Missing or invalid required field"));
        verify(invitationService, never()).sendInvitation(any(), any());
    }

    @Test
    void send_WhenServiceThrowsInv0006_ShouldReturn404() throws Exception {
        // Arrange
        SendInvitationRequest request = new SendInvitationRequest(inviteeId);
        when(invitationService.sendInvitation(any(), any()))
                .thenThrow(new AppException(InvitationErrorCode.INV_0006));

        // Act & Assert
        mockMvc.perform(post("/api/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("INV_0006: Invitee not found"));
    }

    // ===== Group 2: accept (POST /api/invitations/{inviterId}/accept) =====

    @Test
    void accept_WithValidPath_ShouldReturn200WithBody() throws Exception {
        // Arrange
        setJwtPrincipal(inviteeId);
        AcceptInvitationResponse response = AcceptInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status("ACCEPTED")
                .respondedAt(LocalDateTime.now())
                .referredBy(inviterId)
                .cancelledCount(0)
                .message("Invitation accepted")
                .build();
        when(invitationService.acceptInvitation(eq(inviterId), eq(inviteeId))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/invitations/{inviterId}/accept", inviterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.cancelledCount").value(0))
                .andExpect(jsonPath("$.data.referredBy").value(inviterId.toString()));
    }

    @Test
    void accept_WhenServiceThrowsInv0013_ShouldReturn404() throws Exception {
        // Arrange
        setJwtPrincipal(inviteeId);
        when(invitationService.acceptInvitation(eq(inviterId), eq(inviteeId)))
                .thenThrow(new AppException(InvitationErrorCode.INV_0013));

        // Act & Assert
        mockMvc.perform(post("/api/invitations/{inviterId}/accept", inviterId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("INV_0013: Invitation not found"));
    }

    @Test
    void accept_WithMalformedPathUuid_ShouldReturn400() throws Exception {
        // Arrange
        setJwtPrincipal(inviteeId);

        // Act & Assert
        mockMvc.perform(post("/api/invitations/not-a-uuid/accept"))
                .andExpect(status().isBadRequest());
        verify(invitationService, never()).acceptInvitation(any(), any());
    }

    // ===== Group 3: decline (POST /api/invitations/{inviterId}/decline) =====

    @Test
    void decline_WithValidPath_ShouldReturn200WithBody() throws Exception {
        // Arrange
        setJwtPrincipal(inviteeId);
        DeclineInvitationResponse response = DeclineInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status("DECLINED")
                .respondedAt(LocalDateTime.now())
                .message("Invitation declined")
                .build();
        when(invitationService.declineInvitation(eq(inviterId), eq(inviteeId))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/invitations/{inviterId}/decline", inviterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
    }

    @Test
    void decline_WhenServiceThrowsInv0014_ShouldReturn404() throws Exception {
        // Arrange
        setJwtPrincipal(inviteeId);
        when(invitationService.declineInvitation(eq(inviterId), eq(inviteeId)))
                .thenThrow(new AppException(InvitationErrorCode.INV_0014));

        // Act & Assert
        mockMvc.perform(post("/api/invitations/{inviterId}/decline", inviterId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("INV_0014: Invitation not found"));
    }

    @Test
    void decline_WithMalformedPathUuid_ShouldReturn400() throws Exception {
        // Arrange
        setJwtPrincipal(inviteeId);

        // Act & Assert
        mockMvc.perform(post("/api/invitations/not-a-uuid/decline"))
                .andExpect(status().isBadRequest());
        verify(invitationService, never()).declineInvitation(any(), any());
    }

    // ===== Group 4: cancel (POST /api/invitations/{inviteeId}/cancel) =====

    @Test
    void cancel_WithValidPath_ShouldReturn200WithBody() throws Exception {
        // Arrange
        CancelInvitationResponse response = CancelInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status("CANCELLED")
                .cancelledAt(LocalDateTime.now())
                .message("Invitation cancelled")
                .build();
        when(invitationService.cancelInvitation(eq(inviterId), eq(inviteeId))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/invitations/{inviteeId}/cancel", inviteeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancel_WhenServiceThrowsInv0011_ShouldReturn400() throws Exception {
        // Arrange
        when(invitationService.cancelInvitation(eq(inviterId), eq(inviteeId)))
                .thenThrow(new AppException(InvitationErrorCode.INV_0011));

        // Act & Assert
        mockMvc.perform(post("/api/invitations/{inviteeId}/cancel", inviteeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INV_0011: Invitation is no longer PENDING"));
    }

    @Test
    void cancel_WithMalformedPathUuid_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/invitations/not-a-uuid/cancel"))
                .andExpect(status().isBadRequest());
        verify(invitationService, never()).cancelInvitation(any(), any());
    }

    // ===== Group 5: listSent (GET /api/invitations/sent) =====

    @Test
    void listSent_WithDefaultQueryParams_ShouldReturn200WithBody() throws Exception {
        SentInvitationListResponse response = SentInvitationListResponse.builder()
                .invitations(List.of())
                .pendingCount(0L)
                .pendingCap(3)
                .build();
        when(invitationService.listSentInvitations(eq(inviterId), eq("PENDING"), eq(0), eq(10)))
                .thenReturn(response);

        mockMvc.perform(get("/api/invitations/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Sent invitations fetched"))
                .andExpect(jsonPath("$.data.pendingCount").value(0))
                .andExpect(jsonPath("$.data.pendingCap").value(3));
    }

    @Test
    void listSent_WhenSizeExceeds50_ShouldReturn400WithInv0020() throws Exception {
        mockMvc.perform(get("/api/invitations/sent")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INV_0020: Page size must not exceed 50"));
        verify(invitationService, never()).listSentInvitations(any(), any(), anyInt(), anyInt());
    }

    @Test
    void listSent_WhenSizeIsZeroOrNegative_ShouldNormalizeAndStillCallService() throws Exception {
        SentInvitationListResponse response = SentInvitationListResponse.builder()
                .invitations(List.of())
                .pendingCount(0L)
                .pendingCap(3)
                .build();
        when(invitationService.listSentInvitations(eq(inviterId), eq("PENDING"), eq(0), eq(10)))
                .thenReturn(response);

        mockMvc.perform(get("/api/invitations/sent")
                        .param("size", "0"))
                .andExpect(status().isOk());
    }

    @Test
    void listSent_WhenPageIsNegative_ShouldNormalizeTo0AndStillCallService() throws Exception {
        SentInvitationListResponse response = SentInvitationListResponse.builder()
                .invitations(List.of())
                .pendingCount(0L)
                .pendingCap(3)
                .build();
        when(invitationService.listSentInvitations(eq(inviterId), eq("PENDING"), eq(0), eq(10)))
                .thenReturn(response);

        mockMvc.perform(get("/api/invitations/sent")
                        .param("page", "-3"))
                .andExpect(status().isOk());
    }

    @Test
    void listSent_WhenJwtPrincipalIsInvalidUuid_ShouldReturn400() throws Exception {
        setJwtPrincipal("not-a-uuid");

        mockMvc.perform(get("/api/invitations/sent"))
                .andExpect(status().isBadRequest());
        verify(invitationService, never()).listSentInvitations(any(), any(), anyInt(), anyInt());
    }

    // ===== Group 6: listReceived (GET /api/invitations/received) =====

    @Test
    void listReceived_WithDefaultQueryParams_ShouldReturn200WithBody() throws Exception {
        ReceivedInvitationListResponse response = ReceivedInvitationListResponse.builder()
                .invitations(List.of())
                .pendingCount(0L)
                .build();
        when(invitationService.listReceivedInvitations(eq(inviteeId), eq("PENDING"), eq(0), eq(10)))
                .thenReturn(response);

        setJwtPrincipal(inviteeId);

        mockMvc.perform(get("/api/invitations/received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Received invitations fetched"))
                .andExpect(jsonPath("$.data.pendingCount").value(0));
    }

    @Test
    void listReceived_WhenSizeExceeds50_ShouldReturn400WithInv0020() throws Exception {
        setJwtPrincipal(inviteeId);

        mockMvc.perform(get("/api/invitations/received")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INV_0020: Page size must not exceed 50"));
        verify(invitationService, never()).listReceivedInvitations(any(), any(), anyInt(), anyInt());
    }

    @Test
    void listReceived_WhenSizeAndPageAreInvalid_ShouldNormalizeAndStillCallService() throws Exception {
        setJwtPrincipal(inviteeId);
        ReceivedInvitationListResponse response = ReceivedInvitationListResponse.builder()
                .invitations(List.of())
                .pendingCount(0L)
                .build();
        when(invitationService.listReceivedInvitations(eq(inviteeId), eq("PENDING"), eq(0), eq(10)))
                .thenReturn(response);

        mockMvc.perform(get("/api/invitations/received")
                        .param("size", "0")
                        .param("page", "-1"))
                .andExpect(status().isOk());
    }

    @Test
    void listReceived_WhenJwtPrincipalIsInvalidUuid_ShouldReturn400() throws Exception {
        setJwtPrincipal("not-a-uuid");

        mockMvc.perform(get("/api/invitations/received"))
                .andExpect(status().isBadRequest());
        verify(invitationService, never()).listReceivedInvitations(any(), any(), anyInt(), anyInt());
    }
}
