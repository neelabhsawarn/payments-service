package dev.portfolio.payments.messaging;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequestedEvent(UUID eventId, UUID paymentId, Instant occurredAt) {}
