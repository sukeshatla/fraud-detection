package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import java.util.Optional;

/**
 * Outbound port: fraud probability from a model. In-process today; an ONNX runtime or a remote
 * model server would be another adapter. Returns empty when the model is unavailable, and the
 * caller then falls back to rules only.
 */
public interface MlScorer {

    Optional<MlPrediction> score(Transaction transaction, AccountActivity activity);
}
