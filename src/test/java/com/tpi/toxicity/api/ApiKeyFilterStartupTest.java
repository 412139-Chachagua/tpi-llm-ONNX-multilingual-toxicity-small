package com.tpi.toxicity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpi.toxicity.model.ToxicityProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyFilterStartupTest {

    private static ToxicityProperties props(String key, boolean disabled) {
        return new ToxicityProperties(null, null, 256, 100, 1, Duration.ofSeconds(1), key, disabled);
    }

    @Test
    void startupFailsWithoutKeyUnlessExplicitlyDisabled() {
        assertThatThrownBy(() -> new ApiKeyAndSizeFilter(props("", false), new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ApiKeyAndSizeFilter(props(null, false), new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new ApiKeyAndSizeFilter(props("", true), new ObjectMapper())).doesNotThrowAnyException();
        assertThatCode(() -> new ApiKeyAndSizeFilter(props("k", false), new ObjectMapper())).doesNotThrowAnyException();
    }
}
