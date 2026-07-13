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
public class ReceivedInvitationItemDto {
    private UUID inviterId;
    private String inviterName;
    private String inviterPhone;
    private String inviterAvatarUrl;
    private String status;
    private LocalDateTime createdAt;
}
