package br.com.microservices.orchestrated.inventoryservice.core.consumer;


import br.com.microservices.orchestrated.inventoryservice.core.service.InventoryService;
import br.com.microservices.orchestrated.inventoryservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class InventoryConsumer {
    private final JsonUtil jsonUtil;
    private final InventoryService inventoryService;

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${spring.kafka.topic.inventory-success}"
    )
    public void consumerInventorySuccessEvent(String payload) {
        log.info("Receiving event {} from inventory-success topic", payload);
        var event = jsonUtil.toEvent(payload);
        this.inventoryService.updateInventory(event);
    }

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${spring.kafka.topic.inventory-fail}"
    )
    public void consumerInventoryFailEvent(String payload) {
        log.info("Receiving event {} from product-validation-fail topic", payload);
        var event = jsonUtil.toEvent(payload);
        this.inventoryService.rollbackInventory(event);
    }
}
