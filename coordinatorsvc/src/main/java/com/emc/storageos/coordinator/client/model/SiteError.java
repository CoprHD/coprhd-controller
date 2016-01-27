/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.Date;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * 
 * This class is used to present latest standby site error
 *
 */
public class SiteError implements CoordinatorSerializable{
    
    public static final String CONFIG_KIND = "siteError";
    public static final String CONFIG_ID = "global";
    
    private static final String ENCODING_SEPARATOR = "\0";
    
    private long creationTime = 0;
    private ServiceCode serviceCode;
    private String errorMessage;
    private String operation;
    
    public SiteError() {
    }
    
    public SiteError(InternalServerErrorException exception, String operation) {
        this.serviceCode = exception.getServiceCode();
        this.errorMessage = exception.getMessage();
        this.creationTime = (new Date()).getTime();
        this.operation = operation;

    }

    @Override
    public String encodeAsString() {
        StringBuilder builder = new StringBuilder();
        builder.append(creationTime);
        builder.append(ENCODING_SEPARATOR);
        if (serviceCode != null) {
            builder.append(serviceCode.toString());
            builder.append(ENCODING_SEPARATOR);
            builder.append(errorMessage);
            builder.append(operation);
        }
        return builder.toString();
    }

    @Override
    public SiteError decodeFromString(String infoStr) throws FatalCoordinatorException {
        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        SiteError siteError = new SiteError();
        
        siteError.creationTime = Long.parseLong(strings[0]);
        
        if (strings.length > 1) {
            siteError.serviceCode = ServiceCode.valueOf(strings[1]);
            siteError.errorMessage = strings[2];
            siteError.operation = strings[3];
        }
        return siteError;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public ServiceCode getServiceCode() {
        return serviceCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getOperation() {
        return operation;
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
        builder.append(", serviceCode=");
        builder.append(serviceCode);
        builder.append(", errorMessage=");
        builder.append(errorMessage);
        builder.append(", operation=");
        builder.append(operation);
        builder.append("]");
        return builder.toString();
    }
    
    public SiteErrorResponse toResponse() {
        SiteErrorResponse response = new SiteErrorResponse();
        response.setCreationTime(this.creationTime);
        response.setServiceCode(serviceCode.ordinal());
        response.setServiceCodeName(serviceCode.name());
        response.setErrorMessage(this.errorMessage);
        response.setOperation(this.operation);
        return response;
    }
    
    public void cleanup() {
        this.creationTime = 0;
        this.serviceCode = null;
        this.errorMessage = null;
        this.operation = null;
    }
}
