package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.versioning.dataset.LabelDefinition;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Resolves raw label code strings from an import source to the {@link LabelDefinition}
 * rows active for a dataset version. Aborts (rather than skipping) if any requested
 * code has no match, per checklist v2 Feature 5.
 */
@Component
public class LabelCodeResolver {

    public Map<String, LabelDefinition> resolve(List<LabelDefinition> availableLabels, Set<String> requestedCodes) {
        Map<String, LabelDefinition> byCode = availableLabels.stream()
                .collect(Collectors.toMap(LabelDefinition::getLabelCode, label -> label, (a, b) -> a));

        Set<String> unresolved = new TreeSet<>();
        for (String code : requestedCodes) {
            if (!byCode.containsKey(code)) {
                unresolved.add(code);
            }
        }
        if (!unresolved.isEmpty()) {
            throw new UnresolvableLabelCodesException(unresolved);
        }

        Map<String, LabelDefinition> resolved = new HashMap<>();
        for (String code : requestedCodes) {
            resolved.put(code, byCode.get(code));
        }
        return resolved;
    }
}
