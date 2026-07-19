package com.indivaragroup.ageninlite.repository.transaction;

import com.indivaragroup.ageninlite.dto.dashboard.RecentCommissionDto;
import com.indivaragroup.ageninlite.entity.TrxCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrxCommissionRepository extends JpaRepository<TrxCommission, UUID> {
    long countByItemIdAndBeneficiaryIdAndCommissionType(
            UUID itemId, UUID beneficiaryId, String commissionType);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM TrxCommission c WHERE c.beneficiaryId = :beneficiaryId AND c.commissionType = :commissionType")
    BigDecimal sumCommissionAmountByBeneficiaryIdAndCommissionType(
            @Param("beneficiaryId") UUID beneficiaryId,
            @Param("commissionType") String commissionType);

    @Query("""
        SELECT new
        com.indivaragroup.ageninlite.dto.dashboard.RecentCommissionDto(
            c.commissionId,
            c.commissionType,
            p.productName,
            c.commissionAmount,
            u.userName,
            c.createdAt
        )
        FROM TrxCommission c, TrxItem i, MstProduct p, MstUser u
        WHERE c.itemId = i.itemId
        AND i.productId = p.productId
        AND c.sourceUserId = u.userId
        AND c.beneficiaryId = :beneficiaryId
        ORDER BY c.createdAt DESC
    """)
    List<RecentCommissionDto> findRecentCommissionsDtoByBeneficiaryId(
            @Param("beneficiaryId") UUID beneficiaryId,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM TrxCommission c " +
            "WHERE c.beneficiaryId = :beneficiaryId " +
            "AND c.sourceUserId = :sourceUserId " +
            "AND c.commissionType = :commissionType")
    BigDecimal sumCommissionAmountByBeneficiaryIdAndSourceUserIdAndCommissionType(
            @Param("beneficiaryId") UUID beneficiaryId,
            @Param("sourceUserId") UUID sourceUserId,
            @Param("commissionType") String commissionType
    );
    List<TrxCommission> findByBeneficiaryIdAndItemIdInAndCommissionType(UUID beneficiaryId, Collection<UUID> itemIds, String commissionType);

    List<TrxCommission> findTop20ByBeneficiaryIdOrderByCreatedAtDesc(UUID beneficiaryId);

    List<TrxCommission> findAllByItemIdIn(Collection<UUID> itemIds);

    boolean existsByBeneficiaryIdAndSourceUserId(UUID beneficiaryId, UUID sourceUserId);
}
