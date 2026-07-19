package com.indivaragroup.ageninlite.service.audit;

import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.EntityType;
import com.indivaragroup.ageninlite.dto.audit.AuditLogResponseDto;
import com.indivaragroup.ageninlite.entity.SysAuditLog;
import com.indivaragroup.ageninlite.repository.audit.AuditLogRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<SysAuditLog> auditLogCaptor;

    @Captor
    private ArgumentCaptor<Specification<SysAuditLog>> specCaptor;

    private UUID actorId;
    private UUID entityId;
    private SysAuditLog sampleLog;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        entityId = UUID.randomUUID();

        sampleLog = SysAuditLog.builder()
                .auditLogId(UUID.randomUUID())
                .actorId(actorId)
                .action(AuditAction.REGISTER)
                .entityType(EntityType.USER)
                .entityId(entityId)
                .payload("{\"key\":\"value\"}")
                .auditStatus("SUCCESS")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void saveLog_WithCustomStatus_Success() {
        auditService.saveLog(actorId, AuditAction.REGISTER, EntityType.USER, entityId,
                "payload", "FAILED", "192.168.1.1", "Chrome");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals(actorId, savedLog.getActorId());
        assertEquals(AuditAction.REGISTER, savedLog.getAction());
        assertEquals(EntityType.USER, savedLog.getEntityType());
        assertEquals(entityId, savedLog.getEntityId());
        assertEquals("{\"message\":\"payload\"}", savedLog.getPayload());
        assertEquals("FAILED", savedLog.getAuditStatus());
        assertEquals("192.168.1.1", savedLog.getIpAddress());
        assertEquals("Chrome", savedLog.getUserAgent());
    }

    @Test
    void saveLog_WithNullStatus_DefaultsToSuccess() {
        auditService.saveLog(actorId, AuditAction.TRANSACTION_CREATE, EntityType.TRANSACTION, entityId,
                "payload2", null, "192.168.1.2", "Firefox");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals("SUCCESS", savedLog.getAuditStatus());
        assertEquals(AuditAction.TRANSACTION_CREATE, savedLog.getAction());
        assertEquals("192.168.1.2", savedLog.getIpAddress());
        assertEquals("Firefox", savedLog.getUserAgent());
    }

    @Test
    void saveLog_WithNullIpAndUserAgent_FetchesFromRequestContext() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("PostmanRuntime/7.29.0");

        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);

        auditService.saveLog(actorId, AuditAction.LOGIN, EntityType.USER, entityId,
                "payload3", "SUCCESS", null, null);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals("10.0.0.1", savedLog.getIpAddress());
        assertEquals("PostmanRuntime/7.29.0", savedLog.getUserAgent());
    }

    @Test
    void saveLog_WithNullIpAndUserAgent_AndNoRequestContext_IgnoresAndLeavesNull() {
        RequestContextHolder.resetRequestAttributes();

        auditService.saveLog(actorId, AuditAction.LOGIN, EntityType.USER, entityId,
                "payload4", "SUCCESS", null, null);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals(null, savedLog.getIpAddress());
        assertEquals(null, savedLog.getUserAgent());
    }

    @Test
    void saveLog_WithNullIpAndUserAgent_AndExceptionInContext_IgnoresAndLeavesNull() {
        // Simulating attributes != null but throwing exception on getRequest
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenThrow(new RuntimeException("Simulated exception"));
        RequestContextHolder.setRequestAttributes(attributes);

        auditService.saveLog(actorId, AuditAction.LOGIN, EntityType.USER, entityId,
                "payload5", "SUCCESS", null, null);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals(null, savedLog.getIpAddress());
        assertEquals(null, savedLog.getUserAgent());
    }

    @Test
    void saveLog_WithIpNotNullButUserAgentNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("PostmanRuntime/7.29.0");

        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);

        auditService.saveLog(actorId, AuditAction.LOGIN, EntityType.USER, entityId,
                "payload", "SUCCESS", "10.0.0.1", null);

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals("10.0.0.1", savedLog.getIpAddress()); // Should remain the same
        assertEquals("PostmanRuntime/7.29.0", savedLog.getUserAgent()); // Should be fetched
    }

    @Test
    void saveLog_WithIpNullButUserAgentNotNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);

        auditService.saveLog(actorId, AuditAction.LOGIN, EntityType.USER, entityId,
                "payload", "SUCCESS", null, "ExistingAgent");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals("10.0.0.1", savedLog.getIpAddress()); // Should be fetched
        assertEquals("ExistingAgent", savedLog.getUserAgent()); // Should remain the same
    }

    @Test
    void saveLog_WithNullPayload() {
        auditService.saveLog(actorId, AuditAction.REGISTER, EntityType.USER, entityId,
                null, "SUCCESS", "1.1.1.1", "Agent");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();
        assertEquals(null, savedLog.getPayload());
    }

    @Test
    void saveLog_WithJsonPayloadObject() {
        String jsonObject = "{\"key\":\"value\"}";
        auditService.saveLog(actorId, AuditAction.REGISTER, EntityType.USER, entityId,
                jsonObject, "SUCCESS", "1.1.1.1", "Agent");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();
        assertEquals(jsonObject, savedLog.getPayload());
    }

    @Test
    void saveLog_WithJsonPayloadArray() {
        String jsonArray = "[1, 2, 3]";
        auditService.saveLog(actorId, AuditAction.REGISTER, EntityType.USER, entityId,
                jsonArray, "SUCCESS", "1.1.1.1", "Agent");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();
        assertEquals(jsonArray, savedLog.getPayload());
    }

    @Test
    void saveLogSync_Success() {
        auditService.saveLogSync(actorId, AuditAction.REGISTER, EntityType.USER, entityId,
                "payload sync", "SUCCESS", "127.0.0.1", "Browser");

        verify(auditLogRepository).save(auditLogCaptor.capture());
        SysAuditLog savedLog = auditLogCaptor.getValue();

        assertEquals("SUCCESS", savedLog.getAuditStatus());
        assertEquals(AuditAction.REGISTER, savedLog.getAction());
        assertEquals("{\"message\":\"payload sync\"}", savedLog.getPayload());
    }

    @Test
    void getAuditLogs_WithAllFilters_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleLog)));

        // Act
        Page<AuditLogResponseDto> result = auditService.getAuditLogs(
                actorId, AuditAction.REGISTER, EntityType.USER, "SUCCESS", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        AuditLogResponseDto dto = result.getContent().get(0);
        assertEquals(actorId, dto.getActorId());
        assertEquals("REGISTER", dto.getAction());
        assertEquals("USER", dto.getEntityType());
        assertEquals("SUCCESS", dto.getAuditStatus());

        // Verify Specification execution to achieve 100% coverage
        verify(auditLogRepository).findAll(specCaptor.capture(), any(Pageable.class));
        Specification<SysAuditLog> spec = specCaptor.getValue();
        
        Root<SysAuditLog> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Path<Object> actorIdPath = mock(Path.class);
        Path<Object> actionPath = mock(Path.class);
        Path<Object> entityTypePath = mock(Path.class);
        Path<Object> auditStatusPath = mock(Path.class);

        when(root.get("actorId")).thenReturn(actorIdPath);
        when(root.get("action")).thenReturn(actionPath);
        when(root.get("entityType")).thenReturn(entityTypePath);
        when(root.get("auditStatus")).thenReturn(auditStatusPath);
        
        Predicate mockPredicate = mock(Predicate.class);
        when(cb.equal(actorIdPath, actorId)).thenReturn(mockPredicate);
        when(cb.equal(actionPath, AuditAction.REGISTER)).thenReturn(mockPredicate);
        when(cb.equal(entityTypePath, EntityType.USER)).thenReturn(mockPredicate);
        when(cb.equal(auditStatusPath, "SUCCESS")).thenReturn(mockPredicate);
        
        when(cb.and(any(Predicate[].class))).thenReturn(mockPredicate);

        Predicate finalPredicate = spec.toPredicate(root, query, cb);
        assertNotNull(finalPredicate);
    }

    @Test
    void getAuditLogs_WithNoFilters_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleLog)));

        // Act
        Page<AuditLogResponseDto> result = auditService.getAuditLogs(
                null, null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());

        // Verify Specification execution to achieve 100% coverage (empty predicates)
        verify(auditLogRepository).findAll(specCaptor.capture(), any(Pageable.class));
        Specification<SysAuditLog> spec = specCaptor.getValue();
        
        Root<SysAuditLog> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Predicate mockPredicate = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(mockPredicate);

        Predicate finalPredicate = spec.toPredicate(root, query, cb);
        assertNotNull(finalPredicate);
    }
    
    @Test
    void getAuditLogs_WithEmptyAuditStatusString_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleLog)));

        // Act
        Page<AuditLogResponseDto> result = auditService.getAuditLogs(
                null, null, null, "   ", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());

        // Verify Specification execution to achieve 100% coverage (empty string status)
        verify(auditLogRepository).findAll(specCaptor.capture(), any(Pageable.class));
        Specification<SysAuditLog> spec = specCaptor.getValue();
        
        Root<SysAuditLog> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        Predicate mockPredicate = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(mockPredicate);

        Predicate finalPredicate = spec.toPredicate(root, query, cb);
        assertNotNull(finalPredicate);
    }
}
