package com.tpi.toxicity.dto;

import java.util.Map;

public record ClassifyResponse(String model, Map<String, Double> scores) {
}
