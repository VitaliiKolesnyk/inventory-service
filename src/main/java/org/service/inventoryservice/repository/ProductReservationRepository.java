package org.service.inventoryservice.repository;

import org.service.inventoryservice.entity.ProductReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductReservationRepository extends JpaRepository<ProductReservation, Long> {

    @Modifying
    void deleteAllByOrderNumber(String orderNumber);

    List<ProductReservation> findAllByReservationUntilDateLessThan(LocalDateTime localDateTime);
}
