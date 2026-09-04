package com.jorge.prreviewer.controller;

import com.jorge.prreviewer.dto.GitHubReviewRequest;
import com.jorge.prreviewer.dto.ReviewComment;
import com.jorge.prreviewer.dto.ReviewRequest;
import com.jorge.prreviewer.dto.ReviewResponse;
import com.jorge.prreviewer.exception.InvalidDiffException;
import com.jorge.prreviewer.github.GitHubClient;
import com.jorge.prreviewer.service.ReviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final GitHubClient gitHubClient;

    public ReviewController(ReviewService reviewService, GitHubClient gitHubClient) {
        this.reviewService = reviewService;
        this.gitHubClient = gitHubClient;
    }

    @PostMapping("/github")
    public ReviewResponse reviewGitHub(@RequestBody GitHubReviewRequest request) {
        List<Map<String, Object>> files = gitHubClient.getPullRequestFiles(
                request.owner(), request.repo(), request.prNumber());

        List<ReviewComment> comments = files.stream()
                .filter(file -> file.get("patch") != null
                        && !((String) file.get("patch")).isBlank())
                .flatMap(file -> reviewService.review(
                                new ReviewRequest((String) file.get("patch"), (String) file.get("filename")))
                        .getComments()
                        .stream())
                .toList();

        return new ReviewResponse(comments);
    }

    @PostMapping
    public ReviewResponse review(@RequestBody ReviewRequest request) {
        if (request.getDiff() == null || request.getDiff().isBlank()) {
            throw new InvalidDiffException("El diff no puede estar vacío");
        }

        return reviewService.review(request);
    }
}