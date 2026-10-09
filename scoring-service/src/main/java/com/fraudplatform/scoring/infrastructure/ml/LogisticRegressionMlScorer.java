package com.fraudplatform.scoring.infrastructure.ml;

import com.fraudplatform.scoring.application.MlScorer;
import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.FeatureExtractor;
import com.fraudplatform.scoring.domain.ml.LogisticRegressionModel;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import java.util.Optional;

/** In-process inference: feature extraction + dot product, on the order of a microsecond. */
public class LogisticRegressionMlScorer implements MlScorer {

    private final FeatureExtractor extractor;
    private final LogisticRegressionModel model;

    public LogisticRegressionMlScorer(FeatureExtractor extractor, LogisticRegressionModel model) {
        this.extractor = extractor;
        this.model = model;
    }

    @Override
    public Optional<MlPrediction> score(Transaction transaction, AccountActivity activity) {
        return Optional.of(new MlPrediction(model.predict(extractor.extract(transaction, activity)), model.version()));
    }
}
