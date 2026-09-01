package com.corallineage.backend.projects;

import java.util.List;

public class SourceAccessRequest {

    private List<Long> sourceIds;

    public List<Long> getSourceIds() {
        return sourceIds;
    }

    public void setSourceIds(List<Long> sourceIds) {
        this.sourceIds = sourceIds;
    }
}
