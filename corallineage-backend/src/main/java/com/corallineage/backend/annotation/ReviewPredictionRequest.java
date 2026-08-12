package com.corallineage.backend.annotation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReviewPredictionRequest {

    @NotNull
    private Boolean accepted;

    private String correctedLabelCode;

    @NotBlank
    private String reviewedBy;

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }

    public String getCorrectedLabelCode() { return correctedLabelCode; }
    public void setCorrectedLabelCode(String correctedLabelCode) { this.correctedLabelCode = correctedLabelCode; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
}
