package com.tpi.toxicity.api;

import com.tpi.toxicity.model.InferenceException;
import com.tpi.toxicity.model.ServiceSaturatedException;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del endpoint español; el servicio de inferencia está simulado. */
@WebMvcTest(SpanishToxicityController.class)
@TestPropertySource(properties = {"toxicity.auth-disabled=true",
        "toxicity.spanish.revision=rev123"})
@EnableConfigurationProperties({ToxicityProperties.class, SpanishToxicityProperties.class})
class SpanishToxicityApiTest {

    private static final String URL = "/api/v1/toxicity/spanish/classify";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    SpanishToxicityInferenceService service;

    private static SpanishToxicityInferenceService.Result result(String label, double nontoxic, double toxic) {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("NONTOXIC", nontoxic);
        scores.put("TOXIC", toxic);
        return new SpanishToxicityInferenceService.Result(label, scores);
    }

    @Test
    void toxicSpanishTextReturnsModelLabelAndBothScores() throws Exception {
        when(service.classify("Sos un inútil, no servís para nada")).thenReturn(result("TOXIC", 0.1, 0.9));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Sos un inútil, no servís para nada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("bert-spanish-toxicity"))
                .andExpect(jsonPath("$.revision").value("rev123"))
                .andExpect(jsonPath("$.label").value("TOXIC"))
                .andExpect(jsonPath("$.scores.length()").value(2))
                .andExpect(jsonPath("$.scores.TOXIC").value(0.9))
                .andExpect(jsonPath("$.scores.NONTOXIC").value(0.1));
    }

    @Test
    void nonToxicSpanishTextReturnsNonToxic() throws Exception {
        when(service.classify("Gracias por tu ayuda")).thenReturn(result("NONTOXIC", 0.8, 0.2));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Gracias por tu ayuda\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("NONTOXIC"));
    }

    @Test
    void emptyNullAndMalformedBodiesReturn400() throws Exception {
        for (String body : new String[]{"{}", "{\"text\":\"\"}", "{\"text\":\"   \"}", "{\"text\":null}",
                "{\"text\":5", "not json", "", "{\"text\":123}", "{\"text\":[\"a\"]}"}) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"));
        }
    }

    @Test
    void textOverCharLimitReturns413() throws Exception {
        String text = "á".repeat(5001);
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("text_too_long"))
                .andExpect(content().string(not(containsString(text))));
    }

    @Test
    void inferenceErrorReturns500WithoutDetails() throws Exception {
        when(service.classify(anyString())).thenThrow(new InferenceException("/secret/path/model.onnx exploded"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("inference_failed"))
                .andExpect(content().string(not(containsString("secret"))));
        doThrow(new IllegalStateException("/Users/x/stack")).when(service).classify(anyString());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("Users"))));
    }

    @Test
    void saturationReturns503() throws Exception {
        when(service.classify(anyString())).thenThrow(new ServiceSaturatedException());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_busy"));
    }

    @Test
    void wrongContentTypeAndMethod() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("hola")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(URL)).andExpect(status().isMethodNotAllowed());
    }
}
