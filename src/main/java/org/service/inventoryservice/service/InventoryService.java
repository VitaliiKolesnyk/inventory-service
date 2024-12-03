package org.service.inventoryservice.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.dto.ReserveRequest;
import org.service.inventoryservice.event.PaymentEvent;
import org.service.inventoryservice.event.ProductEvent;

import java.util.List;

public interface InventoryService {

    boolean reserveInventory(ReserveRequest reserveRequest);

    InventoryResponse update(Long id, InventoryRequest inventoryRequest);

    List<InventoryResponse> findAll();

    InventoryResponse findBySkuCode(String skuCode);

    void listen(ConsumerRecord<String, String> record);

    void cancelReservation();
}
