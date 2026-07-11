package com.indivaragroup.ageninlite.service.admin;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.common.dto.PaginatedResponseDto;
import com.indivaragroup.ageninlite.dto.admin.UserDeleteResponseDto;
import com.indivaragroup.ageninlite.dto.admin.UserSearchResponseDto;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PaginatedResponseDto<UserSearchResponseDto> searchUsers(String query, int page, int size) {
        log.info("Process search users with query: {}, page: {}, size: {}", query != null ? query : "ALL", page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<MstUser> userPage;

        if (query != null && !query.trim().isEmpty()) {
            userPage = userRepository.searchUsers(query, pageable);
        } else {
            userPage = userRepository.findByRole("AGENT", pageable);
        }

        List<UserSearchResponseDto> content = userPage.getContent().stream().map(user -> UserSearchResponseDto.builder()
                .user_id(user.getUserId())
                .name(user.getUserName())
                .role(user.getRole())
                .user_status(user.getUserStatus())
                .is_deleted(user.isDeleted())
                .build()).toList();

        return PaginatedResponseDto.<UserSearchResponseDto>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    @Transactional
    public UserDeleteResponseDto softDeleteUser(UUID targetUserId, UUID currentAdminId) {
        log.info("Admin {} process soft delete for user: {}", currentAdminId, targetUserId);

        if (targetUserId.equals(currentAdminId)) {
            throw new AppException(UserErrorCode.USR_0002);
        }

        MstUser user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(UserErrorCode.USR_0001));

        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        return UserDeleteResponseDto.builder()
                .message("User soft-deleted successfully")
                .build();
    }
}
