package dev.portfolio.payments.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.portfolio.payments.api.CreatePaymentRequest;
import dev.portfolio.payments.persistence.OutboxRepository;
import dev.portfolio.payments.persistence.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    private PaymentRepository repo;
    private OutboxRepository outbox;
    private PaymentService service;
    private final CreatePaymentRequest request = new CreatePaymentRequest(2599, "GBP", "order-42");

    @BeforeEach
    void setUp() {
        repo = mock(PaymentRepository.class);
        outbox = mock(OutboxRepository.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        service = new PaymentService(repo, outbox, new PaymentFingerprint(mapper),
                mapper, new SimpleMeterRegistry());
    }

    @Test
    void firstRequestCreatesPaymentAndOutboxEvent() {
        when(repo.insertIfAbsent(any())).thenReturn(true);

        var result = service.create(request, "demo-key");

        assertTrue(result.created());
        assertEquals("PENDING", result.payment().status());
        verify(outbox).enqueue(any(), eq(result.payment().id()), contains("paymentId"));
    }

    @Test
    void replayReturnsSamePaymentWithoutSecondOutboxEvent() {
        when(repo.insertIfAbsent(any())).thenReturn(true, false);
        var original = service.create(request, "same-key");
        when(repo.findByIdempotencyKey("same-key")).thenReturn(Optional.of(original.payment()));

        var replay = service.create(request, "same-key");

        assertFalse(replay.created());
        assertEquals(original.payment().id(), replay.payment().id());
        verify(outbox, times(1)).enqueue(any(), any(), anyString());
    }

    @Test
    void reusedKeyWithChangedBodyProducesConflict() {
        when(repo.insertIfAbsent(any())).thenReturn(true, false);
        var original = service.create(request, "same-key");
        when(repo.findByIdempotencyKey("same-key")).thenReturn(Optional.of(original.payment()));
        assertThrows(IdempotencyConflictException.class,
                () -> service.create(new CreatePaymentRequest(2600, "GBP", "order-42"), "same-key"));
    }

    @Test
    void disallowMissingOrInvalidKey() {
        assertThrows(InvalidIdempotencyKeyException.class, () -> service.create(request, ""));
        assertThrows(InvalidIdempotencyKeyException.class, () -> service.create(request, "contains spaces"));
        verifyNoInteractions(repo, outbox);
    }
}
