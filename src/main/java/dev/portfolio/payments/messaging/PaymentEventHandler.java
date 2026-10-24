package dev.portfolio.payments.messaging;

import dev.portfolio.payments.persistence.PaymentRepository;
import dev.portfolio.payments.persistence.ProcessedMessageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventHandler {
    private final PaymentRepository payments;
    private final ProcessedMessageRepository inbox;
    private final MeterRegistry metrics;

    public PaymentEventHandler(PaymentRepository payments, ProcessedMessageRepository inbox,
                               MeterRegistry metrics) {
        this.payments = payments;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    @Transactional
    public void handle(PaymentRequestedEvent event) {
        if (!inbox.recordIfFirst(event.eventId())) {
            metrics.counter("payments.consumer.handled", "outcome", "duplicate").increment();
            return;
        }
        // Simulates payment settlement; NO bank/card network or money movement occurs.
        payments.markProcessed(event.paymentId());
        metrics.counter("payments.consumer.handled", "outcome", "processed").increment();
    }
}
