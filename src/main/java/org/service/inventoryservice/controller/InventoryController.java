package org.service.inventoryservice.controller;

import org.service.inventoryservice.dto.InStockRequest;
import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("inStock")
    @ResponseStatus(HttpStatus.OK)
    public boolean isInStock(@RequestBody InStockRequest inStockRequest) {
        return inventoryService.isInStock(inStockRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> findAll() {
        return inventoryService.findAll();
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponse update(@PathVariable Long id, @RequestBody InventoryRequest inventoryRequest) {
        return inventoryService.update(id, inventoryRequest);
    }
}
