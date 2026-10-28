package com.tpi.toxicity;

import com.tpi.toxicity.model.ToxicityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ToxicityServiceApplicationTests {

    @Autowired
    ToxicityProperties properties;

    @Test
    void contextLoadsWithDefaultProperties() {
        assertThat(properties.maxChars()).isPositive();
        assertThat(properties.maxConcurrentInferences()).isPositive();
        assertThat(properties.modelPath()).isNotNull();
    }
}
