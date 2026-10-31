package com.tpi.toxicity.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Etiquetas del modelo español, leídas de {@code id2label} en su config.json (el orden no se supone). */
final class SpanishLabels {

    static final Set<String> EXPECTED = Set.of("NONTOXIC", "TOXIC");

    private SpanishLabels() {
    }

    /** Devuelve las etiquetas indexadas por id de salida; falla si no son exactamente NONTOXIC y TOXIC. */
    static List<String> load(Path configPath) {
        try {
            JsonNode id2label = new ObjectMapper().readTree(Files.readString(configPath)).path("id2label");
            if (!id2label.isObject() || id2label.size() != EXPECTED.size()) {
                throw new ModelUnavailableException("config.json del modelo español sin id2label válido");
            }
            String[] labels = new String[EXPECTED.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = id2label.path(String.valueOf(i)).asText(null);
            }
            if (!Set.of(labels).equals(EXPECTED)) {
                throw new ModelUnavailableException("Etiquetas inesperadas en el modelo español");
            }
            return List.of(labels);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ModelUnavailableException m) {
                throw m;
            }
            throw new ModelUnavailableException("No se pudo leer la configuración del modelo español", e);
        }
    }
}
