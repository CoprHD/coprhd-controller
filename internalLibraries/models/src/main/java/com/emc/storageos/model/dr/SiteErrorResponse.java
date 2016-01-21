/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "site_error")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteErrorResponse {
    private static SiteErrorResponse noError = new SiteErrorResponse();

    private long creationTime;
    private int serviceCode;
    private String serviceCodeName;
    private String errorMessage;

    @XmlElement(name = "creationTime")
    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }
    
    @XmlElement(name = "serviceCode")
    public int getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(int serviceCode) {
        this.serviceCode = serviceCode;
    }
    
    @XmlElement(name = "serviceCodeName")
    public String getServiceCodeName() {
        return serviceCodeName;
    }

    public void setServiceCodeName(String serviceCodeName) {
        this.serviceCodeName = serviceCodeName;
    }

    @XmlElement(name = "errorMessage")
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteErrorResponse [creationTime=");
        builder.append(creationTime);
        builder.append(", serviceCode=");
        builder.append(serviceCode);
        builder.append(", serviceCodeName=");
        builder.append(serviceCodeName);
        builder.append(", errorMessage=");
        builder.append(errorMessage);
        builder.append("]");
        return builder.toString();
    }

    public static SiteErrorResponse noError() {
        return noError;
    }
    
    public static boolean isErrorResponse(SiteErrorResponse response) {
        if (response == null)
            return false;
        
        if (response.getServiceCode() > 0 && response.getErrorMessage() != null && response.getErrorMessage().length() > 0) {
            return true;
        }
        
        return false;
    }
}
