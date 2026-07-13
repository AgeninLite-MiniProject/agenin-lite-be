package com.indivaragroup.ageninlite.repository.transaction;

import com.indivaragroup.ageninlite.entity.TrxTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrxTransactionRepository extends JpaRepository<TrxTransaction, UUID> {
    List<TrxTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<TrxTransaction> findByUserId(UUID userId, Pageable pageable);

    Page<TrxTransaction> findByUserIdAndTrxStatus(UUID userId, String trxStatus, Pageable pageable);

    long countByUserIdAndTrxStatus(UUID userId, String trxStatus);

    @Query("SELECT t FROM TrxTransaction t WHERE t.trxId IN " +
           "(SELECT i.trxId FROM TrxItem i WHERE i.itemId IN " +
           "(SELECT c.itemId FROM TrxCommission c WHERE c.beneficiaryId = :beneficiaryId)) " +
           "AND (:status IS NULL OR t.trxStatus = :status)")
    Page<TrxTransaction> findTransactionsBenefitingUser(
            @Param("beneficiaryId") UUID beneficiaryId,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT COUNT(DISTINCT t.trxId) FROM TrxTransaction t " +
           "WHERE t.trxId IN " +
           "(SELECT i.trxId FROM TrxItem i WHERE i.itemId IN " +
           "(SELECT c.itemId FROM TrxCommission c WHERE c.beneficiaryId = :beneficiaryId)) " +
           "AND t.trxStatus = 'COMPLETED'")
    long countCompletedTransactionsBenefitingUser(@Param("beneficiaryId") UUID beneficiaryId);
}
