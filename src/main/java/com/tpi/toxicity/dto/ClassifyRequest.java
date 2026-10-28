package com.tpi.toxicity.dto;

import jakarta.validation.constraints.NotBlank;

public record ClassifyRequest(@NotBlank(message = "text is required") String text) {
}
