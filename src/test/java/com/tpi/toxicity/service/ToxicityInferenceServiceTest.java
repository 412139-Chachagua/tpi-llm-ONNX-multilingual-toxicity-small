package com.tpi.toxicity.service;

import com.tpi.toxicity.model.InferenceException;
import com.tpi.toxicity.model.OnnxToxicityModel;
import com.tpi.toxicity.model.ToxicityCategory;
import com.tpi.toxicity.model.ToxicityProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToxicityInferenceServiceTest {

    @Test
    void sigmoidIsAppliedPerCategoryInModelOrder() {
        Map<ToxicityCategory, Double> r =
                ToxicityInferenceService.toProbabilities(new float[]{0f, -100f, 100f, 0f, 0f, 0f, 0f});
        assertThat(r).hasSize(7);
        assertThat(r.get(ToxicityCategory.TOXICITY)).isEqualTo(0.5);
        assertThat(r.get(ToxicityCategory.SEVERE_TOXICITY)).isBetween(0.0, 1e-6);
        assertThat(r.get(ToxicityCategory.OBSCENE)).isBetween(1 - 1e-6, 1.0);
        assertThat(r.keySet()).containsExactly(ToxicityCategory.values());
    }

    @Test
    void rejectsNonFiniteAndWrongSizedOutput() {
        assertThatThrownBy(() -> ToxicityInferenceService.toProbabilities(
                new float[]{0f, Float.NaN, 0f, 0f, 0f, 0f, 0f})).isInstanceOf(InferenceException.class);
        assertThatThrownBy(() -> ToxicityInferenceService.toProbabilities(
                new float[]{0f, Float.POSITIVE_INFINITY, 0f, 0f, 0f, 0f, 0f})).isInstanceOf(InferenceException.class);
        assertThatThrownBy(() -> ToxicityInferenceService.toProbabilities(new float[]{0f, 0f}))
                .isInstanceOf(InferenceException.class);
    }

    @Test
    void rejectsBlankText() {
        ToxicityInferenceService service = service(mock(OnnxToxicityModel.class));
        assertThatThrownBy(() -> service.classify("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.classify("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.classify(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modelFailureIsPropagatedNotMaskedAsScores() {
        OnnxToxicityModel model = mock(OnnxToxicityModel.class);
        when(model.logits("x")).thenThrow(new InferenceException("boom"));
        assertThatThrownBy(() -> service(model).classify("x")).isInstanceOf(InferenceException.class);
    }

    private static ToxicityInferenceService service(OnnxToxicityModel model) {
        return new ToxicityInferenceService(model,
                new ToxicityProperties(null, null, 256, 5000, 2, Duration.ofSeconds(1), ""));
    }
}
