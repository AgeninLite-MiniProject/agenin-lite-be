package com.indivaragroup.ageninlite.controller.audit;

import com.indivaragroup.ageninlite.common.dto.ApiResponse;
import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.EntityType;
import com.indivaragroup.ageninlite.dto.audit.AuditLogResponseDto;
import com.indivaragroup.ageninlite.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogResponseDto>>> getAuditLogs(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) String auditStatus,
            @PageableDefault(size = 20) Pageable pageable
            ) {
        Page<AuditLogResponseDto> logs = auditService.getAuditLogs(actorId, action, entityType, auditStatus, pageable);

        return ResponseEntity.ok(new ApiResponse<>(true, "Berhasil mengambil data log audit", logs));
    }


}
