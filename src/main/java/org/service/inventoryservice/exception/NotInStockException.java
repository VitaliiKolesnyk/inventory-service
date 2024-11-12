package org.service.inventoryservice.exception;

public class NotInStockException extends RuntimeException {
    private String message;

    public NotInStockException(String message) {
        super(message);
    }
}
