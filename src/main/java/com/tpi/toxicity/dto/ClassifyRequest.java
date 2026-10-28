package com.tpi.toxicity.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import jakarta.validation.constraints.NotBlank;

import java.io.IOException;

public record ClassifyRequest(
        @NotBlank(message = "text is required")
        @JsonDeserialize(using = StrictStringDeserializer.class)
        String text) {

    /** Solo acepta un string JSON; números, booleanos, arreglos y objetos se rechazan (400) en vez de convertirse a texto. */
    static final class StrictStringDeserializer extends StdDeserializer<String> {
        StrictStringDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                return p.getText();
            }
            return (String) ctxt.handleUnexpectedToken(String.class, p);
        }
    }
}
