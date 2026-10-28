package com.tpi.toxicity.model;

import com.tpi.toxicity.service.ToxicityInferenceService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inferencia con el modelo real. Se omite (skipped) si los artefactos de models/ no están
 * descargados; ver docs/model.md.
 */
// DJL tokenizers no publica librería nativa para macOS Intel (ninguna versión); allí se ejecuta en Docker.
@DisabledOnOs(value = OS.MAC, architectures = "x86_64")
@EnabledIf("artifactsPresent")
class OnnxToxicityModelRealTest {

    static final Path MODEL = Path.of("models/onnx/model.onnx");
    static final Path TOKENIZER = Path.of("models/tokenizer.json");

    static OnnxToxicityModel model;
    static ToxicityInferenceService service;

    static boolean artifactsPresent() {
        return Files.isRegularFile(MODEL) && Files.isRegularFile(TOKENIZER);
    }

    @BeforeAll
    static void load() {
        model = new OnnxToxicityModel(MODEL, TOKENIZER, 256);
        service = new ToxicityInferenceService(model,
                new ToxicityProperties(MODEL, TOKENIZER, 256, 5000, 2, Duration.ofSeconds(30), "", false));
    }

    @AfterAll
    static void close() throws Exception {
        model.destroy();
    }

    @Test
    void offensiveTextScoresHigherThanBenignText() {
        var benign = service.classify("Thanks for sharing, this was really helpful!");
        var insult = service.classify("You are a complete idiot and nobody wants you here.");
        assertValid(benign);
        assertValid(insult);
        assertThat(insult.get(ToxicityCategory.TOXICITY)).isGreaterThan(benign.get(ToxicityCategory.TOXICITY));
        assertThat(insult.get(ToxicityCategory.INSULT)).isGreaterThan(benign.get(ToxicityCategory.INSULT));
    }

    @Test
    void multilingualAndSpecialCharacters() {
        for (String t : new String[]{"Eres un inútil, lárgate de aquí.", "Halt die Klappe, du Vollidiot.",
                "¡Qué día tan hermoso! 😀🎉", "ñandú café über 你好 مرحبا", "a", "<script>alert(1)</script>"}) {
            assertValid(service.classify(t));
        }
        assertThat(service.classify("Eres un inútil, lárgate de aquí.").get(ToxicityCategory.TOXICITY))
                .isGreaterThan(service.classify("Gracias por tu ayuda, muy amable.").get(ToxicityCategory.TOXICITY));
    }

    @Test
    void longInputIsTruncatedToMaxTokens() {
        String longText = "This is a perfectly normal sentence about the weather. ".repeat(500);
        assertThat(model.tokenCount(longText)).isEqualTo(256);
        assertValid(service.classify(longText));
    }

    @Test
    void tokenizerAddsBosAndEos() {
        // "hola" -> <bos> ... <eos>: al menos 3 tokens
        assertThat(model.tokenCount("hola")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void inferenceIsDeterministic() {
        assertThat(service.classify("This is a test.")).isEqualTo(service.classify("This is a test."));
    }

    /** Textos fijos en src/test/resources/reference-scores.json; detecta cambios accidentales de modelo/tokenizer/preproceso. */
    @Test
    void scoresMatchRegressionBaseline() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path file = Path.of("src/test/resources/reference-scores.json");
        List<String> texts = List.of("Thanks for sharing, this was really helpful!",
                "You are a complete idiot and nobody wants you here.", "Eres un inútil, lárgate de aquí.",
                "Halt die Klappe, du Vollidiot.", "I will find you and hurt you.", "¡Qué día tan hermoso! 😀🎉", "ok");
        if (Boolean.getBoolean("update.reference")) {
            Map<String, Map<String, Double>> out = new LinkedHashMap<>();
            for (String t : texts) {
                Map<String, Double> m = new LinkedHashMap<>();
                service.classify(t).forEach((k, v) -> m.put(k.label(), v));
                out.put(t, m);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), out);
        }
        Map<String, Map<String, Double>> expected = mapper.readValue(file.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<>() { });
        assertThat(expected.keySet()).containsExactlyElementsOf(texts);
        expected.forEach((text, exp) -> service.classify(text).forEach(
                (cat, v) -> assertThat(v).as(text + " / " + cat.label()).isCloseTo(exp.get(cat.label()), org.assertj.core.data.Offset.offset(1e-4))));
    }

    @Test
    void concurrentRequestsAllReturnValidScores() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Map<ToxicityCategory, Double>>> futures = new ArrayList<>();
            for (int i = 0; i < 48; i++) {
                String t = "Concurrent request number " + i;
                futures.add(pool.submit(() -> service.classify(t)));
            }
            for (Future<Map<ToxicityCategory, Double>> f : futures) {
                assertValid(f.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertValid(Map<ToxicityCategory, Double> scores) {
        assertThat(scores.keySet()).containsExactly(ToxicityCategory.values());
        assertThat(scores.values()).allSatisfy(v -> {
            assertThat(v).isFinite();
            assertThat(v).isBetween(0.0, 1.0);
        });
    }
}
