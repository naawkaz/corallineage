package com.corallineage.backend.versioning.diff;

public record ClassDistributionEntry(String labelCode, String labelName, long countInV1, long countInV2, long delta) {
}
