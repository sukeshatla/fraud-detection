package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.application.EventPublishingException;
import com.fraudplatform.ingestion.application.IdempotencyKeyReusedException;
import com.fraudplatform.ingestion.application.IdempotentRequestInProgressException;
import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.application.TransactionRejectedException;
import com.fraudplatform.ingestion.domain.DomainValidationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.DatabindException;

/** Maps failures to RFC 9457 {@code application/problem+json} responses. */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "urn:fraud-platform:problem:";

    /** One entry per invalid field, so clients can fix everything in one round trip. */
    record FieldError(String field, String message) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed",
                "%d field(s) are invalid".formatted(errors.size()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "Request body is not valid JSON or has values of the wrong type");
        if (ex.getMostSpecificCause() instanceof DatabindException databind && !databind.getPath().isEmpty()) {
            String field = databind.getPath().stream()
                    .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            problem.setProperty("errors", List.of(new FieldError(field, "has an invalid value")));
        }
        return problem;
    }

    @ExceptionHandler({TransactionRejectedException.class, DomainValidationException.class})
    ProblemDetail onRejected(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "transaction-rejected", "Transaction rejected", ex.getMessage());
    }

    @ExceptionHandler(EventPublishingException.class)
    ResponseEntity<ProblemDetail> onPublishFailure(EventPublishingException ex) {
        log.error("Transaction could not be made durable; client told to retry", ex);
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "publish-failed",
                "Temporarily unable to accept transactions", "The transaction was NOT accepted. Retry later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> onRateLimited(RateLimitExceededException ex) {
        RateLimitDecision decision = ex.decision();
        long retryAfterSeconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000); // round up
        ProblemDetail problem = problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", "Too many requests",
                "Quota of %d requests exceeded; retry after %d s".formatted(decision.limit(), retryAfterSeconds));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(IdempotentRequestInProgressException.class)
    ProblemDetail onInProgress(IdempotentRequestInProgressException ex) {
        return problem(HttpStatus.CONFLICT, "idempotency-in-progress", "Request in progress", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail onKeyReused(IdempotencyKeyReusedException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", "Idempotency-Key reused", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
