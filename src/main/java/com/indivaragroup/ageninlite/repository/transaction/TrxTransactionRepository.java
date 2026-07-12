package com.indivaragroup.ageninlite.repository.transaction;

import com.indivaragroup.ageninlite.entity.TrxTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrxTransactionRepository extends JpaRepository<TrxTransaction, UUID> {
    List<TrxTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
