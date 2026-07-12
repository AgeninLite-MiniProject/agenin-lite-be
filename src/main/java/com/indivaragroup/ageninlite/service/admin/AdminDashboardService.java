package com.indivaragroup.ageninlite.service.admin;

import com.indivaragroup.ageninlite.dto.admin.AdminDashboardResponseDto;
import com.indivaragroup.ageninlite.dto.admin.RecentActivityDto;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final TrxTransactionRepository transactionRepository;

    public AdminDashboardResponseDto getDashboardOverview() {
        log.info("Process get admin dashboard overview");

        long totalUsers = userRepository.countByRole("AGENT");
        long activeAgents = userRepository.countByRoleAndUserStatusAndIsDeletedFalse("AGENT", "ACTIVE");
        long totalProducts = productRepository.countByProductStatus("ACTIVE");
        long totalTransactions = transactionRepository.count();

        List<RecentActivityDto> recentActivities = new ArrayList<>();

        return AdminDashboardResponseDto.builder()
                .total_users(totalUsers)
                .active_agents(activeAgents)
                .total_products(totalProducts)
                .total_transactions(totalTransactions)
                .recent_activities(recentActivities)
                .build();
    }
}
