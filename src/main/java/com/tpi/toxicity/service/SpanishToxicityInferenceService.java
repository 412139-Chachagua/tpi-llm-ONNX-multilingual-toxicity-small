package com.tpi.toxicity.service;

import com.tpi.toxicity.model.InferenceException;
import com.tpi.toxicity.model.InvalidTextException;
import com.tpi.toxicity.model.ServiceSaturatedException;
import com.tpi.toxicity.model.SpanishOnnxModel;
import com.tpi.toxicity.model.SpanishToxicityProperties;
import com.tpi.toxicity.model.ToxicityProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Inferencia del modelo español; concurrencia propia para no competir con el otro modelo. */
@Service
public class SpanishToxicityInferenceService {

    /** Clase predicha y probabilidad softmax de cada clase. */
    public record Result(String label, Map<String, Double> scores) {
    }

    private final SpanishOnnxModel model;
    private final Semaphore permits;
    private final long acquireTimeoutMillis;

    public SpanishToxicityInferenceService(SpanishOnnxModel model, SpanishToxicityProperties props,
                                           ToxicityProperties general) {
        this.model = model;
        this.permits = new Semaphore(props.maxConcurrentInferences());
        this.acquireTimeoutMillis = general.inferenceTimeout().toMillis();
    }

    public Result classify(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidTextException("El texto no puede estar vacío");
        }
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ServiceSaturatedException();
            }
            return toResult(model.labels(), model.logits(text));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InferenceException("Inferencia interrumpida", e);
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    /** Softmax sobre los logits (clase única, excluyente); el orden de {@code labels} es el de la salida. */
    static Result toResult(List<String> labels, float[] logits) {
        if (logits.length != labels.size()) {
            throw new InferenceException("Dimensiones de salida inesperadas");
        }
        double max = Double.NEGATIVE_INFINITY;
        for (float l : logits) {
            if (!Float.isFinite(l)) {
                throw new InferenceException("El modelo devolvió un valor no finito");
            }
            max = Math.max(max, l);
        }
        double sum = 0;
        double[] exp = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = Math.exp(logits[i] - max);
            sum += exp[i];
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        int best = 0;
        for (int i = 0; i < logits.length; i++) {
            scores.put(labels.get(i), exp[i] / sum);
            if (exp[i] > exp[best]) {
                best = i;
            }
        }
        return new Result(labels.get(best), scores);
    }
}
