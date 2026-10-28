package com.tpi.toxicity.api;

import com.tpi.toxicity.model.InferenceException;
import com.tpi.toxicity.model.OnnxToxicityModel;
import com.tpi.toxicity.model.ServiceSaturatedException;
import com.tpi.toxicity.model.ToxicityCategory;
import com.tpi.toxicity.model.ToxicityProperties;
import com.tpi.toxicity.service.ToxicityInferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP; el servicio de inferencia está simulado (la inferencia real se prueba aparte). */
@WebMvcTest({ToxicityController.class, HealthController.class})
@EnableConfigurationProperties(ToxicityProperties.class)
class ToxicityApiTest {

    private static final String URL = "/api/v1/toxicity/classify";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    ToxicityInferenceService service;
    @MockitoBean
    OnnxToxicityModel model;

    @Test
    void validRequestReturnsSevenNamedScores() throws Exception {
        Map<ToxicityCategory, Double> scores = new EnumMap<>(ToxicityCategory.class);
        for (ToxicityCategory c : ToxicityCategory.values()) {
            scores.put(c, 0.25);
        }
        when(service.classify("hola")).thenReturn(scores);
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hola\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("multilingual-toxicity-small"))
                .andExpect(jsonPath("$.scores.length()").value(7))
                .andExpect(jsonPath("$.scores.toxicity").value(0.25))
                .andExpect(jsonPath("$.scores.severe_toxicity").exists())
                .andExpect(jsonPath("$.scores.obscene").exists())
                .andExpect(jsonPath("$.scores.threat").exists())
                .andExpect(jsonPath("$.scores.insult").exists())
                .andExpect(jsonPath("$.scores.identity_attack").exists())
                .andExpect(jsonPath("$.scores.sexual_explicit").exists());
    }

    @Test
    void invalidBodiesReturn400() throws Exception {
        for (String body : new String[]{"{}", "{\"text\":\"\"}", "{\"text\":\"   \"}", "{\"text\":null}",
                "{\"text\":5", "not json", ""}) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"));
        }
    }

    @Test
    void textOverCharLimitReturns413() throws Exception {
        String text = "a".repeat(5001);
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("text_too_long"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(text))));
    }

    @Test
    void wrongContentTypeAndMethod() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(URL)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void saturationReturns503() throws Exception {
        when(service.classify(anyString())).thenThrow(new ServiceSaturatedException());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_busy"));
    }

    @Test
    void internalErrorsDoNotLeakDetails() throws Exception {
        when(service.classify(anyString())).thenThrow(new InferenceException("/secret/path/model.onnx exploded"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("inference_failed"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        org.mockito.Mockito.doThrow(new IllegalStateException("/Users/x/stack")).when(service).classify(anyString());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Users"))));
    }

    @Test
    void healthEndpoints() throws Exception {
        mvc.perform(get("/health/live")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        when(model.isReady()).thenReturn(true);
        mvc.perform(get("/health/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        when(model.isReady()).thenReturn(false);
        mvc.perform(get("/health/ready")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
