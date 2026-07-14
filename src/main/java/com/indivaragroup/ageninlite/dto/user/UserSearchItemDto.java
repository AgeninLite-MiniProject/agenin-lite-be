package com.indivaragroup.ageninlite.dto.user;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserSearchItemDto {
    private UUID user_id;
    private String name;
    private String phone;
    private String status;
}
