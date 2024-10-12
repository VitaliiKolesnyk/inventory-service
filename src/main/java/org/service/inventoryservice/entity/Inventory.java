package org.service.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "limit_qnty")
    private Integer limit;

    @Column(name = "limit_notification_sent")
    private boolean isLimitNotificationSent;

    @OneToOne(mappedBy = "inventory", cascade = CascadeType.ALL)
    private Product product;
}
