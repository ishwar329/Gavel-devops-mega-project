package com.gavel.shop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiService anthropicService(String apiKey) {
        return new AiService("anthropic", apiKey, "claude-test",
                "https://api.anthropic.com", "https://api.openai.com",
                objectMapper, httpClient);
    }

    private AiService openaiService(String apiKey) {
        return new AiService("openai", apiKey, "gpt-test",
                "https://api.anthropic.com", "https://api.openai.com",
                objectMapper, httpClient);
    }

    @Test
    void notConfigured_whenApiKeyBlank() {
        AiService service = anthropicService("");
        assertFalse(service.isConfigured());
    }

    @Test
    void notConfigured_whenApiKeyNull() {
        AiService service = anthropicService(null);
        assertFalse(service.isConfigured());
    }

    @Test
    void configured_whenApiKeyPresent() {
        AiService service = anthropicService("sk-test");
        assertTrue(service.isConfigured());
    }

    @Test
    void generateDescription_notConfigured_returnsEmpty() {
        AiService service = anthropicService("");
        Optional<String> result = service.generateDescription("Pastry Box", "Bakery", 2800L);
        assertTrue(result.isEmpty());
    }

    @Test
    void generateDescription_withNullCategory_usesGeneral() {
        AiService service = anthropicService("");
        Optional<String> result = service.generateDescription("Bread", null, 500L);
        assertTrue(result.isEmpty());
    }

    @Test
    void generateDescription_withBlankCategory_usesGeneral() {
        AiService service = anthropicService("");
        Optional<String> result = service.generateDescription("Bread", "  ", 500L);
        assertTrue(result.isEmpty());
    }

    @Test
    void defaultModel_anthropic() {
        AiService service = new AiService("anthropic", "key", "",
                "https://api.anthropic.com", "https://api.openai.com",
                objectMapper, httpClient);
        assertTrue(service.isConfigured());
    }

    @Test
    void defaultModel_openai() {
        AiService service = new AiService("openai", "key", null,
                "https://api.anthropic.com", "https://api.openai.com",
                objectMapper, httpClient);
        assertTrue(service.isConfigured());
    }

    // ---- Anthropic provider tests ----

    @Test
    @SuppressWarnings("unchecked")
    void anthropic_success_returnsDescription() throws Exception {
        AiService service = anthropicService("sk-test");
        String responseBody = """
                {"content":[{"type":"text","text":"A delightful assortment of pastries."}]}
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Optional<String> result = service.generateDescription("Pastry Box", "Bakery", 2800L);

        assertTrue(result.isPresent());
        assertEquals("A delightful assortment of pastries.", result.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anthropic_nonOkStatus_returnsEmpty() throws Exception {
        AiService service = anthropicService("sk-test");
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.body()).thenReturn("rate limited");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Optional<String> result = service.generateDescription("Pastry Box", "Bakery", 2800L);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anthropic_emptyContentArray_returnsEmpty() throws Exception {
        AiService service = anthropicService("sk-test");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"content\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Optional<String> result = service.generateDescription("Pastry Box", "Bakery", 2800L);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anthropic_httpException_returnsEmpty() throws Exception {
        AiService service = anthropicService("sk-test");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Optional<String> result = service.generateDescription("Pastry Box", "Bakery", 2800L);

        assertTrue(result.isEmpty());
    }

    // ---- OpenAI provider tests ----

    @Test
    @SuppressWarnings("unchecked")
    void openai_success_returnsDescription() throws Exception {
        AiService service = openaiService("sk-test");
        String responseBody = """
                {"choices":[{"message":{"content":"Fresh baked goods at a steal."}}]}
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Optional<String> result = service.generateDescription("Bread Loaf", "Bakery", 1200L);

        assertTrue(result.isPresent());
        assertEquals("Fresh baked goods at a steal.", result.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void openai_nonOkStatus_returnsEmpty() throws Exception {
        AiService service = openaiService("sk-test");
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("internal error");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Optional<String> result = service.generateDescription("Bread Loaf", "Bakery", 1200L);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void openai_emptyChoicesArray_returnsEmpty() throws Exception {
        AiService service = openaiService("sk-test");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"choices\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Optional<String> result = service.generateDescription("Bread Loaf", "Bakery", 1200L);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void openai_httpException_returnsEmpty() throws Exception {
        AiService service = openaiService("sk-test");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("timeout"));

        Optional<String> result = service.generateDescription("Bread Loaf", "Bakery", 1200L);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anthropic_trimsWhitespace() throws Exception {
        AiService service = anthropicService("sk-test");
        String responseBody = """
                {"content":[{"type":"text","text":"  Trimmed text.  "}]}
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        Optional<String> result = service.complete("test prompt", 100);

        assertEquals("Trimmed text.", result.get());
    }
}
