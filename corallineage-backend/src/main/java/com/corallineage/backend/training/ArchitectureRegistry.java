package com.corallineage.backend.training;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caches the curated architecture list fetched from the Python ML service's GET /architectures.
 * Refreshed on startup; falls back to a hardcoded default if the ML service isn't reachable yet
 * (e.g. Python service not started), so the backend still boots and /architectures still degrades
 * gracefully rather than throwing on every request.
 */
@Component
public class ArchitectureRegistry {

    private static final List<ArchitectureInfo> FALLBACK = List.of(
            new ArchitectureInfo("efficientnet_b0", "EfficientNet-B0", "Lightweight, fast — good baseline for small datasets"),
            new ArchitectureInfo("resnet50", "ResNet-50", "Well-established baseline, strong general performance"),
            new ArchitectureInfo("vit_base_patch16_224", "ViT-Base/16", "Transformer-based — stronger on larger datasets")
    );

    private final PythonMlServiceClient pythonMlServiceClient;
    private final AtomicReference<List<ArchitectureInfo>> cached = new AtomicReference<>(FALLBACK);

    public ArchitectureRegistry(PythonMlServiceClient pythonMlServiceClient) {
        this.pythonMlServiceClient = pythonMlServiceClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refresh();
    }

    public void refresh() {
        try {
            List<ArchitectureInfo> fetched = pythonMlServiceClient.fetchArchitectures();
            if (fetched != null && !fetched.isEmpty()) {
                cached.set(fetched);
            }
        } catch (MlServiceException ex) {
            // Python ML service not reachable yet — keep the previous/fallback list.
        }
    }

    public List<ArchitectureInfo> getAll() {
        return cached.get();
    }

    public boolean isSupported(String architectureId) {
        Set<String> ids = cached.get().stream().map(ArchitectureInfo::id).collect(java.util.stream.Collectors.toSet());
        return ids.contains(architectureId);
    }
}
