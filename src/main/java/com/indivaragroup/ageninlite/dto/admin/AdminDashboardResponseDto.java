package com.indivaragroup.ageninlite.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponseDto {
    private long total_users;
    private long active_agents;
    private long total_transactions;
    private long total_products;
    private List<RecentActivityDto> recent_activities;
}
