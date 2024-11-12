package org.service.inventoryservice.dto;

public record InventoryResponse(Long id, String skuCode, String name, String thumbnailUrl, Integer quantity, Integer limit) {
}
