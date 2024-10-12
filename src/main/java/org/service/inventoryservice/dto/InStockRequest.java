package org.service.inventoryservice.dto;

import java.util.List;

public record InStockRequest(List<ProductDto> products) {
}
