package com.corallineage.backend.versioning.dataset;

import java.time.Instant;
import java.time.LocalDate;

public class ImageResponse {

    private Long id;
    private String filename;
    private String url;
    private String dvcFileHash;
    private String site;
    private LocalDate surveyDate;
    private Instant addedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDvcFileHash() { return dvcFileHash; }
    public void setDvcFileHash(String dvcFileHash) { this.dvcFileHash = dvcFileHash; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public LocalDate getSurveyDate() { return surveyDate; }
    public void setSurveyDate(LocalDate surveyDate) { this.surveyDate = surveyDate; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
