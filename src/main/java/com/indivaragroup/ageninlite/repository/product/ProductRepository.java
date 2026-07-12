package com.indivaragroup.ageninlite.repository.product;

import com.indivaragroup.ageninlite.entity.MstProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<MstProduct, UUID> {
    long countByProductStatus(String productStatus);
}
