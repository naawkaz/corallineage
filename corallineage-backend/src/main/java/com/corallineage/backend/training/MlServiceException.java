package com.corallineage.backend.training;

/** Raised when the Python ML service is unreachable or returns an error for /train or /architectures. */
public class MlServiceException extends RuntimeException {

    public MlServiceException(String message) {
        super(message);
    }

    public MlServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
