package com.corallineage.backend.versioning.diff;

import java.util.List;

public record DatasetDiffResponse(VersionRef fromVersion, VersionRef toVersion, DiffSummary summary,
        List<LabelChangeEntry> labelChanges,
        List<ClassDistributionEntry> classDistributionDiff,
        List<ImageRef> imagesAdded, List<ImageRef> imagesRemoved) {
}
