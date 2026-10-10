package com.fraudplatform.alerts.api;

import static com.fraudplatform.alerts.application.AlertFixtures.NOW;
import static com.fraudplatform.alerts.application.AlertFixtures.alert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fraudplatform.alerts.application.AlertDetails;
import com.fraudplatform.alerts.application.AlertLockedException;
import com.fraudplatform.alerts.application.AlertNotFoundException;
import com.fraudplatform.alerts.application.AlertQueryService;
import com.fraudplatform.alerts.application.AuditEntry;
import com.fraudplatform.alerts.application.Cursor;
import com.fraudplatform.alerts.application.KeysetPage;
import com.fraudplatform.alerts.application.OffsetPage;
import com.fraudplatform.alerts.application.ReviewAlertService;
import com.fraudplatform.alerts.application.StaleAlertException;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.InvalidTransitionException;
import com.fraudplatform.alerts.domain.Severity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fraudplatform.alerts.infrastructure.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Import(SecurityConfig.class)
@WebMvcTest(AlertController.class)
class AlertControllerTest {

    private static final UUID ID = UUID.fromString("7d9c2f4e-1a2b-4c3d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AlertQueryService queries;

    @MockitoBean
    private ReviewAlertService reviews;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("AC-007-02: offset page with total count; filters passed through")
    void offsetPage() {
        given(queries.page(AlertStatus.OPEN, Optional.of(Severity.HIGH), 0, 50))
                .willReturn(new OffsetPage(List.of(alert(ID, AlertStatus.OPEN, 0)), 0, 50, 1));

        MvcTestResult result = mvc.get().uri("/api/v1/alerts?status=OPEN&severity=HIGH&page=0&size=50").with(ANALYST).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(ID.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].ruleHits[0].code").isEqualTo("HIGH_AMOUNT");
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    @DisplayName("AC-007-02: keyset feed returns an opaque nextCursor and accepts it back")
    void keysetFeed() {
        Cursor cursor = new Cursor(NOW, ID);
        given(queries.after(eq(AlertStatus.OPEN), eq(Optional.empty()), eq(Optional.of(cursor)), anyInt()))
                .willReturn(new KeysetPage(List.of(alert(ID, AlertStatus.OPEN, 0)), Optional.of(cursor)));

        MvcTestResult result = mvc.get().uri("/api/v1/alerts/feed?status=OPEN&after=" + cursor.encode()).with(ANALYST).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isEqualTo(cursor.encode());
    }

    @Test
    void invalidCursorIs400() {
        assertThat(mvc.get().uri("/api/v1/alerts/feed?after=garbage").with(ANALYST).exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("AC-007-10: details include the audit trail")
    void details() {
        given(queries.details(ID)).willReturn(new AlertDetails(alert(ID, AlertStatus.UNDER_REVIEW, 1),
                List.of(new AuditEntry(AlertStatus.OPEN, AlertStatus.UNDER_REVIEW, "analyst-1", NOW))));

        MvcTestResult result = mvc.get().uri("/api/v1/alerts/" + ID).with(ANALYST).exchange();

        assertThat(result).bodyJson().extractingPath("$.alert.status").isEqualTo("UNDER_REVIEW");
        assertThat(result).bodyJson().extractingPath("$.history[0].actor").isEqualTo("analyst-1");
    }

    @Test
    @DisplayName("AC-007-10: unknown alert → 404")
    void notFound() {
        given(queries.details(ID)).willThrow(new AlertNotFoundException(ID));

        assertThat(mvc.get().uri("/api/v1/alerts/" + ID).with(ANALYST).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("AC-007-04: PATCH applies the transition with the client's version and actor")
    void patch() {
        given(reviews.review(ID, AlertStatus.UNDER_REVIEW, 0, "analyst-1")).willReturn(alert(ID, AlertStatus.UNDER_REVIEW, 1));

        MvcTestResult result = patch("{\"status\":\"UNDER_REVIEW\",\"version\":0}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    @DisplayName("AC-007-05: lock held → 409 alert-locked")
    void locked() {
        given(reviews.review(any(), any(), anyLong(), any())).willThrow(new AlertLockedException(ID));

        MvcTestResult result = patch("{\"status\":\"UNDER_REVIEW\",\"version\":0}");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("alert-locked");
    }

    @Test
    @DisplayName("AC-007-06: stale version → 409 with the current alert in the body")
    void stale() {
        given(reviews.review(any(), any(), anyLong(), any())).willThrow(new StaleAlertException(alert(ID, AlertStatus.UNDER_REVIEW, 4)));

        MvcTestResult result = patch("{\"status\":\"CONFIRMED_FRAUD\",\"version\":3}");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("stale-version");
        assertThat(result).bodyJson().extractingPath("$.current.version").isEqualTo(4);
    }

    @Test
    @DisplayName("AC-007-04: invalid transition → 409 invalid-transition")
    void invalidTransition() {
        given(reviews.review(any(), any(), anyLong(), any()))
                .willThrow(new InvalidTransitionException(AlertStatus.OPEN, AlertStatus.CONFIRMED_FRAUD));

        assertThat(patch("{\"status\":\"CONFIRMED_FRAUD\",\"version\":0}"))
                .bodyJson().extractingPath("$.type").asString().endsWith("invalid-transition");
    }

    @Test
    void missingVersionIs400() {
        assertThat(patch("{\"status\":\"UNDER_REVIEW\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("AC-015-03: an ANALYST may triage but not close an alert (terminal status needs SUPERVISOR)")
    void analystCannotClose() {
        assertThat(patch("{\"status\":\"FALSE_POSITIVE\",\"version\":1}", ANALYST)).hasStatus(HttpStatus.FORBIDDEN);
        verify(reviews, never()).review(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("AC-015-03: the audit actor comes from the token; a forged X-Actor header is ignored")
    void actorFromToken() {
        given(reviews.review(ID, AlertStatus.UNDER_REVIEW, 0, "analyst-1")).willReturn(alert(ID, AlertStatus.UNDER_REVIEW, 1));

        MvcTestResult result = mvc.patch().uri("/api/v1/alerts/" + ID).header("X-Actor", "someone-else").with(ANALYST)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNDER_REVIEW\",\"version\":0}").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        verify(reviews).review(ID, AlertStatus.UNDER_REVIEW, 0, "analyst-1");
    }

    @Test
    @DisplayName("AC-015-02: no token → 401; a machine token without ANALYST → 403")
    void unauthenticatedAndWrongRole() {
        assertThat(mvc.get().uri("/api/v1/alerts").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/alerts").with(user("payment-gateway", "INGEST")).exchange()).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult patch(String body) {
        return patch(body, SUPERVISOR);
    }

    private MvcTestResult patch(String body, RequestPostProcessor as) {
        return mvc.patch().uri("/api/v1/alerts/" + ID).with(as)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static final RequestPostProcessor ANALYST = user("analyst-1", "ANALYST");
    private static final RequestPostProcessor SUPERVISOR = user("analyst-1", "ANALYST", "SUPERVISOR");

    private static RequestPostProcessor user(String name, String... roles) {
        return jwt().jwt(token -> token.subject(name)).authorities(
                java.util.Arrays.stream(roles).map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
