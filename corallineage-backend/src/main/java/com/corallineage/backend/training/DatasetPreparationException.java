package com.corallineage.backend.training;

/** Raised when a version has too few points or classes to train */
public class DatasetPreparationException extends RuntimeException {

    public DatasetPreparationException(String message) {
        super(message);
    }
}
