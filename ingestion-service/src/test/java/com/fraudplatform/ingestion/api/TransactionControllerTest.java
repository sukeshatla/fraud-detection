package com.fraudplatform.ingestion.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.ingestion.application.EventPublishingException;
import com.fraudplatform.ingestion.application.IngestTransactionService;
import com.fraudplatform.ingestion.application.IngestionReceipt;
import com.fraudplatform.ingestion.application.TransactionRejectedException;
import com.fraudplatform.ingestion.domain.Channel;
import com.fraudplatform.ingestion.domain.Money;
import com.fraudplatform.ingestion.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String URL = "/api/v1/transactions";
    private static final UUID EVENT_ID = UUID.fromString("0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11");
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-09T18:15:30.120Z");

    private static final String VALID_JSON = """
            {
              "transactionId": "txn-7f3a9c",
              "accountId": "acc-1001",
              "amount": 249.99,
              "currency": "USD",
              "merchantId": "m-5541",
              "merchantCategoryCode": "5732",
              "country": "US",
              "channel": "CARD_NOT_PRESENT",
              "occurredAt": "2026-10-09T18:15:30Z"
            }
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IngestTransactionService service;

    @Test
    @DisplayName("AC-001-01: valid transaction → 202 with transactionId, eventId, status, receivedAt")
    void acceptsValidTransaction() {
        given(service.ingest(any())).willReturn(new IngestionReceipt("txn-7f3a9c", EVENT_ID, RECEIVED_AT));

        MvcTestResult result = post(VALID_JSON);

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.transactionId").isEqualTo("txn-7f3a9c");
        assertThat(result).bodyJson().extractingPath("$.eventId").isEqualTo(EVENT_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACCEPTED");
        assertThat(result).bodyJson().extractingPath("$.receivedAt").isEqualTo("2026-10-09T18:15:30.120Z");
    }

    @Test
    @DisplayName("AC-001-01: request is mapped to the domain transaction unchanged")
    void mapsRequestToDomain() {
        given(service.ingest(any())).willReturn(new IngestionReceipt("txn-7f3a9c", EVENT_ID, RECEIVED_AT));

        post(VALID_JSON);

        verify(service).ingest(new Transaction(
                "txn-7f3a9c",
                "acc-1001",
                new Money(new BigDecimal("249.99"), Currency.getInstance("USD")),
                "m-5541",
                "5732",
                "US",
                Channel.CARD_NOT_PRESENT,
                Instant.parse("2026-10-09T18:15:30Z")));
    }

    @Test
    @DisplayName("AC-001-03: empty payload → 400 problem+json listing every missing field; nothing ingested")
    void reportsEveryMissingField() {
        MvcTestResult result = post("{}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).contentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("validation-error");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().contains(
                "transactionId", "accountId", "amount", "currency", "merchantId",
                "merchantCategoryCode", "country", "channel", "occurredAt");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-001-03: invalid values → 400 naming each offending field")
    void reportsInvalidValues() {
        String json = VALID_JSON
                .replace("\"txn-7f3a9c\"", "\"bad id!\"")
                .replace("249.99", "-5")
                .replace("\"5732\"", "\"57\"");

        MvcTestResult result = post(json);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("transactionId", "amount", "merchantCategoryCode");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-001-03: more than 2 fraction digits → 400 on amount")
    void rejectsExcessPrecision() {
        MvcTestResult result = post(VALID_JSON.replace("249.99", "249.999"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("amount");
    }

    @Test
    @DisplayName("AC-001-07: unknown ISO currency and country codes → 400 naming both fields")
    void rejectsUnknownIsoCodes() {
        String json = VALID_JSON.replace("\"USD\"", "\"ABC\"").replace("\"US\"", "\"ZZ\"");

        MvcTestResult result = post(json);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("currency", "country");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-001-04: malformed JSON → 400 malformed-request")
    void rejectsMalformedJson() {
        MvcTestResult result = post("{ \"transactionId\": ");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("malformed-request");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-001-04: unknown enum value → 400 malformed-request naming the field")
    void rejectsUnknownChannel() {
        MvcTestResult result = post(VALID_JSON.replace("CARD_NOT_PRESENT", "TELEPATHY"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("malformed-request");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("channel");
    }

    @Test
    @DisplayName("AC-001-05: business-rule rejection → 400 transaction-rejected")
    void mapsBusinessRejection() {
        given(service.ingest(any())).willThrow(new TransactionRejectedException("occurredAt is in the future"));

        MvcTestResult result = post(VALID_JSON);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("transaction-rejected");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("occurredAt is in the future");
    }

    @Test
    @DisplayName("AC-001-03: domain invariant (JPY has no minor units) → 400")
    void mapsDomainValidation() {
        MvcTestResult result = post(VALID_JSON.replace("\"USD\"", "\"JPY\""));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("transaction-rejected");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-001-06: broker not acknowledging → 503 with Retry-After")
    void mapsPublishFailureToServiceUnavailable() {
        given(service.ingest(any())).willThrow(new EventPublishingException("timeout", null));

        MvcTestResult result = post(VALID_JSON);

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).hasHeader("Retry-After", "1");
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("publish-failed");
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
