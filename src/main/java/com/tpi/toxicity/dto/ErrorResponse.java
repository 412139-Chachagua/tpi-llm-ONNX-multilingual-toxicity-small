package com.tpi.toxicity.dto;

/** Formato único de error; nunca incluye trazas, rutas ni el texto analizado. */
public record ErrorResponse(String error, String message) {
}
