package org.service.inventoryservice.mapper.impl;

import org.service.inventoryservice.dto.ProductDto;
import org.service.inventoryservice.entity.ProductReservation;
import org.service.inventoryservice.mapper.ProductMapper;
import org.springframework.stereotype.Service;

@Service
public class ProductMapperImpl implements ProductMapper {

    @Override
    public ProductReservation map(ProductDto productDto, String orderNumber) {
        return ProductReservation.builder()
                .quantity(productDto.quantity())
                .orderNumber(orderNumber)
                .build();
    }
}
