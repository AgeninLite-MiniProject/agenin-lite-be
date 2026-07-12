package com.indivaragroup.ageninlite.service.admin;

import com.indivaragroup.ageninlite.dto.admin.AdminDashboardResponseDto;
import com.indivaragroup.ageninlite.dto.admin.RecentActivityDto;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TrxTransactionRepository transactionRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void getDashboardOverview_Success() {
        when(userRepository.countByRole("AGENT")).thenReturn(100L);
        when(userRepository.countByRoleAndUserStatusAndIsDeletedFalse("AGENT", "ACTIVE")).thenReturn(80L);
        when(productRepository.countByProductStatus("ACTIVE")).thenReturn(15L);
        when(transactionRepository.count()).thenReturn(50L);

        AdminDashboardResponseDto response = adminDashboardService.getDashboardOverview();

        assertNotNull(response);
        assertEquals(100L, response.getTotal_users());
        assertEquals(80L, response.getActive_agents());
        assertEquals(15L, response.getTotal_products());
        assertEquals(50L, response.getTotal_transactions());

        List<RecentActivityDto> activities = response.getRecent_activities();
        assertEquals(0, activities.size());
    }
}
