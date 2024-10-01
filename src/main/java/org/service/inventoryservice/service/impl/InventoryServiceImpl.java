package org.service.inventoryservice.service.impl;

import org.service.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.service.inventoryservice.service.InventoryService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    public boolean isInStock(String skuCode, Integer quantity) {
        return inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode, quantity);
    }
}
