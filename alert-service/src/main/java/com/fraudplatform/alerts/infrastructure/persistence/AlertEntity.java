package com.fraudplatform.alerts.infrastructure.persistence;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA mapping of the {@code alert} table (persistence model, not the domain model).
 *
 * <p>{@link Version}: Hibernate adds {@code AND version = ?} to every UPDATE and increments it.
 * If another transaction changed the row first, the UPDATE matches 0 rows and Hibernate throws
 * {@code OptimisticLockException}. That's layer 2 of the concurrency protection.
 *
 * <p>{@code ruleHits} is LAZY, the right default. Touching it per row in a list is exactly the N+1
 * problem; the adapter fetches hits for a whole page in one query instead.
 */
@Entity
@Table(name = "alert")
public class AlertEntity {

    @Id
    private UUID id;

    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String merchantCategoryCode;
    private String country;
    private String channel;
    private Instant occurredAt;
    private int ruleScore;
    private int riskScore;
    private BigDecimal mlProbability;
    private String modelVersion;
    private String decision;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    private AlertStatus status;

    @Version
    private long version;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "alert", fetch = FetchType.LAZY)
    @OrderBy("weight DESC, code ASC")
    private List<AlertRuleHitEntity> ruleHits = new ArrayList<>();

    protected AlertEntity() {} // JPA

    void moveTo(AlertStatus next, Instant at) {
        this.status = next;
        this.updatedAt = at;
    }

    public UUID getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMerchantId() { return merchantId; }
    public String getMerchantCategoryCode() { return merchantCategoryCode; }
    public String getCountry() { return country; }
    public String getChannel() { return channel; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getRuleScore() { return ruleScore; }
    public int getRiskScore() { return riskScore; }
    public BigDecimal getMlProbability() { return mlProbability; }
    public String getModelVersion() { return modelVersion; }
    public String getDecision() { return decision; }
    public Severity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<AlertRuleHitEntity> getRuleHits() { return ruleHits; }
}
