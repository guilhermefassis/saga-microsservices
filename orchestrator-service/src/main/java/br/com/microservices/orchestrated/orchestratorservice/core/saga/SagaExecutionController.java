package br.com.microservices.orchestrated.orchestratorservice.core.saga;

import br.com.microservices.orchestrated.orchestratorservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.orchestratorservice.core.dto.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

import static br.com.microservices.orchestrated.orchestratorservice.core.saga.SagaHandler.*;
import static java.lang.String.format;

@Slf4j
@Component
@AllArgsConstructor
public class SagaExecutionController {

    private static final String SAGA_LOG_ID = "ORDER ID: %s | TRANSACTION ID %s | EVENT ID %s";

    public ETopics getNextTopic(Event event) {
        if(ObjectUtils.isEmpty(event.getSource()) || ObjectUtils.isEmpty(event.getStatus())) {
            throw new ValidationException("Source and Status must be import.");
        }
        var topic = this.findTopicBySourceAndStatus(event);
        this.logCurrentSaga(event, topic);
        return topic;
    }


    private ETopics findTopicBySourceAndStatus(Event event) {
        return (ETopics) (Arrays.stream(SAGA_HANDLER)
                .filter(row -> isEventSourceAndStatusValid(event, row))
                .map(row -> row[TOPIC_INDEX])
                .findFirst()
                .orElseThrow(() -> new ValidationException("Topic not found")));
    }

    private boolean isEventSourceAndStatusValid(Event event, Object[] row) {
        var source = row[EVENT_SOURCE_INDEX];
        var status = row[SAGA_STATUS_INDEX];

        return event.getSource().equals(source) && event.getStatus().equals(status);
    }

    private void logCurrentSaga(Event event, ETopics topic) {
        var sagaId = this.createSagaId(event);
        var source = event.getSource();

        switch(event.getStatus()) {
            case SUCCESS -> log.info("### CURRENT SAGA: {} | SUCCESS | NEXT TOPIC {} | {}",
                    source, topic, sagaId);
            case ROLLBACK_PENDING -> log.info("### CURRENT SAGA: {} | SENDING TO ROLLBACK CURRENT SERVICE | NEXT TOPIC {} | {}",
                    source, topic, sagaId);
            case FAIL -> log.info("### CURRENT SAGA: {} | SENDING TO ROLLBACK PREVIOUS SERVICE | NEXT TOPIC {} | {}",
                    source, topic, sagaId);
        }
    }

    private String createSagaId(Event event) {
        return format(SAGA_LOG_ID, event.getPayload().getId(), event.getTransactionId(), event.getId());
    }
}
