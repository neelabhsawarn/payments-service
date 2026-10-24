package dev.portfolio.payments.api;

import dev.portfolio.payments.domain.Payment;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id, long amountMinor, String currency, String merchantReference, String status, Instant createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.id(), payment.amountMinor(), payment.currency(),
                payment.merchantReference(), payment.status(), payment.createdAt());
    }
}
