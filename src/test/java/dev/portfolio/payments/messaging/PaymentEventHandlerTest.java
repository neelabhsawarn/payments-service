package dev.portfolio.payments.messaging;

import dev.portfolio.payments.persistence.PaymentRepository;
import dev.portfolio.payments.persistence.ProcessedMessageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PaymentEventHandlerTest {
    @Test
    void duplicateEventDoesNotProcessTwice() {
        var payments = mock(PaymentRepository.class);
        var inbox = mock(ProcessedMessageRepository.class);
        var event = new PaymentRequestedEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        when(inbox.recordIfFirst(event.eventId())).thenReturn(true, false);
        var service = new PaymentEventHandler(payments, inbox, new SimpleMeterRegistry());

        service.handle(event);
        service.handle(event);

        verify(payments, times(1)).markProcessed(event.paymentId());
    }
}
