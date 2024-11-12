package org.service.inventoryservice.controller;

import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.dto.ReserveRequest;
import org.service.inventoryservice.exception.NotInStockException;
import org.service.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.service.inventoryservice.dto.Error;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("reserveProducts")
    public ResponseEntity<Boolean> reserveProducts(@RequestBody ReserveRequest reserveRequest) {
        return new ResponseEntity<>(inventoryService.reserveInventory(reserveRequest), HttpStatus.CREATED);
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

    @ExceptionHandler(NotInStockException.class)
    public ResponseEntity<Boolean> catchNotInStockException(NotInStockException e) {
        return new ResponseEntity<>(false, HttpStatus.OK);
    }
}
