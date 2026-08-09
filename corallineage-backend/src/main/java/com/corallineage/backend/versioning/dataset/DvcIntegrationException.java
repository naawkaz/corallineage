package com.corallineage.backend.versioning.dataset;

public class DvcIntegrationException extends RuntimeException {

    public DvcIntegrationException(String message) {
        super(message);
    }

    public DvcIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
