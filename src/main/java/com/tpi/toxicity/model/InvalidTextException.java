package com.tpi.toxicity.model;

/** El texto recibido no es válido para clasificar (error del cliente, no un fallo interno). */
public class InvalidTextException extends RuntimeException {
    public InvalidTextException(String message) {
        super(message);
    }
}
