package com.indivaragroup.ageninlite.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.common.dto.PaginatedResponseDto;
import com.indivaragroup.ageninlite.dto.admin.UserDeleteResponseDto;
import com.indivaragroup.ageninlite.dto.admin.UserSearchResponseDto;
import com.indivaragroup.ageninlite.service.admin.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminUserController adminUserController;

    private UUID currentAdminId;
    private UUID targetUserId;
    private Principal principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        currentAdminId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        
        principal = new Principal() {
            @Override
            public String getName() {
                return currentAdminId.toString();
            }
        };
    }

    @Test
    void searchUsers_WithoutQuery_ShouldReturnList() throws Exception {
        UserSearchResponseDto user = UserSearchResponseDto.builder()
                .user_id(targetUserId)
                .name("Test User")
                .role("AGENT")
                .user_status("ACTIVE")
                .is_deleted(false)
                .build();

        PaginatedResponseDto<UserSearchResponseDto> paginatedResponse = PaginatedResponseDto.<UserSearchResponseDto>builder()
                .content(List.of(user))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(adminUserService.searchUsers(null, null, null, 0, 20)).thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].user_id").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Test User"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchUsers_WithQuery_ShouldReturnList() throws Exception {
        UserSearchResponseDto user = UserSearchResponseDto.builder()
                .user_id(targetUserId)
                .name("Test User")
                .role("AGENT")
                .user_status("ACTIVE")
                .is_deleted(false)
                .build();

        PaginatedResponseDto<UserSearchResponseDto> paginatedResponse = PaginatedResponseDto.<UserSearchResponseDto>builder()
                .content(List.of(user))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(adminUserService.searchUsers("Test", null, null, 0, 20)).thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/admin/users")
                .param("q", "Test")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].user_id").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Test User"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchUsers_WithSizeGreaterThan100_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .param("size", "101")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUser_Success() throws Exception {
        UserDeleteResponseDto responseDto = UserDeleteResponseDto.builder()
                .message("User soft-deleted successfully")
                .build();

        when(adminUserService.softDeleteUser(eq(targetUserId), eq(currentAdminId))).thenReturn(responseDto);

        mockMvc.perform(post("/api/admin/users/{userId}/delete", targetUserId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User soft-deleted successfully"));
    }

    @Test
    void deleteUser_SelfLockout_ShouldReturnBadRequest() throws Exception {
        when(adminUserService.softDeleteUser(eq(currentAdminId), eq(currentAdminId)))
                .thenThrow(new AppException(UserErrorCode.USR_0002));

        mockMvc.perform(post("/api/admin/users/{userId}/delete", currentAdminId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("USR_0002: Cannot delete your own Admin account"));
    }
    
    @Test
    void deleteUser_UserNotFound_ShouldReturnNotFound() throws Exception {
        when(adminUserService.softDeleteUser(eq(targetUserId), eq(currentAdminId)))
                .thenThrow(new AppException(UserErrorCode.USR_0001));

        mockMvc.perform(post("/api/admin/users/{userId}/delete", targetUserId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("USR_0001: User not found"));
    }
}
