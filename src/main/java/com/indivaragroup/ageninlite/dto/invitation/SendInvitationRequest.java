package com.indivaragroup.ageninlite.dto.invitation;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendInvitationRequest {
    @NotNull(message = "inviteeId is required")
    private UUID inviteeId;
}
