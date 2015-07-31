/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package storageapi;

import org.apache.commons.lang.StringUtils;

public class APIException extends RuntimeException {

    private final int statusCode;
    private final String statusText;
    private final String errorMessage;

    public APIException(int statusCode, String statusText, String errorMessage) {
        super(constructMessage(statusCode, statusText, errorMessage));
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.errorMessage = errorMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static String constructMessage(int statusCode, String statusText, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append(statusCode);
        if (StringUtils.isNotBlank(statusText)) {
            sb.append(" : ").append(statusText);
        }
        if (StringUtils.isNotBlank(errorMessage)) {
            sb.append(" : ").append(errorMessage);
        }
        return sb.toString();
    }
}
