package com.tpi.toxicity.model;

/** Falló la inferencia o su resultado no es válido. */
public class InferenceException extends RuntimeException {
    public InferenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public InferenceException(String message) {
        super(message);
    }
}
