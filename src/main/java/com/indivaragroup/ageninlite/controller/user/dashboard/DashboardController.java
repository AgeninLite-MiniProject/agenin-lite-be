package com.indivaragroup.ageninlite.controller.user.dashboard;

import com.indivaragroup.ageninlite.common.dto.ApiResponse;
import com.indivaragroup.ageninlite.dto.dashboard.DashboardResponseDto;
import com.indivaragroup.ageninlite.service.user.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponseDto>> getDashboard(@AuthenticationPrincipal String userId) {
        UUID userUuid = UUID.fromString(userId);
        DashboardResponseDto dashboardResponse = dashboardService.getDashboardData(userUuid);

        return ResponseEntity.ok(new ApiResponse<>(true, "Dashboard data received successfully", dashboardResponse));
    }

}
