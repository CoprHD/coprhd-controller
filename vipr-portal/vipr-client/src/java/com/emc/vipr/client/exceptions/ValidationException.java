/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.exceptions;

import com.emc.vipr.model.catalog.ValidationError;

import java.util.List;

public class ValidationException extends ViPRHttpException {
    private List<ValidationError> validationErrors;

    public ValidationException(int httpCode, List<ValidationError> validationErrors) {
        super(httpCode, "Validation Error " + validationErrors.toString());
        this.validationErrors = validationErrors;
    }

    public ValidationException(int httpCode, String message, List<ValidationError> validationErrors) {
        super(httpCode, message);
        this.validationErrors = validationErrors;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }
}
