package com.indivaragroup.ageninlite.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInvitationDto {

    @JsonProperty("inviter_id")
    private UUID inviterId;

    @JsonProperty("inviter_name")
    private String inviterName;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

}
