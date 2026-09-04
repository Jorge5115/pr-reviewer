package com.jorge.prreviewer.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final WebClient webClient;
    private final String apiKey;

    public GeminiClient(WebClient geminiWebClient,
                        @Value("${gemini.api.key}") String apiKey) {
        this.webClient = geminiWebClient;
        this.apiKey = apiKey;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String sendPrompt(String prompt) {
        log.info("Enviando prompt a Gemini:\n{}", prompt);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema()
                )
        );

        long[] backoffMillis = {1000, 2000, 4000};
        int attempt = 0;
        Map<String, Object> response = null;
        WebClientResponseException lastRetryable = null;

        while (attempt <= backoffMillis.length) {
            try {
                response = webClient.post()
                        .uri("/v1beta/models/gemini-3.5-flash-lite:generateContent?key={key}", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                break;
            } catch (WebClientResponseException ex) {
                if (ex.getStatusCode().value() != 429 && ex.getStatusCode().value() != 503) {
                    throw ex;
                }
                if (attempt == backoffMillis.length) {
                    throw ex;
                }
                long wait = backoffMillis[attempt];
                log.warn("Respuesta {} (intento {} de {}). Reintentando en {}ms...",
                        ex.getStatusCode().value(), attempt + 1, backoffMillis.length + 1, wait);
                lastRetryable = ex;
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastRetryable;
                }
                attempt++;
            }
        }

        String text = extractText(response);

        log.info("Respuesta cruda de Gemini:\n{}", text);

        return text;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        var candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        var content = (Map<String, Object>) candidates.get(0).get("content");
        var parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        return (String) parts.get(0).get("text");
    }

    private static Map<String, Object> responseSchema() {
        Map<String, Object> commentSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "file", Map.of("type", "STRING"),
                        "line", Map.of("type", "INTEGER", "nullable", true),
                        "severity", Map.of(
                                "type", "STRING",
                                "enum", List.of("INFO", "WARNING", "CRITICAL")
                        ),
                        "category", Map.of(
                                "type", "STRING",
                                "enum", List.of("BUG", "SECURITY", "STYLE", "PERFORMANCE", "BEST_PRACTICE")
                        ),
                        "message", Map.of("type", "STRING"),
                        "suggestion", Map.of("type", "STRING", "nullable", true)
                ),
                "required", List.of("file", "line", "severity", "category", "message", "suggestion")
        );

        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "comments", Map.of(
                                "type", "ARRAY",
                                "items", commentSchema
                        )
                ),
                "required", List.of("comments")
        );
    }
}
