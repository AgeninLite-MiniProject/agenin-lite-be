package com.indivaragroup.ageninlite.repository.transaction;

import com.indivaragroup.ageninlite.entity.TrxItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrxItemRepository extends JpaRepository<TrxItem, UUID> {
    List<TrxItem> findByTrxId(UUID trxId);
    List<TrxItem> findByTrxIdAndProductId(UUID trxId, UUID productId);

    List<TrxItem> findByTrxIdIn(Collection<UUID> trxIds);
}
