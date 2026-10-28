package com.tpi.toxicity.model;

/** Categorías de salida, en el orden de {@code id2label} de config.json del modelo. */
public enum ToxicityCategory {
    TOXICITY("toxicity"),
    SEVERE_TOXICITY("severe_toxicity"),
    OBSCENE("obscene"),
    THREAT("threat"),
    INSULT("insult"),
    IDENTITY_ATTACK("identity_attack"),
    SEXUAL_EXPLICIT("sexual_explicit");

    private final String label;

    ToxicityCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
