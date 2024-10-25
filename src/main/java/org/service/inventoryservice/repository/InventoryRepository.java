package org.service.inventoryservice.repository;

import org.service.inventoryservice.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("SELECT i.quantity FROM Inventory i " +
            "JOIN Product p ON p.inventory.id = i.id " +
            "WHERE p.skuCode = :skuCode")
    Integer countProductsInStock(@Param("skuCode") String skuCode);

    Optional<Inventory> findByProductSkuCode(String skuCode);
}
