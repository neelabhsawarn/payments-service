package dev.portfolio.payments.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestedListener {
    private final ObjectMapper mapper;
    private final PaymentEventHandler handler;

    public PaymentRequestedListener(ObjectMapper mapper, PaymentEventHandler handler) {
        this.mapper = mapper;
        this.handler = handler;
    }

    @KafkaListener(topics = "${app.kafka.requested-topic}")
    public void onMessage(String payload) throws Exception {
        handler.handle(mapper.readValue(payload, PaymentRequestedEvent.class));
    }
}
