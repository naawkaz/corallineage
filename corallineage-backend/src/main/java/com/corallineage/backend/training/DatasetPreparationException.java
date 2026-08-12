package com.corallineage.backend.training;

/** Raised when a dataset version has too few annotation points/classes to prepare a training set. */
public class DatasetPreparationException extends RuntimeException {

    public DatasetPreparationException(String message) {
        super(message);
    }
}
