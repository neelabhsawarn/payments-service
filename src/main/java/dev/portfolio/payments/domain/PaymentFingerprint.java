package dev.portfolio.payments.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.portfolio.payments.api.CreatePaymentRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PaymentFingerprint {
    private final ObjectMapper mapper;

    public PaymentFingerprint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String sha256(CreatePaymentRequest request) {
        try {
            byte[] canonical = mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to hash payment request", e);
        }
    }
}
