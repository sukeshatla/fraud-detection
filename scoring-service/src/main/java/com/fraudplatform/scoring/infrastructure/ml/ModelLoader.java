package com.fraudplatform.scoring.infrastructure.ml;

import com.fraudplatform.scoring.domain.ml.LogisticRegressionModel;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.Resource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads the JSON artifact written by ml/train.py. Fails fast at startup if it's missing or malformed. */
public class ModelLoader {

    public record LoadedModel(LogisticRegressionModel model, double auc) {}

    private final JsonMapper mapper;

    public ModelLoader(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public LoadedModel load(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = mapper.readTree(in);
            LogisticRegressionModel model = new LogisticRegressionModel(
                    root.required("modelVersion").asString(),
                    mapper.convertValue(root.required("features"), mapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                    doubles(root.required("means")),
                    doubles(root.required("stds")),
                    doubles(root.required("weights")),
                    root.required("intercept").asDouble());
            return new LoadedModel(model, root.path("metrics").path("auc").asDouble(Double.NaN));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load ML model from " + resource, e);
        }
    }

    private static double[] doubles(JsonNode array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).asDouble();
        }
        return values;
    }
}
