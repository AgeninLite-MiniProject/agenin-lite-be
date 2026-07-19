package com.indivaragroup.ageninlite.dto.invitation;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationResponse {
    private UUID inviterId;
    private UUID inviteeId;
    private String inviteeName;
    private String status;
    private LocalDateTime createdAt;
    private String message;
}
