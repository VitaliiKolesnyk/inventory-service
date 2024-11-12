package org.service.inventoryservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.dto.ProductDto;
import org.service.inventoryservice.dto.ReserveRequest;
import org.service.inventoryservice.entity.Inventory;
import org.service.inventoryservice.entity.Product;
import org.service.inventoryservice.entity.ProductReservation;
import org.service.inventoryservice.event.LimitExceedEvent;
import org.service.inventoryservice.event.OrderCancelEvent;
import org.service.inventoryservice.event.PaymentEvent;
import org.service.inventoryservice.event.ProductEvent;
import org.service.inventoryservice.exception.NotInStockException;
import org.service.inventoryservice.mapper.InventoryMapper;
import org.service.inventoryservice.mapper.ProductMapper;
import org.service.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.service.inventoryservice.repository.ProductRepository;
import org.service.inventoryservice.repository.ProductReservationRepository;
import org.service.inventoryservice.service.InventoryService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    private final ProductRepository productRepository;

    private final ProductReservationRepository productReservationRepository;

    private final ProductMapper productMapper;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional // Ensures atomicity of the entire method
    public boolean reserveInventory(ReserveRequest reserveRequest) {
        for (ProductDto productDto : reserveRequest.products()) {
            // Fetch the product and inventory in the same transaction
            Product product = productRepository.findBySkuCode(productDto.skuCode())
                    .orElseThrow(() -> new NotInStockException("Product not found"));

            Inventory inventory = inventoryRepository.findByProductSkuCode(productDto.skuCode()).orElseThrow(
                    () -> new NotInStockException("Inventory not found"));

            // Check and subtract inventory in one go to prevent race conditions
            if (inventory.getQuantity() < productDto.quantity()) {
                log.warn("Not enough stock for SKU: {}", productDto.skuCode());
                return false;
            }

            // Subtract the quantity
            inventory.setQuantity(inventory.getQuantity() - productDto.quantity());

            // Save the updated inventory
            inventoryRepository.save(inventory);

            // Reserve the product after updating the inventory
            ProductReservation productReservation = productMapper.map(productDto, reserveRequest.orderNumber());
            productReservation.setProduct(product);
            product.getProductReservations().add(productReservation);
            productReservation.setReservationUntilDate(LocalDateTime.now().plusMinutes(10));

            // Save the reservation
            productReservationRepository.save(productReservation);
        }

        return true;
    }

    private void addQuantityToInventory(String skuCode, Integer quantity) {
        final int MAX_RETRIES = 3; // Maximum retries for optimistic locking failure
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                // Fetch inventory by SKU code
                Inventory inventory = inventoryRepository.findByProductSkuCode(skuCode).orElseThrow(
                        () -> new RuntimeException("Inventory not found"));

                // Update the quantity
                inventory.setQuantity(inventory.getQuantity() + quantity);

                // Save the inventory, version will be checked for optimistic locking
                inventoryRepository.save(inventory);

                // Exit the loop if save is successful
                break;

            } catch (OptimisticLockingFailureException e) {
                // Handle optimistic locking failure
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException("Failed to update inventory due to concurrent modifications for SKU: " + skuCode, e);
                }
                // Optional: Add a small delay before retrying
                try {
                    Thread.sleep(100); // 100ms delay before retrying
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // Handle thread interruption
                }
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cancelReservation() {
        List<ProductReservation> productReservations =
                productReservationRepository.findAllByReservationUntilDateLessThan(LocalDateTime.now());

        for (ProductReservation productReservation : productReservations) {
            productReservationRepository.delete(productReservation);

            addQuantityToInventory(productReservation.getProduct().getSkuCode(), productReservation.getQuantity());

            String orderNumber = productReservation.getOrderNumber();

            kafkaTemplate.send("order-cancel-events", new OrderCancelEvent(orderNumber));
        }
    }

    @Override
    public InventoryResponse update(Long id, InventoryRequest inventoryRequest) {
        Inventory inventory = inventoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Inventory not found"));

        inventory.setLimit(inventoryRequest.limit());
        inventory.setQuantity(inventoryRequest.quantity());
        inventory.setLimitNotificationSent(false);

        Inventory savedInventory = inventoryRepository.save(inventory);

        return inventoryMapper.map(savedInventory);
    }

    @Override
    public List<InventoryResponse> findAll() {
        return inventoryMapper.map(inventoryRepository.findAll());
    }

    private void saveInventory(ProductEvent productEvent) {
        Product product = new Product();
        product.setName(productEvent.name());
        product.setSkuCode(productEvent.skuCode());
        product.setThumbnailUrl(productEvent.thumbnailUrl());

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantity(0);
        inventory.setLimit(0);
        product.setInventory(inventory);

        inventoryRepository.save(inventory);
    }

    private void deleteInventory(ProductEvent productEvent) {
        Inventory inventory = inventoryRepository.findByProductSkuCode(productEvent.skuCode())
                .orElseThrow(() -> new RuntimeException("Inventory not found"));

        inventoryRepository.delete(inventory);
    }

    private void updateInventory(ProductEvent productEvent) {
        Product product = productRepository.findBySkuCode(productEvent.skuCode())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(productEvent.name());
        product.setThumbnailUrl(productEvent.thumbnailUrl());

        productRepository.save(product);
    }

    @Scheduled(fixedRate = 60000) // 10 minutes in milliseconds
    public void checkInventoryAndNotify() {
        List<Inventory> inventories = inventoryRepository.findAll();

        for (Inventory inventory : inventories) {
            if (inventory.getQuantity() <= inventory.getLimit() && !inventory.isLimitNotificationSent()) {
                LimitExceedEvent limitExceedEvent = new LimitExceedEvent();
                limitExceedEvent.setSkuCode(inventory.getProduct().getSkuCode());
                limitExceedEvent.setLimit(inventory.getQuantity());

                kafkaTemplate.send("inventory-limit-topic", limitExceedEvent);

                updateIsNotificationSentFlag(inventory);
            }
        }
    }

    private void updateIsNotificationSentFlag(Inventory inventory) {
        inventory.setLimitNotificationSent(true);

        inventoryRepository.save(inventory);
    }

    @KafkaListener(topics = {"product-events", "payment-events"}, groupId = "inventory-service-group")
    public void listen(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        String message = record.value();

        switch (topic) {
            case "product-events":
                ProductEvent productEvent = deserialize(message, ProductEvent.class);
                handleProductEvent(productEvent);
                break;
            case "payment-events":
                PaymentEvent paymentEvent = deserialize(message, PaymentEvent.class);
                handlePaymentEvent(paymentEvent);
                break;
            default:
                throw new IllegalArgumentException("Unknown topic: " + topic);
        }
    }

    private <T> T deserialize(String json, Class<T> targetType) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON message", e);
        }
    }

    private void handleProductEvent(ProductEvent productEvent) {
        log.info("Got Message from product-events topic {}", productEvent);

        String action = productEvent.action();

        switch (action) {
            case "CREATE": saveInventory(productEvent);
                break;
            case "DELETE": deleteInventory(productEvent);
                break;
            case "UPDATE": updateInventory(productEvent);
                break;
        }
    }

    private void handlePaymentEvent(PaymentEvent paymentEvent) {
        log.info("Got Message from payment-events topic {}", paymentEvent);

        if (paymentEvent.status().equals("Success")) {
            productReservationRepository.deleteByOrderNumber(paymentEvent.orderNumber());
        }
    }
}
