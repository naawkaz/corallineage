package com.corallineage.backend.versioning.diff;

public record DiffSummary(int imagesAdded, int imagesRemoved, long annotationsModified,
        int labelsAdded, int labelsDeprecated) {
}
