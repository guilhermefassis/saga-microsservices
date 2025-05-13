package br.com.microservices.orchestrated.inventoryservice.core.service;

import br.com.microservices.orchestrated.inventoryservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Event;
import br.com.microservices.orchestrated.inventoryservice.core.dto.History;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Order;
import br.com.microservices.orchestrated.inventoryservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.inventoryservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.inventoryservice.core.model.Inventory;
import br.com.microservices.orchestrated.inventoryservice.core.model.OrderInventory;
import br.com.microservices.orchestrated.inventoryservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.inventoryservice.core.repository.IInventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.repository.IOrderInventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class InventoryService {

    private static final String CURRENT_SOURCE = "INVENTORY_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final IInventoryRepository inventoryRepository;
    private final IOrderInventoryRepository orderInventoryRepository;

    public void updateInventory(Event event) {
        try {
            this.checkCurrentValidation(event);
            this.createOrderInventory(event);
            this.updateInventory(event.getPayload());
            this.handleSuccess(event);
        } catch(Exception e) {
            log.error("Error trying update inventory: ".concat(e.getMessage()));
            this.handleFailCurrentExecuted(event, "Fail to update inventory: ".concat(e.getMessage()));
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    public void rollbackInventory(Event event) {
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);

        try {
            this.returnInventoryToPreviousValues(event);
            addHistory(event, "Rollback executed for inventory!");
        } catch(Exception e) {
            addHistory(event, "Rollback not executed for inventory: ".concat(e.getMessage()));
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void returnInventoryToPreviousValues(Event event) {
        this.orderInventoryRepository
            .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
            .forEach(orderInventory -> {
                var inventory = orderInventory.getInventory();
                inventory.setAvailable(orderInventory.getOldQuantity());
                this.inventoryRepository.save(inventory);
                log.info("Restored inventory for order {}: from {} to {}",
                        event.getPayload().getId(), orderInventory.getNewQuantity(), inventory.getAvailable());
            });
    }


    private void handleFailCurrentExecuted(Event event, String message) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        addHistory(event, "Payment is Fail to realize payment: ".concat(message));
    }

    private void updateInventory(Order order) {
        order
            .getProducts()
            .forEach(product -> {
                var inventory = this.findInventoryByProductCode(product.getProduct().getCode());
                this.checkInventory(inventory.getAvailable(), product.getQuantity());
                inventory.setAvailable(inventory.getAvailable() - product.getQuantity());
                inventoryRepository.save(inventory);
            });
    }

    private void checkInventory(int available, int orderQuantity) {
        if(orderQuantity > available) {
            throw new ValidationException("Product is out of stock!");
        }
    }

    private void createOrderInventory(Event event) {
        event
                .getPayload()
                .getProducts()
                .forEach(product -> {
                    var inventory = this.findInventoryByProductCode(product.getProduct().getCode());
                    var orderInventory = createOrderInventory(event, product, inventory);
                    orderInventoryRepository.save(orderInventory);
                });
    }

    private void handleSuccess(Event event) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.SUCCESS);

        addHistory(event, "Inventory updated successfully");
    }

    private void addHistory(Event event, String message) {
        History history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        event.addToHistory(history);

    }

    private OrderInventory createOrderInventory(Event event, OrderProducts product, Inventory inventory) {
        return OrderInventory
                .builder()
                .inventory(inventory)
                .oldQuantity(inventory.getAvailable())
                .orderQuantity(product.getQuantity())
                .newQuantity((inventory.getAvailable() - product.getQuantity()))
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .build();
    }

    private Inventory findInventoryByProductCode(String productCode) {
        return this.inventoryRepository
                .findByProductCode(productCode)
                .orElseThrow(() -> new ValidationException("Inventory not found by informed product. "));
    }

    private void checkCurrentValidation(Event event) {
        if(this.orderInventoryRepository.existsByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())) {
            throw new ValidationException("There's another transactionId for this validation.");
        }
    }
}
