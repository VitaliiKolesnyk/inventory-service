package org.service.inventoryservice.dto;

import java.util.List;

public record ReserveRequest(List<ProductDto> products, String orderNumber) {
}
