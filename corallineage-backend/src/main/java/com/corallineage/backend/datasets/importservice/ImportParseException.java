package com.corallineage.backend.datasets.importservice;

public class ImportParseException extends RuntimeException {

    public ImportParseException(String message) {
        super(message);
    }

    public ImportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
