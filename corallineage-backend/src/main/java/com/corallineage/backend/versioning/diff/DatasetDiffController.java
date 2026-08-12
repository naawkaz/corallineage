package com.corallineage.backend.versioning.diff;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets/{datasetId}/diff")
public class DatasetDiffController {

    private final DatasetDiffService datasetDiffService;

    public DatasetDiffController(DatasetDiffService datasetDiffService) {
        this.datasetDiffService = datasetDiffService;
    }

    @GetMapping
    public DatasetDiffResponse getDiff(
            @PathVariable Long datasetId,
            @RequestParam Long v1,
            @RequestParam Long v2) {
        return datasetDiffService.computeDiff(datasetId, v1, v2);
    }
}
