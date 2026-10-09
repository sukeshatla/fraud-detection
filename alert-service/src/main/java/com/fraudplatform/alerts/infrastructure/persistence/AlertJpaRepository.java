package com.fraudplatform.alerts.infrastructure.persistence;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository. Used by the tests to demonstrate the naive (N+1) access pattern that
 * {@link JpaAlertRepository} avoids.
 */
public interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {

    List<AlertEntity> findByStatusAndSeverityOrderByCreatedAtDescIdDesc(AlertStatus status, Severity severity, Pageable page);
}
