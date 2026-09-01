package com.corallineage.backend.projects;

import java.util.List;

public class SourceAccessResponse {

    private boolean restricted;
    private List<Long> sourceIds;

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public List<Long> getSourceIds() {
        return sourceIds;
    }

    public void setSourceIds(List<Long> sourceIds) {
        this.sourceIds = sourceIds;
    }
}
