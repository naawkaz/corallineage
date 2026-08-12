package com.corallineage.backend.training;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureRegistryTest {

    @Mock
    private PythonMlServiceClient pythonMlServiceClient;

    @Test
    void beforeRefresh_fallsBackToDefaultCuratedList() {
        ArchitectureRegistry registry = new ArchitectureRegistry(pythonMlServiceClient);

        assertThat(registry.getAll()).extracting(ArchitectureInfo::id)
                .containsExactly("efficientnet_b0", "resnet50", "vit_base_patch16_224");
        assertThat(registry.isSupported("efficientnet_b0")).isTrue();
        assertThat(registry.isSupported("bogus")).isFalse();
    }

    @Test
    void refresh_success_replacesCacheWithFetchedList() {
        ArchitectureRegistry registry = new ArchitectureRegistry(pythonMlServiceClient);
        when(pythonMlServiceClient.fetchArchitectures()).thenReturn(
                List.of(new ArchitectureInfo("custom_arch", "Custom", "test-only architecture")));

        registry.refresh();

        assertThat(registry.getAll()).extracting(ArchitectureInfo::id).containsExactly("custom_arch");
        assertThat(registry.isSupported("custom_arch")).isTrue();
        assertThat(registry.isSupported("efficientnet_b0")).isFalse();
    }

    @Test
    void refresh_mlServiceUnreachable_keepsPreviousList() {
        ArchitectureRegistry registry = new ArchitectureRegistry(pythonMlServiceClient);
        when(pythonMlServiceClient.fetchArchitectures()).thenThrow(new MlServiceException("unreachable"));

        registry.refresh();

        assertThat(registry.getAll()).extracting(ArchitectureInfo::id)
                .containsExactly("efficientnet_b0", "resnet50", "vit_base_patch16_224");
    }
}
