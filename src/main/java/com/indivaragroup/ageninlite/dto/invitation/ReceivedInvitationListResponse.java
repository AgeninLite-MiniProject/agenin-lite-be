package com.indivaragroup.ageninlite.dto.invitation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivedInvitationListResponse {
    private List<ReceivedInvitationItemDto> invitations;
    private long pendingCount;
}
