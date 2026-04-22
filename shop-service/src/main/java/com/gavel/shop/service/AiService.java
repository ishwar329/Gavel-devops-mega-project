package com.gavel.shop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final String provider;
    private final String model;
    private final String apiKey;
    private final String anthropicBaseUrl;
    private final String openaiBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiService(@Value("${ai.provider:anthropic}") String provider,
                     @Value("${ai.api-key:}") String apiKey,
                     @Value("${ai.model:}") String model) {
        this(provider, apiKey, model,
                "https://api.anthropic.com", "https://api.openai.com",
                new ObjectMapper(), HttpClient.newHttpClient());
    }

    AiService(String provider, String apiKey, String model,
              String anthropicBaseUrl, String openaiBaseUrl,
              ObjectMapper objectMapper, HttpClient httpClient) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : defaultModel(provider);
        this.anthropicBaseUrl = anthropicBaseUrl;
        this.openaiBaseUrl = openaiBaseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Optional<String> generateDescription(String title, String category, long retailValueCents) {
        String retailDisplay = String.format("$%.2f", retailValueCents / 100.0);
        String categoryHint = (category != null && !category.isBlank()) ? category : "general";

        String prompt = "You are a copywriter for a surplus food and item auction platform called Gavel. "
                + "Write a 2–3 sentence product description for an item being listed for auction. "
                + "Highlight freshness, value, and appeal to bargain-hunting buyers. "
                + "Be warm and concise — no bullet points, no headings.\n\n"
                + "Item title: " + title + "\n"
                + "Category: " + categoryHint + "\n"
                + "Retail value: " + retailDisplay + "\n\n"
                + "Description:";

        return complete(prompt, 200);
    }

    Optional<String> complete(String prompt, int maxTokens) {
        if (!isConfigured()) return Optional.empty();
        try {
            return "openai".equals(provider)
                    ? callOpenAi(prompt, maxTokens)
                    : callAnthropic(prompt, maxTokens);
        } catch (Exception e) {
            log.warn("AI completion failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> callAnthropic(String prompt, int maxTokens) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(anthropicBaseUrl + "/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Anthropic API returned {}: {}", response.statusCode(), response.body());
            return Optional.empty();
        }

        var root = objectMapper.readTree(response.body());
        var content = root.path("content");
        if (content.isArray() && !content.isEmpty()) {
            return Optional.of(content.get(0).path("text").asText().trim());
        }
        return Optional.empty();
    }

    private Optional<String> callOpenAi(String prompt, int maxTokens) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openaiBaseUrl + "/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("OpenAI API returned {}: {}", response.statusCode(), response.body());
            return Optional.empty();
        }

        var root = objectMapper.readTree(response.body());
        var choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return Optional.of(choices.get(0).path("message").path("content").asText().trim());
        }
        return Optional.empty();
    }

    private static String defaultModel(String provider) {
        return "openai".equals(provider) ? "gpt-4o-mini" : "claude-sonnet-4-20250514";
    }
}
