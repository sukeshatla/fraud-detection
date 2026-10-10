package com.fraudplatform.scoring.infrastructure.ml;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.FeatureExtractor;
import com.fraudplatform.scoring.domain.ml.LogisticRegressionModel;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-006-06: guards against training/serving skew. ml/train.py stores reference inputs, the
 * features it computed and the probability it predicted. Java must reproduce both exactly.
 */
class ModelParityTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant MIDNIGHT = Instant.parse("2026-10-09T00:00:00Z");

    private static final ModelLoader.LoadedModel LOADED = new ModelLoader(MAPPER).load(new ClassPathResource("ml/model-v1.json"));
    private static final FeatureExtractor EXTRACTOR = new FeatureExtractor(Map.of(), Set.of("7995"));

    static Stream<JsonNode> referenceCases() {
        JsonNode root = MAPPER.readTree(read());
        return root.get("referenceCases").valueStream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("referenceCases")
    @DisplayName("AC-006-06: Java features and probability match the Python training pipeline")
    void javaMatchesPython(JsonNode ref) {
        Transaction tx = aTransaction()
                .amount(ref.get("amountUsd").asString())
                .currency("USD")
                .mcc(ref.get("highRiskMcc").asBoolean() ? "7995" : "5411")
                .channel(ref.get("cardNotPresent").asBoolean() ? "CARD_NOT_PRESENT" : "CARD_PRESENT")
                .country("US")
                .occurredAt(MIDNIGHT.plus(Duration.ofSeconds(Math.round(ref.get("hourUtc").asDouble() * 3600))))
                .build();
        AccountActivity activity = new AccountActivity(1, ref.get("txCount1h").asInt(), ref.get("txCount24h").asInt(),
                ref.get("smallTx5m").asInt(), ref.get("countryChanged").asBoolean() ? "GB" : null,
                ref.get("countryChanged").asBoolean() ? MIDNIGHT : null);

        double[] features = EXTRACTOR.extract(tx, activity);
        LogisticRegressionModel model = LOADED.model();

        for (int i = 0; i < features.length; i++) {
            assertThat(features[i]).as(FeatureExtractor.FEATURE_NAMES.get(i))
                    .isCloseTo(ref.get("expectedFeatures").get(i).asDouble(), within(1e-9));
        }
        assertThat(model.predict(features)).isCloseTo(ref.get("expectedProbability").asDouble(), within(1e-6));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Model file declares the same feature names, in the same order, as the Java extractor")
    void featureNamesMatch() {
        assertThat(LOADED.model().featureNames()).isEqualTo(FeatureExtractor.FEATURE_NAMES);
        assertThat(LOADED.model().version()).isEqualTo("lr-v1");
        assertThat(LOADED.auc()).isGreaterThan(0.85);
    }

    private static String read() {
        try (var in = new ClassPathResource("ml/model-v1.json").getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
