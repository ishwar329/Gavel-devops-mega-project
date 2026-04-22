package com.gavel.shop.controller;

import com.gavel.shop.service.AiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/describe")
    public ResponseEntity<?> describe(@RequestBody DescribeRequest request,
                                      Authentication auth) {
        if (!"seller".equals(getRole(auth))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "sellers only"));
        }

        if (!aiService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI service is not configured"));
        }

        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "title is required"));
        }

        Optional<String> description = aiService.generateDescription(
                request.title(),
                request.category(),
                request.retailValue() != null ? request.retailValue() : 0
        );

        return description
                .map(d -> ResponseEntity.ok(Map.of("description", d)))
                .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "failed to generate description")));
    }

    @SuppressWarnings("unchecked")
    private String getRole(Authentication auth) {
        Map<String, String> details = (Map<String, String>) auth.getDetails();
        return details.get("role");
    }

    public record DescribeRequest(String title, String category, Long retailValue) {}
}
