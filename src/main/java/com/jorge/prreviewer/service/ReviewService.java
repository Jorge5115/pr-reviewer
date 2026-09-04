package com.jorge.prreviewer.service;

import com.jorge.prreviewer.dto.ReviewComment;
import com.jorge.prreviewer.dto.ReviewRequest;
import com.jorge.prreviewer.dto.ReviewResponse;
import com.jorge.prreviewer.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;

    public ReviewService(LlmClient llmClient, JsonMapper jsonMapper) {
        this.llmClient = llmClient;
        this.jsonMapper = jsonMapper;
    }

    public ReviewResponse review(ReviewRequest request) {
        String prompt = buildPrompt(request.getDiff(), request.getFileName());

        String raw = llmClient.sendPrompt(prompt);
        log.info("Respuesta cruda del LLM (intento 1):\n{}", raw);

        ReviewResponse parsed = tryParse(raw);
        if (parsed != null) {
            return parsed;
        }

        log.warn("Primer parseo fallido, reintentando...");
        raw = llmClient.sendPrompt(prompt);
        log.info("Respuesta cruda del LLM (intento 2):\n{}", raw);

        parsed = tryParse(raw);
        if (parsed != null) {
            return parsed;
        }

        log.warn("Segundo parseo fallido, devolviendo respuesta de fallback");
        return fallbackResponse();
    }

    private ReviewResponse tryParse(String raw) {
        try {
            return jsonMapper.readValue(cleanJson(raw), ReviewResponse.class);
        } catch (Exception e) {
            log.warn("Error al parsear respuesta del LLM: {}", e.getMessage());
            return null;
        }
    }

    private ReviewResponse fallbackResponse() {
        ReviewComment fallback = new ReviewComment(
                null,
                null,
                ReviewComment.Severity.WARNING,
                ReviewComment.Category.BEST_PRACTICE,
                "No se pudo analizar este diff automáticamente",
                null
        );
        return new ReviewResponse(List.of(fallback));
    }

    private String buildPrompt(String diff, String fileName) {
        return """
                You are a senior code reviewer. Analyze the following diff and identify all \
                bugs, security issues, performance problems, style issues and best-practice violations.

                For each issue found, add a comment. If the code is clean, return an empty \
                "comments" array.

                Notes:
                - "line" is the line number in the new file where the issue is, or null if it applies broadly
                - "suggestion" can be null if no fix is suggested

                File: %s

                Diff:
                %s
                """.formatted(fileName, diff);
    }

    private String cleanJson(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "");
        }
        return trimmed.strip();
    }
}
