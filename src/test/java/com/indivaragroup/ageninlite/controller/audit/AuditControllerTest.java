package com.indivaragroup.ageninlite.controller.audit;

import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.EntityType;
import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.dto.audit.AuditLogResponseDto;
import com.indivaragroup.ageninlite.service.audit.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditController auditController;

    private UUID adminId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        mockMvc = MockMvcBuilders.standaloneSetup(auditController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                adminId.toString(),
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAuditLogs_WithAllParameters_Success() throws Exception {
        // Arrange
        AuditLogResponseDto responseDto = new AuditLogResponseDto();
        responseDto.setAuditLogId(UUID.randomUUID());
        responseDto.setActorId(actorId);
        responseDto.setAction("LOGIN");
        responseDto.setEntityType("USER");
        responseDto.setAuditStatus("SUCCESS");
        
        Page<AuditLogResponseDto> pageResponse = new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1);
        
        when(auditService.getAuditLogs(eq(actorId), eq(AuditAction.LOGIN), eq(EntityType.USER), eq("SUCCESS"), any(Pageable.class)))
                .thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("actorId", actorId.toString())
                        .param("action", "LOGIN")
                        .param("entityType", "USER")
                        .param("auditStatus", "SUCCESS")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Berhasil mengambil data log audit"))
                .andExpect(jsonPath("$.data.content[0].actorId").value(actorId.toString()))
                .andExpect(jsonPath("$.data.content[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.data.content[0].entityType").value("USER"))
                .andExpect(jsonPath("$.data.content[0].auditStatus").value("SUCCESS"));

        verify(auditService).getAuditLogs(eq(actorId), eq(AuditAction.LOGIN), eq(EntityType.USER), eq("SUCCESS"), any(Pageable.class));
    }

    @Test
    void getAuditLogs_WithoutParameters_Success() throws Exception {
        // Arrange
        AuditLogResponseDto responseDto = new AuditLogResponseDto();
        responseDto.setAuditLogId(UUID.randomUUID());
        responseDto.setAction("REGISTER");
        
        Page<AuditLogResponseDto> pageResponse = new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1);
        
        when(auditService.getAuditLogs(eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Berhasil mengambil data log audit"))
                .andExpect(jsonPath("$.data.content[0].action").value("REGISTER"));

        verify(auditService).getAuditLogs(eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
    }
}
