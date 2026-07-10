package com.indivaragroup.ageninlite.controller.admin;

import com.indivaragroup.ageninlite.dto.admin.UserDeleteResponseDto;
import com.indivaragroup.ageninlite.dto.admin.UserSearchResponseDto;
import com.indivaragroup.ageninlite.service.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<UserSearchResponseDto>> searchUsers(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(adminUserService.searchUsers(q));
    }

    @PostMapping("/{userId}/delete")
    public ResponseEntity<UserDeleteResponseDto> deleteUser(
            @PathVariable UUID userId,
            Principal principal
    ) {
        UUID currentAdminId = UUID.fromString(principal.getName());

        UserDeleteResponseDto response = adminUserService.softDeleteUser(userId, currentAdminId);
        return ResponseEntity.ok(response);
    }
}
