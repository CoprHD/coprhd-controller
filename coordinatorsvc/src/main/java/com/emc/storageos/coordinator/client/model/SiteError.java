package com.emc.storageos.coordinator.client.model;

import java.util.Date;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class SiteError implements CoordinatorSerializable{
    
    public static final String CONFIG_KIND = "siteError";
    public static final String CONFIG_ID = "global";
    
    private static final String ENCODING_SEPARATOR = ";";
    
    private long creationTime = 0;
    private String errorMessage = null;
    
    public SiteError() {
    }
    
    public SiteError(String errorMessage) {
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
        if (errorMessage!=null)
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

}
