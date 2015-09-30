/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package storageapi;

import java.net.ConnectException;
import java.util.concurrent.ExecutionException;

import play.mvc.Http;
import util.MessagesUtils;

import com.google.gson.JsonSyntaxException;

/**
 * @author Chris Dail
 */
public class APIResponse<T> {
    public Integer status;
    public Throwable cause;
    public T value;

    public APIResponse() {
    }

    public APIResponse(Integer status, T value) {
        this.status = status;
        this.value = value;
    }

    public APIResponse(Throwable cause) {
        while (cause instanceof ExecutionException) {
            cause = cause.getCause();
        }
        this.cause = cause;
    }

    public boolean isSuccessful() {
        if (cause != null || status == null) {
            return false;
        }
        return Http.StatusCode.success(status);
    }

    public boolean isError() {
        if (cause != null || status == null) {
            return true;
        }
        return Http.StatusCode.error(status);
    }

    public boolean isConnectionError() {
        return cause != null && cause instanceof ConnectException;
    }

    public String getErrorMessage() {
        if (status != null) {
            String messageKey = "storageapi.error.unknown";
            if (status == Http.StatusCode.NOT_FOUND) {
                messageKey = "storageapi.error.404";
            }
            else if (status == Http.StatusCode.BAD_REQUEST) {
                messageKey = "storageapi.error.400";
            }
            else if (status == Http.StatusCode.INTERNAL_ERROR) {
                messageKey = "storageapi.error.500";
            }
            return MessagesUtils.get(messageKey, status);
        }

        // Exceptions
        String messageKey = null;
        if (cause instanceof ConnectException) {
            messageKey = "storageapi.error.connect";
        }
        else if (cause instanceof InterruptedException) {
            messageKey = "storageapi.error.timeout";
        }
        else if (cause instanceof JsonSyntaxException) {
            messageKey = "storageapi.error.json";
        }

        if (messageKey != null) {
            return MessagesUtils.get(messageKey);
        }
        else if (cause != null) {
            return cause.getMessage();
        }
        else {
            return MessagesUtils.get("storageapi.error.exception");
        }
    }
}
