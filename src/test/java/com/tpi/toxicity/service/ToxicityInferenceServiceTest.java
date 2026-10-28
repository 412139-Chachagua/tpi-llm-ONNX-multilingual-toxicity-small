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
        assertThatThrownBy(() -> service.classify("")).isInstanceOf(com.tpi.toxicity.model.InvalidTextException.class);
        assertThatThrownBy(() -> service.classify("   ")).isInstanceOf(com.tpi.toxicity.model.InvalidTextException.class);
        assertThatThrownBy(() -> service.classify(null)).isInstanceOf(com.tpi.toxicity.model.InvalidTextException.class);
    }

    @Test
    void modelFailureIsPropagatedNotMaskedAsScores() {
        OnnxToxicityModel model = mock(OnnxToxicityModel.class);
        when(model.logits("x")).thenThrow(new InferenceException("boom"));
        assertThatThrownBy(() -> service(model).classify("x")).isInstanceOf(InferenceException.class);
    }

    @Test
    void saturationFailsFastWithoutBlockingForever() throws Exception {
        OnnxToxicityModel slow = mock(OnnxToxicityModel.class);
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(slow.logits("slow")).thenAnswer(inv -> {
            started.countDown();
            release.await();
            return new float[7];
        });
        ToxicityInferenceService single = new ToxicityInferenceService(slow,
                new ToxicityProperties(null, null, 256, 5000, 1, Duration.ofMillis(100), "", false));
        Thread t = new Thread(() -> single.classify("slow"));
        t.start();
        started.await();
        assertThatThrownBy(() -> single.classify("other"))
                .isInstanceOf(com.tpi.toxicity.model.ServiceSaturatedException.class);
        release.countDown();
        t.join();
    }

    private static ToxicityInferenceService service(OnnxToxicityModel model) {
        return new ToxicityInferenceService(model,
                new ToxicityProperties(null, null, 256, 5000, 2, Duration.ofSeconds(1), "", false));
    }
}
