package com.tpi.toxicity.service;

import com.tpi.toxicity.model.InferenceException;
import com.tpi.toxicity.model.OnnxToxicityModel;
import com.tpi.toxicity.model.ServiceSaturatedException;
import com.tpi.toxicity.model.ToxicityCategory;
import com.tpi.toxicity.model.ToxicityProperties;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class ToxicityInferenceService {

    private final OnnxToxicityModel model;
    private final Semaphore permits;
    private final long acquireTimeoutMillis;

    public ToxicityInferenceService(OnnxToxicityModel model, ToxicityProperties props) {
        this.model = model;
        this.permits = new Semaphore(props.maxConcurrentInferences());
        this.acquireTimeoutMillis = props.inferenceTimeout().toMillis();
    }

    /** Probabilidad (sigmoide independiente por etiqueta) para cada categoría. */
    public Map<ToxicityCategory, Double> classify(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto no puede estar vacío");
        }
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ServiceSaturatedException();
            }
            return toProbabilities(model.logits(text));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InferenceException("Inferencia interrumpida", e);
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    static Map<ToxicityCategory, Double> toProbabilities(float[] logits) {
        ToxicityCategory[] cats = ToxicityCategory.values();
        if (logits.length != cats.length) {
            throw new InferenceException("Dimensiones de salida inesperadas");
        }
        Map<ToxicityCategory, Double> scores = new EnumMap<>(ToxicityCategory.class);
        for (int i = 0; i < cats.length; i++) {
            if (!Float.isFinite(logits[i])) {
                throw new InferenceException("El modelo devolvió un valor no finito");
            }
            double p = 1.0 / (1.0 + Math.exp(-logits[i]));
            if (!(p >= 0.0 && p <= 1.0)) {
                throw new InferenceException("Probabilidad fuera de rango");
            }
            scores.put(cats[i], p);
        }
        return scores;
    }
}
