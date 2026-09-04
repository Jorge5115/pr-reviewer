package com.jorge.prreviewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewComment {

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    public enum Category {
        BUG, SECURITY, STYLE, PERFORMANCE, BEST_PRACTICE
    }

    private String file;
    private Integer line;
    private Severity severity;
    private Category category;
    private String message;
    private String suggestion;
}
