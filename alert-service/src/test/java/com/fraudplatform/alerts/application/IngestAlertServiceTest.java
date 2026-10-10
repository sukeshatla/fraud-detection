package com.fraudplatform.alerts.application;

import static com.fraudplatform.alerts.application.AlertFixtures.NOW;
import static com.fraudplatform.alerts.application.AlertFixtures.newAlert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.alerts.domain.AlertStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestAlertServiceTest {

    @Mock
    private AlertRepository repository;

    @Mock
    private AlertChangeBus changes;

    @Mock
    private AlertMetrics metrics;

    private final NewAlert alert = newAlert(UUID.randomUUID(), "txn-1", "acc-1", NOW);

    @Test
    @DisplayName("AC-008-02: a NEW alert is announced on the change bus as an OPEN, version-0 view")
    void announcesNewAlert() {
        given(repository.insertIfAbsent(alert)).willReturn(true);

        assertThat(new IngestAlertService(repository, changes, metrics).ingest(alert)).isTrue();

        ArgumentCaptor<AlertChange> captor = ArgumentCaptor.forClass(AlertChange.class);
        verify(changes).publish(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertChange.Type.CREATED);
        assertThat(captor.getValue().alert().id()).isEqualTo(alert.id());
        assertThat(captor.getValue().alert().status()).isEqualTo(AlertStatus.OPEN);
        assertThat(captor.getValue().alert().version()).isZero();
        verify(metrics).raised(com.fraudplatform.alerts.domain.Severity.HIGH);
    }

    @Test
    @DisplayName("AC-007-01: a redelivered alert is not announced again")
    void duplicateIsSilent() {
        given(repository.insertIfAbsent(alert)).willReturn(false);

        assertThat(new IngestAlertService(repository, changes, metrics).ingest(alert)).isFalse();
        verifyNoInteractions(changes, metrics);
    }
}
