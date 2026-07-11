package com.indivaragroup.ageninlite.service.admin;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.common.dto.PaginatedResponseDto;
import com.indivaragroup.ageninlite.dto.admin.UserDeleteResponseDto;
import com.indivaragroup.ageninlite.dto.admin.UserSearchResponseDto;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private UUID currentAdminId;
    private UUID targetUserId;
    private MstUser targetUser;

    @BeforeEach
    void setUp() {
        currentAdminId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        targetUser = MstUser.builder()
                .userId(targetUserId)
                .userName("Target User")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .build();
    }

    @Test
    void searchUsers_WithNullQuery_ShouldReturnAllNonDeletedUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<MstUser> page = new PageImpl<>(List.of(targetUser), pageable, 1);
        when(userRepository.findByIsDeletedFalseAndRole("AGENT", pageable)).thenReturn(page);

        PaginatedResponseDto<UserSearchResponseDto> result = adminUserService.searchUsers(null, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("Target User", result.getContent().get(0).getName());
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        verify(userRepository).findByIsDeletedFalseAndRole("AGENT", pageable);
        verify(userRepository, never()).searchUsers(anyString(), any(Pageable.class));
    }

    @Test
    void searchUsers_WithEmptyQuery_ShouldReturnAllNonDeletedUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<MstUser> page = new PageImpl<>(List.of(targetUser), pageable, 1);
        when(userRepository.findByIsDeletedFalseAndRole("AGENT", pageable)).thenReturn(page);

        PaginatedResponseDto<UserSearchResponseDto> result = adminUserService.searchUsers("   ", 0, 20);

        assertEquals(1, result.getContent().size());
        verify(userRepository).findByIsDeletedFalseAndRole("AGENT", pageable);
        verify(userRepository, never()).searchUsers(anyString(), any(Pageable.class));
    }

    @Test
    void searchUsers_WithValidQuery_ShouldReturnMatchingUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<MstUser> page = new PageImpl<>(List.of(targetUser), pageable, 1);
        when(userRepository.searchUsers("Target", pageable)).thenReturn(page);

        PaginatedResponseDto<UserSearchResponseDto> result = adminUserService.searchUsers("Target", 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("Target User", result.getContent().get(0).getName());
        verify(userRepository).searchUsers("Target", pageable);
        verify(userRepository, never()).findByIsDeletedFalseAndRole(anyString(), any(Pageable.class));
    }

    @Test
    void softDeleteUser_Success() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

        UserDeleteResponseDto result = adminUserService.softDeleteUser(targetUserId, currentAdminId);

        assertNotNull(result);
        assertEquals("User soft-deleted successfully", result.getMessage());
        assertTrue(targetUser.isDeleted());
        assertNotNull(targetUser.getDeletedAt());
        verify(userRepository).save(targetUser);
    }

    @Test
    void softDeleteUser_SelfLockout_ShouldThrowException() {
        AppException exception = assertThrows(AppException.class, () -> 
            adminUserService.softDeleteUser(currentAdminId, currentAdminId)
        );

        assertEquals(UserErrorCode.USR_0002, exception.getErrorCode());
        verify(userRepository, never()).findById(any(UUID.class));
        verify(userRepository, never()).save(any(MstUser.class));
    }

    @Test
    void softDeleteUser_UserNotFound_ShouldThrowException() {
        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> 
            adminUserService.softDeleteUser(targetUserId, currentAdminId)
        );

        assertEquals(UserErrorCode.USR_0001, exception.getErrorCode());
        verify(userRepository, never()).save(any(MstUser.class));
    }
}
