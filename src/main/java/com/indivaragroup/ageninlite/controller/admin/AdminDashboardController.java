package com.indivaragroup.ageninlite.controller.admin;

import com.indivaragroup.ageninlite.dto.admin.AdminDashboardResponseDto;
import com.indivaragroup.ageninlite.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ResponseEntity<AdminDashboardResponseDto> getDashboardOverview() {
        return ResponseEntity.ok(adminDashboardService.getDashboardOverview());
    }
}
