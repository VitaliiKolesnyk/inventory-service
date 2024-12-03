package org.service.inventoryservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
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

    @Transactional
    public boolean reserveInventory(ReserveRequest reserveRequest) {
        log.info("Reserving inventory for order {}", reserveRequest.orderNumber());

        for (ProductDto productDto : reserveRequest.products()) {
            log.info("Processing product with SKU: {}", productDto.skuCode());

            Product product = productRepository.findBySkuCode(productDto.skuCode())
                    .orElseThrow(() -> new NotInStockException("Product not found"));

            log.info("Product {} found", productDto.skuCode());

            boolean success = reserveProductInventory(productDto, reserveRequest.orderNumber(), product);
            if (!success) {
                return false;
            }
        }

        log.info("Inventory successfully reserved for order {}", reserveRequest.orderNumber());
        return true;
    }

    private boolean reserveProductInventory(ProductDto productDto, String orderNumber, Product product) {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                Inventory inventory = inventoryRepository.findByProductSkuCode(productDto.skuCode())
                        .orElseThrow(() -> new NotInStockException("Inventory not found"));

                log.info("Inventory found for SKU: {}", productDto.skuCode());

                if (inventory.getQuantity() < productDto.quantity()) {
                    log.warn("Not enough stock for SKU: {}", productDto.skuCode());
                    return false;
                }

                inventory.setQuantity(inventory.getQuantity() - productDto.quantity());
                inventoryRepository.save(inventory);

                ProductReservation productReservation = productMapper.map(productDto, orderNumber);
                productReservation.setProduct(product);
                product.getProductReservations().add(productReservation);
                productReservation.setReservationUntilDate(LocalDateTime.now().plusMinutes(10));
                productReservationRepository.save(productReservation);

                log.info("Reserved {} units of SKU: {}", productDto.quantity(), productDto.skuCode());
                return true;
            } catch (OptimisticLockException e) {
                attempt++;
                log.warn("Optimistic lock failed for SKU: {}. Retrying {}/{}", productDto.skuCode(), attempt, maxRetries);
                if (attempt >= maxRetries) {
                    log.error("Failed to reserve inventory for SKU: {} after {} attempts", productDto.skuCode(), maxRetries);
                    return false;
                }
            }
        }

        return false;
    }

    private void addQuantityToInventory(String skuCode, Integer quantity) {
        final int MAX_RETRIES = 3;
        int attempt = 0;

        log.info("Adding {} units to inventory for SKU: {}", quantity, skuCode);

        while (attempt < MAX_RETRIES) {
            try {
                Inventory inventory = inventoryRepository.findByProductSkuCode(skuCode).orElseThrow(
                        () -> new RuntimeException("Inventory not found"));

                log.info("Inventory found for SKU: {}", skuCode);

                inventory.setQuantity(inventory.getQuantity() + quantity);

                inventoryRepository.save(inventory);

                log.info("Inventory updated successfully for SKU: {}", skuCode);

                break;

            } catch (OptimisticLockingFailureException e) {
                attempt++;

                log.warn("Optimistic locking failure on attempt {} for SKU: {}", attempt, skuCode);

                if (attempt >= MAX_RETRIES) {
                    log.error("Failed to update inventory due to concurrent modifications for SKU: {}", skuCode, e);

                    throw new RuntimeException("Failed to update inventory due to concurrent modifications for SKU: " + skuCode, e);
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cancelReservation() {
        log.info("Checking for expired product reservations");

        List<ProductReservation> productReservations =
                productReservationRepository.findAllByReservationUntilDateLessThan(LocalDateTime.now());

        for (ProductReservation productReservation : productReservations) {
            productReservationRepository.delete(productReservation);
            log.info("Canceled reservation for order {}", productReservation.getOrderNumber());

            addQuantityToInventory(productReservation.getProduct().getSkuCode(), productReservation.getQuantity());
            log.info("Restored {} units to inventory for SKU: {}", productReservation.getQuantity(),
                    productReservation.getProduct().getSkuCode());

            String orderNumber = productReservation.getOrderNumber();

            kafkaTemplate.send("order-cancel-events", new OrderCancelEvent(orderNumber));
            log.info("Sent order cancel event for order {}", productReservation.getOrderNumber());
        }
    }

    @Override
    public InventoryResponse update(Long id, InventoryRequest inventoryRequest) {
        log.info("Updating inventory with ID: {}", id);

        Inventory inventory = inventoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Inventory not found"));

        log.info("Inventory found for ID: {}", id);

        inventory.setLimit(inventoryRequest.limit());
        inventory.setQuantity(inventoryRequest.quantity());
        inventory.setLimitNotificationSent(false);

        Inventory savedInventory = inventoryRepository.save(inventory);

        log.info("Inventory updated successfully for ID: {}", id);

        return inventoryMapper.map(savedInventory);
    }

    @Override
    public List<InventoryResponse> findAll() {
        return inventoryMapper.map(inventoryRepository.findAll());
    }

    @Override
    public InventoryResponse findBySkuCode(String skuCode) {
        return inventoryMapper.map(inventoryRepository.findByProductSkuCode(skuCode)
                .orElseThrow(() -> new RuntimeException("Inventory not found")));
    }

    private void saveInventory(ProductEvent productEvent) {
        log.info("Saving new inventory for product {}", productEvent.skuCode());

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

        log.info("Inventory saved successfully for product {}", productEvent.skuCode());
    }

    private void deleteInventory(ProductEvent productEvent) {
        log.info("Deleting inventory for product {}", productEvent.skuCode());

        Inventory inventory = inventoryRepository.findByProductSkuCode(productEvent.skuCode())
                .orElseThrow(() -> new RuntimeException("Inventory not found"));

        inventoryRepository.delete(inventory);
    }

    private void updateInventory(ProductEvent productEvent) {
        log.info("Updating inventory for product {}", productEvent.skuCode());

        Product product = productRepository.findBySkuCode(productEvent.skuCode())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(productEvent.name());
        product.setThumbnailUrl(productEvent.thumbnailUrl());

        productRepository.save(product);

        log.info("Inventory deleted for product {}", productEvent.skuCode());
    }

    @Scheduled(fixedRate = 60000)
    public void checkInventoryAndNotify() {
        log.info("Checking inventory limits for all products");

        List<Inventory> inventories = inventoryRepository.findAll();

        for (Inventory inventory : inventories) {
            if (inventory.getQuantity() <= inventory.getLimit() && !inventory.isLimitNotificationSent()) {
                log.warn("Inventory limit exceeded for SKU: {}, Quantity: {}",
                        inventory.getProduct().getSkuCode(), inventory.getQuantity());

                LimitExceedEvent limitExceedEvent = new LimitExceedEvent();
                limitExceedEvent.setSkuCode(inventory.getProduct().getSkuCode());
                limitExceedEvent.setLimit(inventory.getQuantity());

                kafkaTemplate.send("inventory-limit-topic", limitExceedEvent);

                log.info("Sent limit exceed event for SKU: {}", inventory.getProduct().getSkuCode());

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

        log.info("Received message from topic {}: {}", topic, message);

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
                log.error("Unknown topic: {}", topic);

                throw new IllegalArgumentException("Unknown topic: " + topic);
        }
    }

    private <T> T deserialize(String json, Class<T> targetType) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON message: {}", json, e);

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
            default:
                log.warn("Unknown product event action: {}", action);
        }
    }

    private void handlePaymentEvent(PaymentEvent paymentEvent) {
        log.info("Got Message from payment-events topic {}", paymentEvent);

        if (paymentEvent.status().equals("Success")) {
            productReservationRepository.deleteAllByOrderNumber(paymentEvent.orderNumber());

            log.info("Deleted reservations for order {}", paymentEvent.orderNumber());
        }
    }
}
