package com.tpi.toxicity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.file.Files;
import java.nio.file.Path;

/** Arranque completo de la aplicación con el modelo real y petición HTTP real. */
@DisabledOnOs(value = OS.MAC, architectures = "x86_64")
@EnabledIf("artifactsPresent")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ToxicityApplicationRealTest {

    @LocalServerPort
    int port;

    static boolean artifactsPresent() {
        return Files.isRegularFile(Path.of("models/onnx/model.onnx"))
                && Files.isRegularFile(Path.of("models/tokenizer.json"));
    }

    @Test
    void realRequestReturnsSevenValidScores() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .post().uri("/api/v1/toxicity/classify").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"You are a complete idiot\"}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.scores.length()").isEqualTo(7)
                .jsonPath("$.scores.toxicity").isNumber();
    }
}
