package com.indivaragroup.ageninlite.controller.invitation;

import com.indivaragroup.ageninlite.common.dto.ApiResponse;
import com.indivaragroup.ageninlite.dto.invitation.AcceptInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.CancelInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.DeclineInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.InvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.SendInvitationRequest;
import com.indivaragroup.ageninlite.dto.invitation.SentInvitationListResponse;
import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.InvitationErrorCode;
import com.indivaragroup.ageninlite.service.invitation.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitationService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvitationResponse>> send(
            @AuthenticationPrincipal String inviterId,
            @Valid @RequestBody SendInvitationRequest request
    ) {
        UUID inviterUuid = UUID.fromString(inviterId);
        InvitationResponse response = invitationService.sendInvitation(inviterUuid, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Invitation sent successfully", response));
    }

    @PostMapping("/{inviterId}/accept")
    public ResponseEntity<ApiResponse<AcceptInvitationResponse>> accept(
            @AuthenticationPrincipal String inviteeId,
            @PathVariable UUID inviterId
    ) {
        UUID inviteeUuid = UUID.fromString(inviteeId);
        AcceptInvitationResponse response = invitationService.acceptInvitation(inviterId, inviteeUuid);
        return ResponseEntity.ok(new ApiResponse<>(true, "Invitation accepted", response));
    }

    @PostMapping("/{inviterId}/decline")
    public ResponseEntity<ApiResponse<DeclineInvitationResponse>> decline(
            @AuthenticationPrincipal String inviteeId,
            @PathVariable UUID inviterId
    ) {
        UUID inviteeUuid = UUID.fromString(inviteeId);
        DeclineInvitationResponse response = invitationService.declineInvitation(inviterId, inviteeUuid);
        return ResponseEntity.ok(new ApiResponse<>(true, "Invitation declined", response));
    }

    @PostMapping("/{inviteeId}/cancel")
    public ResponseEntity<ApiResponse<CancelInvitationResponse>> cancel(
            @AuthenticationPrincipal String inviterId,
            @PathVariable UUID inviteeId
    ) {
        UUID inviterUuid = UUID.fromString(inviterId);
        CancelInvitationResponse response = invitationService.cancelInvitation(inviterUuid, inviteeId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Invitation cancelled", response));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<SentInvitationListResponse>> listSent(
            @AuthenticationPrincipal String inviterId,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > 50) {
            throw new AppException(InvitationErrorCode.INV_0020);
        }
        if (size <= 0) {
            size = 10;
        }
        if (page < 0) {
            page = 0;
        }

        UUID inviterUuid = UUID.fromString(inviterId);
        SentInvitationListResponse response = invitationService
                .listSentInvitations(inviterUuid, status, page, size);
        return ResponseEntity.ok(new ApiResponse<>(true, "Sent invitations fetched", response));
    }
}
