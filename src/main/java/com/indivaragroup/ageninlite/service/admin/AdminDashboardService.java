package com.indivaragroup.ageninlite.service.admin;

import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.dto.admin.AdminDashboardResponseDto;
import com.indivaragroup.ageninlite.dto.admin.RecentActivityDto;
import com.indivaragroup.ageninlite.entity.SysAuditLog;
import com.indivaragroup.ageninlite.repository.audit.AuditLogRepository;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final TrxTransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminDashboardResponseDto getDashboardOverview() {
        log.info("Process get admin dashboard overview");

        long totalUsers = userRepository.countByRoleAndIsDeletedFalse("AGENT");
        long activeAgents = userRepository.countByRoleAndUserStatusAndIsDeletedFalse("AGENT", "ACTIVE");
        long totalProducts = productRepository.countByProductStatus("ACTIVE");
        long totalTransactions = transactionRepository.countByTrxStatus("COMPLETED");

        List<AuditAction> importantActions = List.of(
                AuditAction.TRANSACTION_COMPLETE,
                AuditAction.REGISTER_WITH_REFERRAL,
                AuditAction.INVITE_ACCEPTED
        );

        Page<SysAuditLog> recentAuditPage = auditLogRepository.findByActionInOrderByCreatedAtDesc(
                importantActions, 
                PageRequest.of(0, 10)
        );
        
        List<RecentActivityDto> recentActivities = recentAuditPage.stream().map(logAudit -> {
            String userName = "System/Unknown";
            if (logAudit.getActorId() != null) {
                userName = userRepository.findById(logAudit.getActorId())
                        .map(com.indivaragroup.ageninlite.entity.MstUser::getUserName)
                        .orElse("Unknown User");
            }
            
            String actionName = switch (logAudit.getAction()) {
                case TRANSACTION_COMPLETE -> "Transaksi Baru";
                case REGISTER_WITH_REFERRAL -> "Registrasi Downline";
                case INVITE_ACCEPTED -> "Terima Undangan Jaringan";
                default -> logAudit.getAction().toString();
            };

            return RecentActivityDto.builder()
                    .time(logAudit.getCreatedAt().toString())
                    .user(userName)
                    .action(actionName)
                    .status(logAudit.getAuditStatus())
                    .build();
        }).toList();

        return AdminDashboardResponseDto.builder()
                .total_users(totalUsers)
                .active_agents(activeAgents)
                .total_products(totalProducts)
                .total_transactions(totalTransactions)
                .recent_activities(recentActivities)
                .build();
    }
}
