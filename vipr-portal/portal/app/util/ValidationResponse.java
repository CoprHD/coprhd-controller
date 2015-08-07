/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.List;

import play.data.validation.Error;
import play.data.validation.Validation;

import com.google.common.collect.Lists;

/**
 * Simple validation response object for serializing over JSON.
 * 
 * @author jonnymiller
 */
public class ValidationResponse {
    public boolean success;
    public String key;
    public String message;

    public ValidationResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ValidationResponse(boolean success, String key, String message) {
        this.success = success;
        this.key = key;
        this.message = message;
    }

    public static ValidationResponse valid() {
        return valid(MessagesUtils.get("ValidationResponse.valid"));
    }

    public static ValidationResponse invalid() {
        return invalid(MessagesUtils.get("ValidationResponse.invalid"));
    }

    public static ValidationResponse valid(String message) {
        return new ValidationResponse(true, message);
    }

    public static ValidationResponse invalid(String message) {
        return new ValidationResponse(false, message);
    }

    public static ValidationResponse valid(String key, String message) {
        return new ValidationResponse(true, key, message);
    }

    public static ValidationResponse invalid(String key, String message) {
        return new ValidationResponse(false, key, message);
    }

    public static List<ValidationResponse> collectErrors() {
        List<ValidationResponse> responses = Lists.newArrayList();
        if (Validation.hasErrors()) {
            for (Error error : Validation.errors()) {
                responses.add(new ValidationResponse(false, error.getKey(), error.message()));
            }
        }
        return responses;
    }
}
