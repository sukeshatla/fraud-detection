package com.fraudplatform.alerts.api;

import com.fraudplatform.alerts.application.AlertDetails;
import com.fraudplatform.alerts.application.AlertQueryService;
import com.fraudplatform.alerts.application.AlertStats;
import com.fraudplatform.alerts.application.AlertView;
import com.fraudplatform.alerts.application.Cursor;
import com.fraudplatform.alerts.application.KeysetPage;
import com.fraudplatform.alerts.application.OffsetPage;
import com.fraudplatform.alerts.application.ReviewAlertService;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Analyst queue API. */
@RestController
@RequestMapping("/api/v1/alerts")
class AlertController {

    record AlertFeedResponse(List<AlertView> items, String nextCursor) {}

    /** {@code version} is the client's optimistic-concurrency token (like an ETag for If-Match). */
    record ReviewRequest(@NotNull AlertStatus status, @NotNull @PositiveOrZero Long version) {}

    private final AlertQueryService queries;
    private final ReviewAlertService reviews;

    AlertController(AlertQueryService queries, ReviewAlertService reviews) {
        this.queries = queries;
        this.reviews = reviews;
    }

    /** Offset pagination with a total: fine for page-numbered UIs and shallow pages. */
    @GetMapping
    OffsetPage page(@RequestParam(defaultValue = "OPEN") AlertStatus status, @RequestParam Optional<Severity> severity,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return queries.page(status, severity, page, size);
    }

    /** Keyset pagination for the live queue: O(page) at any depth and stable while new alerts arrive. */
    @GetMapping("/feed")
    AlertFeedResponse feed(@RequestParam(defaultValue = "OPEN") AlertStatus status, @RequestParam Optional<Severity> severity,
            @RequestParam Optional<String> after, @RequestParam(defaultValue = "50") int size) {
        Optional<Cursor> cursor;
        try {
            cursor = after.map(Cursor::decode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
        KeysetPage page = queries.after(status, severity, cursor, size);
        return new AlertFeedResponse(page.items(), page.next().map(Cursor::encode).orElse(null));
    }

    /** KPIs for the dashboard header; "last hour" window. */
    @GetMapping("/stats")
    AlertStats stats() {
        return queries.stats();
    }

    @GetMapping("/{id}")
    AlertDetails details(@PathVariable UUID id) {
        return queries.details(id);
    }

    /**
     * The actor is the authenticated user (from the token, never from a client-supplied header), so the
     * audit trail can't be forged. Closing an alert (a terminal status) requires the SUPERVISOR role.
     */
    @PatchMapping("/{id}")
    AlertView review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request, Authentication user) {
        if (request.status().isTerminal() && !hasRole(user, "ROLE_SUPERVISOR")) {
            throw new AccessDeniedException("Closing an alert requires the SUPERVISOR role");
        }
        return reviews.review(id, request.status(), request.version(), user.getName());
    }

    private static boolean hasRole(Authentication user, String role) {
        return user.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
