package org.service.inventoryservice.service;

import org.service.inventoryservice.dto.InStockRequest;
import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.event.ProductEvent;

import java.util.List;

public interface InventoryService {

    boolean isInStock(InStockRequest inStockRequest);

    InventoryResponse update(Long id, InventoryRequest inventoryRequest);

    List<InventoryResponse> findAll();

    void listen(ProductEvent productEvent);
}
