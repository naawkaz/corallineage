package com.corallineage.backend.versioning.diff;

import com.corallineage.backend.datasets.DatasetService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets/{datasetId}/diff")
public class DatasetDiffController {

    private final DatasetDiffService datasetDiffService;
    private final DatasetService datasetService;

    public DatasetDiffController(DatasetDiffService datasetDiffService, DatasetService datasetService) {
        this.datasetDiffService = datasetDiffService;
        this.datasetService = datasetService;
    }

    @GetMapping
    public DatasetDiffResponse getDiff(
            @PathVariable Long datasetId,
            @RequestParam Long v1,
            @RequestParam Long v2,
            Authentication authentication) {
        datasetService.requireAccess(datasetId, authentication.getName());
        return datasetDiffService.computeDiff(datasetId, v1, v2);
    }
}
