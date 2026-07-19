package com.indivaragroup.ageninlite.repository.audit;

import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.entity.SysAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<SysAuditLog, UUID>, JpaSpecificationExecutor<SysAuditLog> {
    Page<SysAuditLog> findByActionInOrderByCreatedAtDesc(List<AuditAction> actions, Pageable pageable);
}
