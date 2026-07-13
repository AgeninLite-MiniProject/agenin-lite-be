package com.indivaragroup.ageninlite.repository.transaction;

import com.indivaragroup.ageninlite.entity.TrxCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    List<TrxCommission> findTop20ByBeneficiaryIdOrderByCreatedAtDesc(UUID beneficiaryId);

    List<TrxCommission> findAllByItemIdIn(Collection<UUID> itemIds);

    boolean existsByBeneficiaryIdAndSourceUserId(UUID beneficiaryId, UUID sourceUserId);
}
