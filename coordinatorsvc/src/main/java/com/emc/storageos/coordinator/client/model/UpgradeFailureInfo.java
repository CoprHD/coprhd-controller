/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

/**
 * UpgradeFailureInfo is used to store detail information in coordinator
 * when upgrade failure, which will be displayed in GUI for end user
 */
public class UpgradeFailureInfo implements CoordinatorSerializable {
    private static Logger log = LoggerFactory.getLogger(UpgradeFailureInfo.class);
    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();
    private static final String DEFAULT_SUGGESTION = "Please collect dbsvc/syssvc log from %s and contact EMC Support Engineer";

    private String version;
    private Date startTime;
    private String message;
    private List<String> callStack;
    private String suggestion;
    
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getCallStack() {
        return callStack;
    }

    public void setCallStack(List<String> callStack) {
        this.callStack = callStack;
    }

    public String getSuggestion() {
        return suggestion!=null? suggestion : String.format(DEFAULT_SUGGESTION, startTime.toGMTString());
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    @Override
    @JsonIgnore
    public String encodeAsString() {
        return toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    @JsonIgnore
    public UpgradeFailureInfo decodeFromString(String infoStr) throws FatalCoordinatorException {
        try {
            mapper.readerForUpdating(this).readValue(infoStr);
            return this;
        } catch (IOException e) {
            log.error("Failed to decode data string", e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }

    @Override
    @JsonIgnore
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        throw new UnsupportedOperationException("");
    }
    
    @Override
    @JsonIgnore
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize this object", e);
            throw CoordinatorException.fatals.failedToSerialize(e);
        }
    }
}
