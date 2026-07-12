package com.indivaragroup.ageninlite.dto.invitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcceptInvitationResponse {

    private UUID inviterId;
    private UUID inviteeId;
    private String status;
    private LocalDateTime respondedAt;
    private UUID referredBy;
    private int cancelledCount;
    private String message;
}
