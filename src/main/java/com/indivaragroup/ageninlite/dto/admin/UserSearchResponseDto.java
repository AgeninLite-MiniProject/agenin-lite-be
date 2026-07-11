package com.indivaragroup.ageninlite.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserSearchResponseDto {
    private UUID user_id;
    private String name;
    private String role;
    private String user_status;
    private Boolean is_deleted;
}
