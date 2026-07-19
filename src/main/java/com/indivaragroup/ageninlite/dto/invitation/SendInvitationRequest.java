package com.indivaragroup.ageninlite.dto.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendInvitationRequest {
    @NotBlank(message = "phoneNumber is required")
    @Size(min = 8, max = 16, message = "phoneNumber must be between 8 and 16 characters")
    private String phoneNumber;
}
