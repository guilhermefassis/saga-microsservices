package br.com.microservices.orchestrated.paymentservice.core.service;

import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.History;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.IPaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {

    private static final Double MINIMUM_AMOUNT_VALUE = 0.1;
    private static final Double REDUCE_SUM_VALUE = 0.0;
    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final IPaymentRepository paymentRepository;

    public void realizePayment(Event event) {
        try {
            this.checkCurrentValidation(event);
            this.createPendingPayment(event);
            Payment payment = this.findByOrderIdAndTransactionId(event);
            this.validateAmount(payment.getTotalAmount());
            this.changePaymentToSuccess(payment);
            this.handleSuccess(event);
        } catch (Exception e) {
            handleFailCurrentExecuted(event, e.getMessage());
            log.error("Error trying to make payment!");
        }
        producer.sendEvent(this.jsonUtil.toJson(event));
    }

    public void realizeRefund(Event event) {
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        try {
            this.changePaymentStatusToRefund(event);
            addHistory(event, "Rollback executed for payment.");
        } catch(Exception e) {
            addHistory(event, "Rollback executed for payment: ".concat(e.getMessage()));
        }

        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changePaymentToSuccess(Payment payment) {
        payment.setStatus(EPaymentStatus.SUCCESS);
        this.save(payment);
    }

    private void createPendingPayment(Event event) {
        Double totalAmount = this.totalAmount(event);
        Integer totalItems = this.totalItems(event);

        Payment payment = Payment
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .totalAmount(totalAmount)
                .totalItems(totalItems)
                .build();

        this.save(payment);
        this.setEventAmountItems(event, payment);
    }

    private void checkCurrentValidation(Event event) {
        if(this.paymentRepository.existsByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())) {
            throw new ValidationException("There's another transactionId to this validation.");
        }
    }

    private Payment findByOrderIdAndTransactionId(Event event) {
        return this.paymentRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .orElseThrow(() -> new ValidationException("Payment not found by orderId and trasactionId"));
    }

    private void save(Payment payment) {
        this.paymentRepository.save(payment);
    }

    private Double totalAmount(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(product -> product.getQuantity() * product.getProduct().getUnitValue())
                .reduce(REDUCE_SUM_VALUE, Double::sum);
    }

    private Integer totalItems(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(OrderProducts::getQuantity)
                .reduce(REDUCE_SUM_VALUE.intValue(), Integer::sum);
    }

    private void setEventAmountItems(Event event, Payment payment) {
        event.getPayload().setTotalAmount(payment.getTotalAmount());
        event.getPayload().setTotalItems(payment.getTotalItems());
    }

    private void validateAmount(Double amount) {
        if(amount < 0.1) {
            throw new ValidationException("The minimal amount value is ".concat(MINIMUM_AMOUNT_VALUE.toString()));
        }
    }

    private void handleSuccess(Event event) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.SUCCESS);

        addHistory(event, "Payment realized successfully");
    }

    private void handleFailCurrentExecuted(Event event, String message) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        addHistory(event, "Payment is Fail to realize payment: ".concat(message));
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

    private void changePaymentStatusToRefund(Event event) {
        Payment payment = this.paymentRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .orElseThrow(() -> new ValidationException("Payment not found with this transactionId"));
        this.setEventAmountItems(event, payment);
        payment.setStatus(EPaymentStatus.REFUND);

        this.save(payment);
    }
}
