package com.corallineage.backend.datasets.importservice;

import com.corallineage.backend.versioning.dataset.LabelDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelCodeResolverTest {

    private final LabelCodeResolver resolver = new LabelCodeResolver();

    private LabelDefinition label(Long id, String code) {
        LabelDefinition label = new LabelDefinition();
        label.setId(id);
        label.setLabelCode(code);
        return label;
    }

    @Test
    void resolve_allCodesMatch_returnsMapping() {
        List<LabelDefinition> available = List.of(label(1L, "ACROPORA"), label(2L, "SAND"));

        Map<String, LabelDefinition> resolved = resolver.resolve(available, Set.of("ACROPORA", "SAND"));

        assertThat(resolved).hasSize(2);
        assertThat(resolved.get("ACROPORA").getId()).isEqualTo(1L);
        assertThat(resolved.get("SAND").getId()).isEqualTo(2L);
    }

    @Test
    void resolve_unknownCode_throwsAndListsUnresolved() {
        List<LabelDefinition> available = List.of(label(1L, "ACROPORA"));

        assertThatThrownBy(() -> resolver.resolve(available, Set.of("ACROPORA", "UNKNOWN")))
                .isInstanceOf(UnresolvableLabelCodesException.class)
                .satisfies(ex -> assertThat(((UnresolvableLabelCodesException) ex).getUnresolvedCodes())
                        .containsExactly("UNKNOWN"));
    }

    @Test
    void resolve_emptyAvailableLabels_allCodesUnresolved() {
        assertThatThrownBy(() -> resolver.resolve(List.of(), Set.of("ACROPORA")))
                .isInstanceOf(UnresolvableLabelCodesException.class);
    }
}
