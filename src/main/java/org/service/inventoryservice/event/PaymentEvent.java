package org.service.inventoryservice.event;

public record PaymentEvent(String status, String orderNumber, String email) {
}
