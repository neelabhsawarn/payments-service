package dev.portfolio.payments.api;

import dev.portfolio.payments.domain.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody CreatePaymentRequest request) {
        PaymentService.Creation result = service.create(request, key);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/v1/payments/" + result.payment().id()))
                .body(PaymentResponse.from(result.payment()));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(service.get(id));
    }
}
