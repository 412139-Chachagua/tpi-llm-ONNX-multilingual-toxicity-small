package com.tpi.toxicity.api;

import com.tpi.toxicity.model.OnnxToxicityModel;
import com.tpi.toxicity.model.ToxicityCategory;
import com.tpi.toxicity.model.ToxicityProperties;
import com.tpi.toxicity.service.ToxicityInferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ToxicityController.class, HealthController.class})
@EnableConfigurationProperties(ToxicityProperties.class)
@TestPropertySource(properties = {"toxicity.api-key=s3cret-key", "toxicity.max-chars=100"})
@ExtendWith(OutputCaptureExtension.class)
class ApiSecurityTest {

    private static final String URL = "/api/v1/toxicity/classify";
    private static final String BODY = "{\"text\":\"hola\"}";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    ToxicityInferenceService service;
    @MockitoBean
    OnnxToxicityModel model;

    @Test
    void missingOrWrongKeyIsRejectedBeforeInference() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthorized"));
        mvc.perform(post(URL).header("X-API-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("X-API-Key", "").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(service, never()).classify(anyString());
    }

    @Test
    void correctKeyIsAccepted() throws Exception {
        when(service.classify("hola")).thenReturn(new EnumMap<>(ToxicityCategory.class));
        mvc.perform(post(URL).header("X-API-Key", "s3cret-key").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointsDoNotRequireKey() throws Exception {
        mvc.perform(get("/health/live")).andExpect(status().isOk());
        when(model.isReady()).thenReturn(true);
        mvc.perform(get("/health/ready")).andExpect(status().isOk());
    }

    @Test
    void oversizedBodyIsRejectedByDeclaredLength() throws Exception {
        String big = "{\"text\":\"" + "a".repeat(5000) + "\"}";
        mvc.perform(post(URL).header("X-API-Key", "s3cret-key").contentType(MediaType.APPLICATION_JSON).content(big))
                .andExpect(status().isPayloadTooLarge());
        verify(service, never()).classify(anyString());
    }

    @Test
    void textAndKeyAreNeverLogged(CapturedOutput output) throws Exception {
        when(service.classify(anyString())).thenThrow(new RuntimeException("fallo"));
        mvc.perform(post(URL).header("X-API-Key", "s3cret-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"texto-sensible-xyz\"}")).andExpect(status().isInternalServerError());
        mvc.perform(post(URL).header("X-API-Key", "bad-key-attempt").contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isUnauthorized());
        assertThat(output.getAll()).doesNotContain("texto-sensible-xyz", "s3cret-key", "bad-key-attempt");
    }
}
