package org.service.inventoryservice.event;

public record ProductEvent(String action, String name, String skuCode, String description,
                           Double price, String thumbnailUrl) {}
