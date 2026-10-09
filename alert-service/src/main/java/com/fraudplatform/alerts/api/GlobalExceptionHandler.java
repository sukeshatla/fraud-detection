package com.fraudplatform.alerts.api;

import com.fraudplatform.alerts.application.AlertLockedException;
import com.fraudplatform.alerts.application.AlertNotFoundException;
import com.fraudplatform.alerts.application.StaleAlertException;
import com.fraudplatform.alerts.domain.InvalidTransitionException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 problem details. All three 409s are distinct so the UI can react differently. */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "urn:fraud-platform:problem:";

    @ExceptionHandler(AlertNotFoundException.class)
    ProblemDetail notFound(AlertNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "alert-not-found", e.getMessage());
    }

    /** Layer 1 said no: someone else is updating it right now. Safe to retry shortly. */
    @ExceptionHandler(AlertLockedException.class)
    ProblemDetail locked(AlertLockedException e) {
        return problem(HttpStatus.CONFLICT, "alert-locked", e.getMessage());
    }

    /** Layer 2 said no: the client's copy is outdated. Return the current state so the UI can refresh. */
    @ExceptionHandler(StaleAlertException.class)
    ProblemDetail stale(StaleAlertException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "stale-version", e.getMessage());
        problem.setProperty("current", e.current());
        return problem;
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ProblemDetail invalidTransition(InvalidTransitionException e) {
        return problem(HttpStatus.CONFLICT, "invalid-transition", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        return problem;
    }
}
