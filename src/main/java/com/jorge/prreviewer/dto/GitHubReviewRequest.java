package com.jorge.prreviewer.dto;

public record GitHubReviewRequest(String owner, String repo, int prNumber) {
}