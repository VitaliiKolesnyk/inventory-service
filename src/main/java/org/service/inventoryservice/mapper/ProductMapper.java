package org.service.inventoryservice.mapper;

import org.service.inventoryservice.dto.ProductDto;
import org.service.inventoryservice.entity.ProductReservation;
import org.springframework.stereotype.Component;


public interface ProductMapper {

    ProductReservation map(ProductDto productDto, String orderNumber);
}
