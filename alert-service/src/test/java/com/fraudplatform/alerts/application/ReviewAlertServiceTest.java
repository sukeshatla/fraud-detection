package com.fraudplatform.alerts.application;

import static com.fraudplatform.alerts.application.AlertFixtures.NOW;
import static com.fraudplatform.alerts.application.AlertFixtures.alert;
import static com.fraudplatform.alerts.domain.AlertStatus.CONFIRMED_FRAUD;
import static com.fraudplatform.alerts.domain.AlertStatus.FALSE_POSITIVE;
import static com.fraudplatform.alerts.domain.AlertStatus.OPEN;
import static com.fraudplatform.alerts.domain.AlertStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.alerts.domain.InvalidTransitionException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewAlertServiceTest {

    private static final UUID ID = UUID.fromString("7d9c2f4e-1a2b-4c3d-8e9f-0a1b2c3d4e5f");

    @Mock
    private AlertRepository repository;

    @Mock
    private AlertResolutionPublisher publisher;

    @Mock
    private AlertChangeBus changes;

    private final AtomicBoolean lockReleased = new AtomicBoolean();
    private boolean lockAvailable = true;

    private final AlertLock lock = alertId -> lockAvailable ? Optional.of(() -> lockReleased.set(true)) : Optional.empty();

    private ReviewAlertService service() {
        return new ReviewAlertService(repository, lock, publisher, changes, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("AC-007-05: lock held by another request → AlertLockedException immediately, no DB work")
    void lockedAlertFailsFast() {
        lockAvailable = false;

        assertThatThrownBy(() -> service().review(ID, UNDER_REVIEW, 0, "analyst-1")).isInstanceOf(AlertLockedException.class);
        verifyNoInteractions(repository, publisher, changes);
    }

    @Test
    @DisplayName("AC-007-06: client's version is stale → StaleAlertException carrying the current state")
    void staleVersion() {
        given(repository.findById(ID)).willReturn(Optional.of(alert(ID, UNDER_REVIEW, 4)));

        assertThatThrownBy(() -> service().review(ID, CONFIRMED_FRAUD, 3, "analyst-1"))
                .isInstanceOfSatisfying(StaleAlertException.class, e -> assertThat(e.current().version()).isEqualTo(4));
        verify(repository, never()).transition(any(), anyLong(), any(), any(), any());
        assertThat(lockReleased).isTrue();
    }

    @Test
    @DisplayName("AC-007-04: invalid transition → InvalidTransitionException, lock released")
    void invalidTransition() {
        given(repository.findById(ID)).willReturn(Optional.of(alert(ID, OPEN, 0)));

        assertThatThrownBy(() -> service().review(ID, CONFIRMED_FRAUD, 0, "analyst-1"))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(lockReleased).isTrue();
    }

    @Test
    @DisplayName("AC-010: unknown alert → AlertNotFoundException")
    void unknownAlert() {
        given(repository.findById(ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().review(ID, UNDER_REVIEW, 0, "analyst-1")).isInstanceOf(AlertNotFoundException.class);
    }

    @Test
    @DisplayName("AC-007-04/08: valid transition goes through the repository (version-checked + audited); not terminal → no event")
    void startReview() {
        given(repository.findById(ID)).willReturn(Optional.of(alert(ID, OPEN, 0)));
        given(repository.transition(ID, 0, UNDER_REVIEW, "analyst-1", NOW)).willReturn(alert(ID, UNDER_REVIEW, 1));

        assertThat(service().review(ID, UNDER_REVIEW, 0, "analyst-1").version()).isEqualTo(1);
        verifyNoInteractions(publisher);
        verify(changes).publish(new AlertChange(AlertChange.Type.UPDATED, alert(ID, UNDER_REVIEW, 1)));
        assertThat(lockReleased).isTrue();
    }

    @Test
    @DisplayName("AC-007-09: resolving publishes AlertResolvedEvent after the transition committed")
    void resolutionIsPublished() {
        given(repository.findById(ID)).willReturn(Optional.of(alert(ID, UNDER_REVIEW, 1)));
        given(repository.transition(ID, 1, FALSE_POSITIVE, "analyst-1", NOW)).willReturn(alert(ID, FALSE_POSITIVE, 2));

        service().review(ID, FALSE_POSITIVE, 1, "analyst-1");

        ArgumentCaptor<AlertResolution> captor = ArgumentCaptor.forClass(AlertResolution.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().resolution()).isEqualTo(FALSE_POSITIVE);
        assertThat(captor.getValue().accountId()).isEqualTo("acc-1001");
        assertThat(captor.getValue().resolvedBy()).isEqualTo("analyst-1");
    }

    @Test
    @DisplayName("AC-007-06: a concurrent writer wins between our read and our write → Stale (from the repository)")
    void concurrentWriterDetectedAtWrite() {
        given(repository.findById(ID)).willReturn(Optional.of(alert(ID, OPEN, 0)));
        given(repository.transition(ID, 0, UNDER_REVIEW, "analyst-1", NOW))
                .willThrow(new StaleAlertException(alert(ID, UNDER_REVIEW, 1)));

        assertThatThrownBy(() -> service().review(ID, UNDER_REVIEW, 0, "analyst-1")).isInstanceOf(StaleAlertException.class);
        assertThat(lockReleased).isTrue();
    }

    @Test
    @DisplayName("AC-008-02: a failed review publishes no change to the live stream")
    void failedReviewPublishesNothing() {
        given(repository.findById(ID)).willReturn(Optional.of(alert(ID, OPEN, 0)));

        assertThatThrownBy(() -> service().review(ID, CONFIRMED_FRAUD, 0, "a")).isInstanceOf(InvalidTransitionException.class);
        verifyNoInteractions(changes);
    }
}
