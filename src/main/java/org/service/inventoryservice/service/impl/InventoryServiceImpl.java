package org.service.inventoryservice.service.impl;

import org.service.inventoryservice.dto.InStockRequest;
import org.service.inventoryservice.dto.InventoryRequest;
import org.service.inventoryservice.dto.InventoryResponse;
import org.service.inventoryservice.dto.ProductDto;
import org.service.inventoryservice.entity.Inventory;
import org.service.inventoryservice.entity.Product;
import org.service.inventoryservice.event.NotificationEvent;
import org.service.inventoryservice.event.ProductEvent;
import org.service.inventoryservice.mapper.InventoryMapper;
import org.service.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.service.inventoryservice.repository.ProductRepository;
import org.service.inventoryservice.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    private final ProductRepository productRepository;

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Override
    public boolean isInStock(InStockRequest inStockRequest) {
        List<ProductDto> products = inStockRequest.products();

       for (ProductDto product : products) {
           Integer count = inventoryRepository.countProductsInStock(product.skuCode());

           if (count < product.quantity()) {
               return false;
           }
       }

       return true;
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

    @Override
    @KafkaListener(topics = "product-events")
    public void listen(ProductEvent productEvent) {
        log.info("Got Message from product-events topic {}", productEvent);

        String action = productEvent.action();

        switch (action) {
            case "CREATE": saveInventory(productEvent);
                break;
            case "DELETE": deleteInventory(productEvent);
                break;
        }
    }

    private void saveInventory(ProductEvent productEvent) {
        Product product = new Product();
        product.setName(productEvent.name());
        product.setSkuCode(productEvent.skuCode());

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

    @Scheduled(fixedRate = 60000) // 10 minutes in milliseconds
    public void checkInventoryAndNotify() {
        List<Inventory> inventories = inventoryRepository.findAll();

        for (Inventory inventory : inventories) {
            if (inventory.getQuantity() <= inventory.getLimit() && !inventory.isLimitNotificationSent()) {
                NotificationEvent event = new NotificationEvent();
                event.setSubject("Product limit notification");
                event.setMessage(String.format("""
                        Dear admin,
                        
                        Please be informed that product %s quantiti is less than limit %d
                        
                        """, inventory.getProduct().getSkuCode(), inventory.getLimit()));

                kafkaTemplate.send("inventory-limit-topic", event);

                updateIsNotificationSentFlag(inventory);
            }
        }
    }

    private void updateIsNotificationSentFlag(Inventory inventory) {
        inventory.setLimitNotificationSent(true);

        inventoryRepository.save(inventory);
    }
}
