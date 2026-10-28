package com.tpi.toxicity.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Errores de inicialización; no necesitan el modelo real. */
class OnnxToxicityModelLoadingTest {

    @TempDir
    Path dir;

    @Test
    void missingModelFailsExplicitly() {
        assertThatThrownBy(() -> new OnnxToxicityModel(dir.resolve("no.onnx"), dir.resolve("tokenizer.json"), 256))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void corruptModelFailsExplicitly() throws Exception {
        Path model = Files.writeString(dir.resolve("model.onnx"), "esto no es un modelo");
        Path tok = Files.writeString(dir.resolve("tokenizer.json"), "{}");
        assertThatThrownBy(() -> new OnnxToxicityModel(model, tok, 256))
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void errorMessageDoesNotLeakLocalPaths() {
        Path missing = dir.resolve("secret-dir/no.onnx");
        assertThatThrownBy(() -> new OnnxToxicityModel(missing, dir.resolve("t.json"), 256))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(dir.toString()));
    }
}
