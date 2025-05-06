package br.com.microservices.orchestrated.orchestratorservice.core.enums;

public enum ETopics {

    BASE_ORCHESTRATOR("orchestrator"),
    FINISH_FAIL("finish-fail"),
    FINISH_SUCCESS("finish-success"),
    INVENTORY_FAIL("inventory-fail"),
    INVENTORY_SUCCESS("inventory-success"),
    NOTIFY_ENDING("notify-ending"),
    PAYMENT_FAIL("payment-fail"),
    PAYMENT_SUCCESS("payment-success"),
    PRODUCT_VALIDATION_FAIL("product-validation-fail"),
    PRODUCT_VALIDATION_SUCCESS("product-validation-success"),
    START_SAGA("start-saga");

    private final String topic;

    ETopics(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
