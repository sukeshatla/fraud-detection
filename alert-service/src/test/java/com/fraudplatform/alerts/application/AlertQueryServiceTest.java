package com.fraudplatform.alerts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertQueryServiceTest {

    @Mock
    private AlertRepository repository;

    @Test
    @DisplayName("Page size is clamped to 1..100")
    void clampsPageSize() {
        given(repository.findPage(any())).willReturn(new OffsetPage(List.of(), 0, 100, 0));

        new AlertQueryService(repository).page(AlertStatus.OPEN, Optional.of(Severity.HIGH), 0, 5000);

        verify(repository).findPage(new PageQuery(AlertStatus.OPEN, Optional.of(Severity.HIGH), 0, 100));
    }

    @Test
    @DisplayName("Cursor round-trips through its opaque string form")
    void cursorRoundTrip() {
        Cursor cursor = new Cursor(Instant.parse("2026-10-09T19:00:00.123456Z"), UUID.randomUUID());

        assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    @DisplayName("A tampered cursor is rejected as a client error")
    void rejectsGarbageCursor() {
        assertThatThrownBy(() -> Cursor.decode("not-a-cursor")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-007-10: unknown alert → AlertNotFoundException")
    void detailsNotFound() {
        UUID id = UUID.randomUUID();
        given(repository.findDetails(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> new AlertQueryService(repository).details(id)).isInstanceOf(AlertNotFoundException.class);
    }
}
