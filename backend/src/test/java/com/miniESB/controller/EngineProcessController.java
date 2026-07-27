/*package com.miniESB.controller;

import com.miniESB.service.EngineProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "true")
public class EngineProcessController {

    private final EngineProcessService engineProcessService;

    @PostMapping
    public ResponseEntity<?> process(@RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result =
                    engineProcessService.process(body.get("rawContent"));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Processing failed: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "mode", "engine"));
    }
}*/