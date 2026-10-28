package com.tpi.toxicity.model;

/** No hay capacidad de inferencia disponible dentro del timeout configurado. */
public class ServiceSaturatedException extends InferenceException {
    public ServiceSaturatedException() {
        super("Servicio saturado");
    }
}
