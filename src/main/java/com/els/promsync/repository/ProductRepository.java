package com.els.promsync.repository;

import com.els.promsync.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByDealerCodeAndSku(String dealerCode, String sku);

    List<Product> findByDealerCodeAndActiveFromDealerTrue(String dealerCode);

    List<Product> findByActiveFromDealerTrue();

    long countByActiveFromDealerTrue();
}
