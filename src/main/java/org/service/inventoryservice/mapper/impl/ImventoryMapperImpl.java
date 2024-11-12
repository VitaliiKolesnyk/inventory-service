package org.service.inventoryservice.mapper.impl;

import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.entity.Inventory;
import org.service.inventoryservice.mapper.InventoryMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImventoryMapperImpl implements InventoryMapper {

    @Override
    public Inventory map(InventoryRequest inventoryRequest) {
        return Inventory.builder()
                .limit(inventoryRequest.limit())
                .quantity(inventoryRequest.quantity())
                .build();
    }

    @Override
    public InventoryResponse map(Inventory inventory) {
        return new InventoryResponse(inventory.getId(), inventory.getProduct().getSkuCode(), inventory.getProduct().getName(),
                inventory.getProduct().getThumbnailUrl(), inventory.getQuantity(), inventory.getLimit());
    }

    @Override
    public List<InventoryResponse> map(List<Inventory> productList) {
        return productList.stream()
                .map(p -> map(p))
                .toList();
    }
}
