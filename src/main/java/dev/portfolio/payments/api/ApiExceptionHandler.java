package dev.portfolio.payments.api;

import dev.portfolio.payments.domain.IdempotencyConflictException;
import dev.portfolio.payments.domain.InvalidIdempotencyKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail onConflict(IdempotencyConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ProblemDetail onBadKey(InvalidIdempotencyKeyException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail onNotFound(NoSuchElementException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle(status.getReasonPhrase());
        return result;
    }
}
