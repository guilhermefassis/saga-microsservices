package br.com.microservices.orchestrated.paymentservice.core.utils;


import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class JsonUtil {
    private final ObjectMapper mapper;

    public String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            return "";
        }
    }

    public Event toEvent(String json) {
        try {
            return mapper.readValue(json, Event.class);
        } catch(Exception e) {
            return null;
        }
    }
}
