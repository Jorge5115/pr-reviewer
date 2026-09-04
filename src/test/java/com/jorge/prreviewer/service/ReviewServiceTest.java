package com.jorge.prreviewer.service;

import com.jorge.prreviewer.dto.ReviewComment;
import com.jorge.prreviewer.dto.ReviewRequest;
import com.jorge.prreviewer.dto.ReviewResponse;
import com.jorge.prreviewer.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private LlmClient llmClient;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(llmClient, new JsonMapper());
    }

    private ReviewRequest dummyRequest() {
        return new ReviewRequest("- old\n+ new", "Test.java");
    }

    private String validJson() {
        return """
                {
                  "comments": [
                    {
                      "file": "Test.java",
                      "line": 5,
                      "severity": "WARNING",
                      "category": "BUG",
                      "message": "Potential null pointer",
                      "suggestion": "Add null check"
                    }
                  ]
                }""";
    }

    @Test
    void review_conJsonValido_devuelveReviewResponseParseado() {
        when(llmClient.sendPrompt(any())).thenReturn(validJson());

        ReviewResponse response = reviewService.review(dummyRequest());

        assertNotNull(response);
        assertEquals(1, response.getComments().size());

        ReviewComment comment = response.getComments().getFirst();
        assertEquals("Test.java", comment.getFile());
        assertEquals(5, comment.getLine());
        assertEquals(ReviewComment.Severity.WARNING, comment.getSeverity());
        assertEquals(ReviewComment.Category.BUG, comment.getCategory());
        assertEquals("Potential null pointer", comment.getMessage());
        assertEquals("Add null check", comment.getSuggestion());

        verify(llmClient, times(1)).sendPrompt(any());
    }

    @Test
    void review_conJsonInvalidoDosVeces_devuelveFallback() {
        when(llmClient.sendPrompt(any()))
                .thenReturn("esto no es json")
                .thenReturn("{ basura incompleta");

        ReviewResponse response = reviewService.review(dummyRequest());

        assertNotNull(response);
        assertEquals(1, response.getComments().size());

        ReviewComment fallback = response.getComments().getFirst();
        assertNull(fallback.getFile());
        assertNull(fallback.getLine());
        assertEquals(ReviewComment.Severity.WARNING, fallback.getSeverity());
        assertEquals(ReviewComment.Category.BEST_PRACTICE, fallback.getCategory());
        assertEquals("No se pudo analizar este diff automáticamente", fallback.getMessage());
        assertNull(fallback.getSuggestion());

        verify(llmClient, times(2)).sendPrompt(any());
    }

    @Test
    void review_primerIntentoFallido_segundoValido_devuelveParseado() {
        when(llmClient.sendPrompt(any()))
                .thenReturn("respuesta mala")
                .thenReturn(validJson());

        ReviewResponse response = reviewService.review(dummyRequest());

        assertNotNull(response);
        assertEquals(1, response.getComments().size());
        assertEquals("Potential null pointer", response.getComments().getFirst().getMessage());

        verify(llmClient, times(2)).sendPrompt(any());
    }
}
