package org.service.inventoryservice.service;

public interface InventoryService {

    boolean isInStock(String skuCode, Integer quantity);
}
