package com.indivaragroup.ageninlite.dto.audit;

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
public class AuditLogResponseDto {
    private UUID auditLogId;
    private UUID actorId;
    private String action;
    private String entityType;
    private UUID entityId;
    private String payload;
    private String ipAddress;
    private String userAgent;
    private String auditStatus;
    private LocalDateTime createdAt;
}
