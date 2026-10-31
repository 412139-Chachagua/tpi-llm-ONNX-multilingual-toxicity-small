package com.tpi.toxicity.service;

import com.tpi.toxicity.model.InferenceException;
import com.tpi.toxicity.model.InvalidTextException;
import com.tpi.toxicity.model.ServiceSaturatedException;
import com.tpi.toxicity.model.SpanishOnnxModel;
import com.tpi.toxicity.model.SpanishToxicityProperties;
import com.tpi.toxicity.model.ToxicityProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpanishToxicityInferenceServiceTest {

    static final List<String> LABELS = List.of("NONTOXIC", "TOXIC");

    @Test
    void softmaxSumsToOneAndLabelIsTheArgmax() {
        var r = SpanishToxicityInferenceService.toResult(LABELS, new float[]{-0.5523130f, 0.8784073f});
        assertThat(r.label()).isEqualTo("TOXIC");
        assertThat(r.scores().keySet()).containsExactly("NONTOXIC", "TOXIC");
        assertThat(r.scores().values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(r.scores().get("TOXIC")).isCloseTo(1 / (1 + Math.exp(-(0.8784073 + 0.5523130))), org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void labelOrderComesFromTheModelConfigNotFromAssumption() {
        var swapped = SpanishToxicityInferenceService.toResult(List.of("TOXIC", "NONTOXIC"), new float[]{3f, -3f});
        assertThat(swapped.label()).isEqualTo("TOXIC");
        var normal = SpanishToxicityInferenceService.toResult(LABELS, new float[]{3f, -3f});
        assertThat(normal.label()).isEqualTo("NONTOXIC");
    }

    @Test
    void softmaxIsStableWithExtremeLogits() {
        var r = SpanishToxicityInferenceService.toResult(LABELS, new float[]{-1000f, 1000f});
        assertThat(r.scores().get("TOXIC")).isEqualTo(1.0);
        assertThat(r.scores().get("NONTOXIC")).isEqualTo(0.0);
    }

    @Test
    void rejectsNonFiniteAndWrongSizedOutput() {
        assertThatThrownBy(() -> SpanishToxicityInferenceService.toResult(LABELS, new float[]{Float.NaN, 0f}))
                .isInstanceOf(InferenceException.class);
        assertThatThrownBy(() -> SpanishToxicityInferenceService.toResult(LABELS, new float[]{0f, Float.POSITIVE_INFINITY}))
                .isInstanceOf(InferenceException.class);
        assertThatThrownBy(() -> SpanishToxicityInferenceService.toResult(LABELS, new float[]{0f}))
                .isInstanceOf(InferenceException.class);
    }

    @Test
    void rejectsBlankAndNullText() {
        var service = service(mock(SpanishOnnxModel.class), 1);
        assertThatThrownBy(() -> service.classify("")).isInstanceOf(InvalidTextException.class);
        assertThatThrownBy(() -> service.classify("   ")).isInstanceOf(InvalidTextException.class);
        assertThatThrownBy(() -> service.classify(null)).isInstanceOf(InvalidTextException.class);
    }

    @Test
    void toxicAndNonToxicTextAreClassifiedFromTheModelLogits() {
        SpanishOnnxModel model = mock(SpanishOnnxModel.class);
        when(model.labels()).thenReturn(LABELS);
        when(model.logits("Sos un inútil, no servís para nada")).thenReturn(new float[]{-2f, 2f});
        when(model.logits("Gracias por tu ayuda")).thenReturn(new float[]{2f, -2f});
        var service = service(model, 1);
        assertThat(service.classify("Sos un inútil, no servís para nada").label()).isEqualTo("TOXIC");
        assertThat(service.classify("Gracias por tu ayuda").label()).isEqualTo("NONTOXIC");
    }

    @Test
    void modelFailureIsPropagatedNotMaskedAsScores() {
        SpanishOnnxModel model = mock(SpanishOnnxModel.class);
        when(model.labels()).thenReturn(LABELS);
        when(model.logits(anyString())).thenThrow(new InferenceException("boom"));
        assertThatThrownBy(() -> service(model, 1).classify("hola")).isInstanceOf(InferenceException.class);
    }

    @Test
    void saturatedServiceFailsFastAndReleasesPermits() {
        SpanishOnnxModel model = mock(SpanishOnnxModel.class);
        when(model.labels()).thenReturn(LABELS);
        when(model.logits(anyString())).thenReturn(new float[]{0f, 0f});
        var service = service(model, 0);
        assertThatThrownBy(() -> service.classify("hola")).isInstanceOf(ServiceSaturatedException.class);
        var ok = service(model, 1);
        ok.classify("hola");
        ok.classify("hola");
    }

    private static SpanishToxicityInferenceService service(SpanishOnnxModel model, int permits) {
        var props = new SpanishToxicityProperties(null, null, null, "rev", 256, permits, 0);
        var general = new ToxicityProperties(null, null, 256, 5000, 4, Duration.ofMillis(50), "", true);
        return new SpanishToxicityInferenceService(model, props, general);
    }
}
