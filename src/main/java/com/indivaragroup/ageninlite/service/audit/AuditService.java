package com.indivaragroup.ageninlite.service.audit;

import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.EntityType;
import com.indivaragroup.ageninlite.dto.audit.AuditLogResponseDto;
import com.indivaragroup.ageninlite.entity.SysAuditLog;
import com.indivaragroup.ageninlite.repository.audit.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(UUID actorId, AuditAction action, EntityType entityType, UUID entityId, String payload, String auditStatus, String ipAddress, String userAgent) {
        if (ipAddress == null || userAgent == null) {
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    if (ipAddress == null) ipAddress = request.getRemoteAddr();
                    if (userAgent == null) userAgent = request.getHeader("User-Agent");
                }
            } catch (Exception e) {
                // Ignore if not running within a HTTP request context
            }
        }

        String jsonPayload = payload;
        if (payload != null && !payload.trim().startsWith("{") && !payload.trim().startsWith("[")) {
            jsonPayload = "{\"message\":\"" + payload.replace("\"", "\\\"") + "\"}";
        }

        SysAuditLog auditLog = SysAuditLog.builder()
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .payload(jsonPayload)
                .auditStatus(auditStatus != null ? auditStatus : "SUCCESS")
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> getAuditLogs(
            UUID actorId, AuditAction action, EntityType entityType,
            String auditStatus, Pageable pageable) {

        Specification<SysAuditLog> spec = ((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (auditStatus != null && !auditStatus.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("auditStatus"), auditStatus));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        Page<SysAuditLog> auditLogs = auditLogRepository.findAll(spec, pageable);

        return auditLogs.map(log -> {
            AuditLogResponseDto dto = new AuditLogResponseDto();
            dto.setAuditLogId(log.getAuditLogId());
            dto.setActorId(log.getActorId());
            dto.setAction(log.getAction().name());
            dto.setEntityType(log.getEntityType().name());
            dto.setEntityId(log.getEntityId());
            dto.setPayload(log.getPayload());
            dto.setIpAddress(log.getIpAddress());
            dto.setUserAgent(log.getUserAgent());
            dto.setAuditStatus(log.getAuditStatus());
            dto.setCreatedAt(log.getCreatedAt());

            return dto;
        });
    }

}
