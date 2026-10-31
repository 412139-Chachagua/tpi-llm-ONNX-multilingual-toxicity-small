package com.tpi.toxicity.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Errores de inicialización del modelo español; no necesitan el modelo real. */
class SpanishOnnxModelLoadingTest {

    static final String CONFIG = "{\"id2label\":{\"0\":\"NONTOXIC\",\"1\":\"TOXIC\"}}";

    @TempDir
    Path dir;

    @Test
    void missingFilesFailExplicitly() throws Exception {
        Path config = Files.writeString(dir.resolve("config.json"), CONFIG);
        assertThatThrownBy(() -> new SpanishOnnxModel(dir.resolve("no.onnx"), dir.resolve("tokenizer.json"), config, 256))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void corruptModelFailsExplicitly() throws Exception {
        Path model = Files.writeString(dir.resolve("model.onnx"), "esto no es un modelo");
        Path tok = Files.writeString(dir.resolve("tokenizer.json"), "{}");
        Path config = Files.writeString(dir.resolve("config.json"), CONFIG);
        assertThatThrownBy(() -> new SpanishOnnxModel(model, tok, config, 256))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void invalidLabelConfigFailsExplicitly() throws Exception {
        for (String bad : new String[]{"{}", "not json", "{\"id2label\":{\"0\":\"A\",\"1\":\"B\"}}",
                "{\"id2label\":{\"0\":\"TOXIC\"}}", "{\"id2label\":{\"0\":\"TOXIC\",\"1\":\"TOXIC\"}}"}) {
            Path config = Files.writeString(dir.resolve("bad.json"), bad);
            assertThatThrownBy(() -> SpanishLabels.load(config)).isInstanceOf(ModelUnavailableException.class);
        }
    }

    @Test
    void labelOrderIsReadFromId2label() throws Exception {
        Path normal = Files.writeString(dir.resolve("a.json"), CONFIG);
        Path swapped = Files.writeString(dir.resolve("b.json"), "{\"id2label\":{\"0\":\"TOXIC\",\"1\":\"NONTOXIC\"}}");
        assertThat(SpanishLabels.load(normal)).isEqualTo(List.of("NONTOXIC", "TOXIC"));
        assertThat(SpanishLabels.load(swapped)).isEqualTo(List.of("TOXIC", "NONTOXIC"));
    }

    @Test
    void errorMessageDoesNotLeakLocalPaths() {
        Path missing = dir.resolve("secret-dir/no.onnx");
        assertThatThrownBy(() -> new SpanishOnnxModel(missing, dir.resolve("t.json"), dir.resolve("c.json"), 256))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(dir.toString()));
    }
}
