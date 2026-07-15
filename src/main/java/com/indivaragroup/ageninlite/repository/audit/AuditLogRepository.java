package com.indivaragroup.ageninlite.repository.audit;

import com.indivaragroup.ageninlite.entity.SysAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<SysAuditLog, UUID>, JpaSpecificationExecutor<SysAuditLog> {

}
