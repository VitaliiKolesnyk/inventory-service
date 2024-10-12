package org.service.inventoryservice.event;

public record ProductEvent(String action, String name, String skuCode) {}
