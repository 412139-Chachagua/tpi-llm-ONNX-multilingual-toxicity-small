package com.tpi.toxicity;

import com.tpi.toxicity.model.ToxicityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.tpi.toxicity.model.OnnxToxicityModel;
import com.tpi.toxicity.model.SpanishOnnxModel;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "toxicity.auth-disabled=true")
class ToxicityServiceApplicationTests {

    @MockitoBean
    OnnxToxicityModel model;
    @MockitoBean
    SpanishOnnxModel spanishModel;

    @Autowired
    ToxicityProperties properties;

    @Test
    void contextLoadsWithDefaultProperties() {
        assertThat(properties.maxChars()).isPositive();
        assertThat(properties.maxConcurrentInferences()).isPositive();
        assertThat(properties.modelPath()).isNotNull();
    }
}
