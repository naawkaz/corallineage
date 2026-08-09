package com.corallineage.backend.dto;

import java.time.Instant;

public class DatasetResponse {

    private Long id;
    private String datasetName;
    private String description;
    private String createdBy;
    private Instant createdAt;
    private long versionCount;
    private String latestVersionTag;
    private Integer imageCount;
    private Integer annotationCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public long getVersionCount() { return versionCount; }
    public void setVersionCount(long versionCount) { this.versionCount = versionCount; }

    public String getLatestVersionTag() { return latestVersionTag; }
    public void setLatestVersionTag(String latestVersionTag) { this.latestVersionTag = latestVersionTag; }

    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }

    public Integer getAnnotationCount() { return annotationCount; }
    public void setAnnotationCount(Integer annotationCount) { this.annotationCount = annotationCount; }
}
