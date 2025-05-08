package br.com.microservices.orchestrated.orderservice.core.service;


import br.com.microservices.orchestrated.orderservice.core.document.Event;
import br.com.microservices.orchestrated.orderservice.core.document.Order;
import br.com.microservices.orchestrated.orderservice.core.dto.OrderRequestDTO;
import br.com.microservices.orchestrated.orderservice.core.producer.SagaProducer;
import br.com.microservices.orchestrated.orderservice.core.repository.IOrderRepository;
import br.com.microservices.orchestrated.orderservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OrderService {
    private static final String TRANSACTION_ID_PATTERN = "%s_%s";

    private final IOrderRepository repository;
    private final JsonUtil jsonUtil;
    private final EventService eventService;
    private final SagaProducer producer;

    public Order createOrder(OrderRequestDTO orderRequest) {
        var order = Order
                .builder()
                .products(orderRequest.getProducts())
                .createdAt(LocalDateTime.now())
                .transactionId(
                        String.format(TRANSACTION_ID_PATTERN, Instant.now()
                                .toEpochMilli(),
                                UUID.randomUUID())
                )
                .build();

        repository.save(order);
        Event event = this.createPayload(order);
        producer.sendEvent(jsonUtil.toJson(event));
        return order;
    }

    public Event createPayload(Order order) {
        Event event = Event.builder()
                .orderId(order.getId())
                .transactionId(order.getTransactionId())
                .payload(order)
                .createdAt(LocalDateTime.now())
                .build();

        return eventService.save(event);
    }
}
