package com.tpi.toxicity.api;

import com.tpi.toxicity.dto.ClassifyRequest;
import com.tpi.toxicity.dto.SpanishClassifyResponse;
import com.tpi.toxicity.model.SpanishToxicityProperties;
import com.tpi.toxicity.model.ToxicityProperties;
import com.tpi.toxicity.service.SpanishToxicityInferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/toxicity/spanish")
public class SpanishToxicityController {

    static final String MODEL_NAME = "bert-spanish-toxicity";

    private final SpanishToxicityInferenceService service;
    private final int maxChars;
    private final String revision;

    public SpanishToxicityController(SpanishToxicityInferenceService service, ToxicityProperties props,
                                     SpanishToxicityProperties spanish) {
        this.service = service;
        this.maxChars = props.maxChars();
        this.revision = spanish.revision();
    }

    @PostMapping("/classify")
    public SpanishClassifyResponse classify(@Valid @RequestBody ClassifyRequest request) {
        if (request.text().length() > maxChars) {
            throw new TextTooLongException(maxChars);
        }
        SpanishToxicityInferenceService.Result r = service.classify(request.text());
        return new SpanishClassifyResponse(MODEL_NAME, revision, r.label(), r.scores());
    }
}
