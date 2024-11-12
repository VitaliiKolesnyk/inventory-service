package org.service.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String skuCode;

    private String thumbnailUrl;

    @OneToOne
    private Inventory inventory;

    @OneToMany(mappedBy = "product")
    private List<ProductReservation> productReservations = new ArrayList<>();
}