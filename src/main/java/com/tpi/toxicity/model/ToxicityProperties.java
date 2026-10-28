package com.tpi.toxicity.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuración del servicio. Se enlaza con el prefijo {@code toxicity} de application.yml;
 * cada valor puede sobrescribirse con variables de entorno.
 */
@ConfigurationProperties(prefix = "toxicity")
public record ToxicityProperties(
        Path modelPath,
        Path tokenizerPath,
        int maxTokens,
        int maxChars,
        int maxConcurrentInferences,
        Duration inferenceTimeout,
        String apiKey) {
}
