package com.corallineage.backend.pointannotation;

import java.time.Instant;

/** One row of the unlabeled import queue */
public class UnlabeledImportQueueEntryResponse {

    private String jobType;
    private Long jobId;
    // Permanent ordinal position of the affected dataset version, null for a still uploading import
    private Integer versionNumber;
    private String versionTag;
    private String status;
    private Long seedLabeledPoints;
    private Long seedTotalPoints;
    private String modelTag;
    private Instant startedAt;
    private String resolveRoute;
    private Long resolveJobId;
    // Only set for a VERSION_START entry
    private Integer imageCount;

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getSeedLabeledPoints() { return seedLabeledPoints; }
    public void setSeedLabeledPoints(Long seedLabeledPoints) { this.seedLabeledPoints = seedLabeledPoints; }

    public Long getSeedTotalPoints() { return seedTotalPoints; }
    public void setSeedTotalPoints(Long seedTotalPoints) { this.seedTotalPoints = seedTotalPoints; }

    public String getModelTag() { return modelTag; }
    public void setModelTag(String modelTag) { this.modelTag = modelTag; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public String getResolveRoute() { return resolveRoute; }
    public void setResolveRoute(String resolveRoute) { this.resolveRoute = resolveRoute; }

    public Long getResolveJobId() { return resolveJobId; }
    public void setResolveJobId(Long resolveJobId) { this.resolveJobId = resolveJobId; }

    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }
}
