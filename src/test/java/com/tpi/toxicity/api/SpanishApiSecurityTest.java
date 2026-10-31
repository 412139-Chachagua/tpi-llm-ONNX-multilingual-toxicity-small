package com.tpi.toxicity.api;

import com.tpi.toxicity.model.SpanishToxicityProperties;
import com.tpi.toxicity.model.ToxicityProperties;
import com.tpi.toxicity.service.SpanishToxicityInferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El endpoint español queda protegido por la misma API key que el original. */
@WebMvcTest(SpanishToxicityController.class)
@EnableConfigurationProperties({ToxicityProperties.class, SpanishToxicityProperties.class})
@TestPropertySource(properties = "toxicity.api-key=s3cret-key")
class SpanishApiSecurityTest {

    private static final String URL = "/api/v1/toxicity/spanish/classify";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    SpanishToxicityInferenceService service;

    @Test
    void missingOrWrongKeyIsRejectedBeforeInference() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hola\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthorized"));
        mvc.perform(post(URL).header("X-API-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hola\"}"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).classify(anyString());
    }
}
