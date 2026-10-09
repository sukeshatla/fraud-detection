package com.fraudplatform.alerts.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_rule_hit")
public class AlertRuleHitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private AlertEntity alert;

    private String code;
    private int weight;
    private String reason;

    protected AlertRuleHitEntity() {} // JPA

    public AlertEntity getAlert() { return alert; }
    public String getCode() { return code; }
    public int getWeight() { return weight; }
    public String getReason() { return reason; }
}
