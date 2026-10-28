package com.tpi.toxicity.api;

import com.tpi.toxicity.dto.HealthResponse;
import com.tpi.toxicity.model.OnnxToxicityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final OnnxToxicityModel model;

    public HealthController(OnnxToxicityModel model) {
        this.model = model;
    }

    @GetMapping("/live")
    public HealthResponse live() {
        return new HealthResponse("UP");
    }

    @GetMapping("/ready")
    public ResponseEntity<HealthResponse> ready() {
        if (model.isReady()) {
            return ResponseEntity.ok(new HealthResponse("UP"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new HealthResponse("DOWN"));
    }
}
