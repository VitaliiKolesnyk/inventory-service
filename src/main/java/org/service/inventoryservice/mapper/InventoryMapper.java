package org.service.inventoryservice.mapper;

import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.entity.Inventory;

import java.util.List;

public interface InventoryMapper {

    Inventory map(InventoryRequest inventoryRequest);

    InventoryResponse map(Inventory inventory);

    List<InventoryResponse> map(List<Inventory> inventoryList);
}
