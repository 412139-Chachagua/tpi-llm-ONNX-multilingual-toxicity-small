package com.tpi.toxicity.model;

import com.tpi.toxicity.service.SpanishToxicityInferenceService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Inferencia con el modelo español real. Se omite si los artefactos de models/spanish/ no están
 * presentes; ver docs/model-spanish.md. Las referencias son logits de PyTorch (transformers) sobre la
 * revisión fijada del modelo original.
 */
@DisabledOnOs(value = OS.MAC, architectures = "x86_64")
@EnabledIf("artifactsPresent")
class SpanishOnnxModelRealTest {

    static final Path DIR = Path.of("models/spanish");

    static SpanishOnnxModel model;
    static SpanishToxicityInferenceService service;

    static boolean artifactsPresent() {
        return Files.isRegularFile(DIR.resolve("model.onnx")) && Files.isRegularFile(DIR.resolve("tokenizer.json"))
                && Files.isRegularFile(DIR.resolve("config.json"));
    }

    @BeforeAll
    static void load() {
        model = new SpanishOnnxModel(DIR.resolve("model.onnx"), DIR.resolve("tokenizer.json"), DIR.resolve("config.json"), 256);
        service = new SpanishToxicityInferenceService(model,
                new SpanishToxicityProperties(null, null, null, "rev", 256, 2, 0),
                new ToxicityProperties(null, null, 256, 5000, 4, Duration.ofSeconds(30), "", true));
    }

    @AfterAll
    static void close() throws Exception {
        model.destroy();
    }

    @Test
    void labelsComeFromTheModelConfig() {
        assertThat(model.labels()).containsExactly("NONTOXIC", "TOXIC");
    }

    @Test
    void logitsMatchPyTorchReference() {
        assertThat(model.logits("Sos un inútil, no servís para nada")[0]).isCloseTo(-0.5523130f, within(1e-3f));
        assertThat(model.logits("Sos un inútil, no servís para nada")[1]).isCloseTo(0.8784074f, within(1e-3f));
        assertThat(model.logits("Gracias por tu ayuda, que tengas un lindo día")[0]).isCloseTo(1.0757105f, within(1e-3f));
        assertThat(model.logits("Gracias por tu ayuda, que tengas un lindo día")[1]).isCloseTo(-1.1269292f, within(1e-3f));
        // 256 tokens tras truncar: Hola + emoji + texto largo
        float[] longText = model.logits("Hola 😀 ¿cómo estás? " + "palabra ".repeat(300));
        assertThat(longText[0]).isCloseTo(0.2616057f, within(1e-3f));
        assertThat(longText[1]).isCloseTo(-0.0966275f, within(1e-3f));
    }

    @Test
    void realClassificationUsesSoftmaxLabels() {
        var toxic = service.classify("Sos un inútil, no servís para nada");
        var fine = service.classify("Gracias por tu ayuda, que tengas un lindo día");
        assertThat(toxic.label()).isEqualTo("TOXIC");
        assertThat(fine.label()).isEqualTo("NONTOXIC");
        assertThat(toxic.scores().values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
    }
}
