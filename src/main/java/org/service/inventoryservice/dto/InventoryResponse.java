package org.service.inventoryservice.dto;

public record InventoryResponse(Long id, String skuCode, Integer quantity, Integer limit) {
}
