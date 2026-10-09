package com.fraudplatform.scoring.api;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.AlertResolvedEvent;
import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.application.InvalidEventException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Analysts' verdicts feed back into scoring: a FALSE_POSITIVE clears the account's high-risk flag
 * (DB clearance first, then cache eviction). Idempotent: clearing twice is harmless.
 */
@Component
class AlertResolutionListener {

    private final AccountRiskService risk;
    private final JsonMapper mapper;

    AlertResolutionListener(AccountRiskService risk, JsonMapper mapper) {
        this.risk = risk;
        this.mapper = mapper;
    }

    @KafkaListener(id = "scoring-resolutions", topics = Topics.ALERT_RESOLUTIONS, groupId = "scoring-resolutions", batch = "false")
    void onResolution(String payload) {
        AlertResolvedEvent event;
        try {
            event = mapper.readValue(payload, AlertResolvedEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Unprocessable AlertResolvedEvent", e);
        }
        if ("FALSE_POSITIVE".equals(event.resolution())) {
            risk.clear(event.accountId());
        }
    }
}
