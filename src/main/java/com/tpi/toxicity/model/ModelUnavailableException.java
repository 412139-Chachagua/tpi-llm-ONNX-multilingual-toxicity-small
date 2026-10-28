package com.tpi.toxicity.model;

/** El modelo o el tokenizer no existen, están corruptos o no tienen la interfaz esperada. */
public class ModelUnavailableException extends RuntimeException {
    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModelUnavailableException(String message) {
        super(message);
    }
}
