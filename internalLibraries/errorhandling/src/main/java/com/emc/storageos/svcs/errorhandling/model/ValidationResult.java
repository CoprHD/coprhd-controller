/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.svcs.errorhandling.model;

public final class ValidationResult {
    private boolean valid;
    private String errorMessage;

    public static final ValidationResult VALID_RESULT = new ValidationResult(true);

    public static ValidationResult getInvalidResult(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }

    private ValidationResult(boolean valid) {
        this.valid = true;
    }

    private ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }

    public boolean isValid() {
        return valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
