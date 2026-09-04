package com.jorge.prreviewer.controller;

import com.jorge.prreviewer.dto.ReviewRequest;
import com.jorge.prreviewer.dto.ReviewResponse;
import com.jorge.prreviewer.exception.InvalidDiffException;
import com.jorge.prreviewer.service.ReviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResponse review(@RequestBody ReviewRequest request) {
        if (request.getDiff() == null || request.getDiff().isBlank()) {
            throw new InvalidDiffException("El diff no puede estar vacío");
        }

        return reviewService.review(request);
    }
}
