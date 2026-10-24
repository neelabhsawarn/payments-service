package dev.portfolio.payments.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.portfolio.payments.api.CreatePaymentRequest;
import dev.portfolio.payments.messaging.PaymentRequestedEvent;
import dev.portfolio.payments.persistence.OutboxRepository;
import dev.portfolio.payments.persistence.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PaymentService {
    public record Creation(Payment payment, boolean created) {}

    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final PaymentFingerprint fingerprint;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public PaymentService(PaymentRepository payments, OutboxRepository outbox,
                          PaymentFingerprint fingerprint, ObjectMapper mapper, MeterRegistry metrics) {
        this.payments = payments;
        this.outbox = outbox;
        this.fingerprint = fingerprint;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Transactional
    public Creation create(CreatePaymentRequest request, String key) {
        if (key == null || key.length() > 128 || !key.matches("[a-zA-Z0-9._:-]+")) {
            throw new InvalidIdempotencyKeyException();
        }
        String hash = fingerprint.sha256(request);
        Payment candidate = new Payment(UUID.randomUUID(), key, hash, request.amountMinor(),
                request.currency(), request.merchantReference(), "PENDING", Instant.now());

        // PostgreSQL resolves a concurrent unique-key race before this returns.
        if (payments.insertIfAbsent(candidate)) {
            UUID eventId = UUID.randomUUID();
            try {
                outbox.enqueue(eventId, candidate.id(), mapper.writeValueAsString(
                        new PaymentRequestedEvent(eventId, candidate.id(), Instant.now())));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to serialize outbox event", e);
            }
            metrics.counter("payments.api.requests", "outcome", "created").increment();
            return new Creation(candidate, true);
        }

        Payment existing = payments.findByIdempotencyKey(key).orElseThrow(
                () -> new IllegalStateException("Idempotency winner was not visible"));
        if (!existing.requestHash().equals(hash)) {
            metrics.counter("payments.api.requests", "outcome", "conflict").increment();
            throw new IdempotencyConflictException();
        }
        metrics.counter("payments.api.requests", "outcome", "replayed").increment();
        return new Creation(existing, false);
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> new NoSuchElementException("Payment not found"));
    }
}
