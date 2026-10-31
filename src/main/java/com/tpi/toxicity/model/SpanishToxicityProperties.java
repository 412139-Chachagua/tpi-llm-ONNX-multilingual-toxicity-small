package com.tpi.toxicity.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Configuración del modelo español ({@code bgonzalezbustamante/bert-spanish-toxicity}). Prefijo
 * {@code toxicity.spanish}; el límite de caracteres y el timeout de inferencia son los del contrato
 * general ({@link ToxicityProperties}). {@code intraOpThreads}: hilos de ONNX Runtime; 0 = CPUs que ve la JVM
 * (respeta el límite de vCPU del contenedor).
 */
@ConfigurationProperties(prefix = "toxicity.spanish")
public record SpanishToxicityProperties(
        Path modelPath,
        Path tokenizerPath,
        Path configPath,
        String revision,
        int maxTokens,
        int maxConcurrentInferences,
        int intraOpThreads) {
}
