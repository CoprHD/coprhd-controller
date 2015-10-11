/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.Date;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.storageos.model.dr.SiteErrorResponse;

/**
 * 
 * This class is used to present latest standby site error
 *
 */
public class SiteError implements CoordinatorSerializable{
    
    public static final String CONFIG_KIND = "siteError";
    public static final String CONFIG_ID = "global";
    
    public static final String ERROR_DESCRIPTION_ADD = "Error occurs during adding new standby site";
    public static final String ERROR_DESCRIPTION_REMOVE = "Error occurs during removing standby site";
    
    private static final String ENCODING_SEPARATOR = ";";
    
    private long creationTime = 0;
    private String errorDescription;
    private String errorMessage;
    
    public SiteError() {
    }
    
    public SiteError(String errorDescription, String errorMessage) {
        this.errorDescription = errorDescription;
        this.errorMessage = errorMessage;
        this.creationTime = (new Date()).getTime();
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String encodeAsString() {
        StringBuilder builder = new StringBuilder();
        builder.append(creationTime);
        builder.append(ENCODING_SEPARATOR);
        if (errorDescription != null) 
            builder.append(errorMessage);
        return builder.toString();
    }

    @Override
    public SiteError decodeFromString(String infoStr) throws FatalCoordinatorException {
        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        SiteError siteError = new SiteError();
        
        siteError.setCreationTime(Long.parseLong(strings[0]));
        
        if (strings.length == 2)
            siteError.setErrorMessage(strings[1]);
        
        return siteError;
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteError");
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteError [creationTime=");
        builder.append(creationTime);
        builder.append(", errorDescription=");
        builder.append(errorDescription);
        builder.append(", errorMessage=");
        builder.append(errorMessage);
        builder.append("]");
        return builder.toString();
    }

    public void cleanup() {
        this.creationTime = 0;
        this.errorDescription = null;
        this.errorMessage = null;
    }
    
    public SiteErrorResponse toResponse() {
        SiteErrorResponse response = new SiteErrorResponse();
        response.setCreationTime(this.creationTime);
        response.setErrorDescription(this.errorDescription);
        response.setErrorMessage(this.errorMessage);
        return response;
    }
}
