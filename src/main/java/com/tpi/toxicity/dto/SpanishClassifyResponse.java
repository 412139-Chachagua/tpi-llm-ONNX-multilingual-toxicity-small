package com.tpi.toxicity.dto;

import java.util.Map;

/**
 * {@code scores} son las probabilidades softmax de las dos clases (suman 1; no están calibradas) y
 * {@code label} es la clase de mayor probabilidad.
 */
public record SpanishClassifyResponse(String model, String revision, String label, Map<String, Double> scores) {
}
