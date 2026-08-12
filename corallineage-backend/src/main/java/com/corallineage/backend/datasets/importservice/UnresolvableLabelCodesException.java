package com.corallineage.backend.datasets.importservice;

import java.util.Set;

public class UnresolvableLabelCodesException extends RuntimeException {

    private final Set<String> unresolvedCodes;

    public UnresolvableLabelCodesException(Set<String> unresolvedCodes) {
        super("Unresolvable label code(s): " + String.join(", ", unresolvedCodes)
                + ". Import aborted — define these labels on the dataset before importing.");
        this.unresolvedCodes = unresolvedCodes;
    }

    public Set<String> getUnresolvedCodes() {
        return unresolvedCodes;
    }
}
