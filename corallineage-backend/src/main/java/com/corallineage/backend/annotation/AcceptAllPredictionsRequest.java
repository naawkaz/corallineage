package com.corallineage.backend.annotation;

import jakarta.validation.constraints.NotBlank;

public class AcceptAllPredictionsRequest {

    @NotBlank(message = "reviewedBy is required")
    private String reviewedBy;

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
}
