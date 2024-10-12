package org.service.inventoryservice.event;

import lombok.Data;

@Data
public class NotificationEvent {
    private String subject;

    private String message;

    private String email;
}
