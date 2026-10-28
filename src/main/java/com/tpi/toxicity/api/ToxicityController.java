package com.tpi.toxicity.api;

import com.tpi.toxicity.dto.ClassifyRequest;
import com.tpi.toxicity.dto.ClassifyResponse;
import com.tpi.toxicity.model.ToxicityProperties;
import com.tpi.toxicity.service.ToxicityInferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/toxicity")
public class ToxicityController {

    static final String MODEL_NAME = "multilingual-toxicity-small";

    private final ToxicityInferenceService service;
    private final int maxChars;

    public ToxicityController(ToxicityInferenceService service, ToxicityProperties props) {
        this.service = service;
        this.maxChars = props.maxChars();
    }

    @PostMapping("/classify")
    public ClassifyResponse classify(@Valid @RequestBody ClassifyRequest request) {
        if (request.text().length() > maxChars) {
            throw new TextTooLongException(maxChars);
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        service.classify(request.text()).forEach((category, value) -> scores.put(category.label(), value));
        return new ClassifyResponse(MODEL_NAME, scores);
    }
}
