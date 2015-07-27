/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.adapters.CalendarAdapter;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Calendar;

/**
 * Representation for syssvc aysnchronous task
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "syssvc_task")
public class SysSvcTask {

    private String opId;
    private NamedRelatedResourceRep resource;
    private String message;
    private String state;
    private String description;
    private Calendar startTime;
    private Calendar endTime;
    private ServiceErrorRestRep serviceError;

    public SysSvcTask() {}
    
    public SysSvcTask(String opId, NamedRelatedResourceRep resource,
            String message, String state, String description,
            Calendar startTime, Calendar endTime,
            ServiceErrorRestRep serviceError) {
        this.opId = opId;
        this.resource = resource;
        this.message = message;
        this.state = state;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.serviceError = serviceError;
    }

    @XmlElement(name = "op_id")
    public String getOpId() {
        return opId;
    }

    public void setOpId(String opId) {
        this.opId = opId;
    }

    @XmlElement (name = "resource")
    public NamedRelatedResourceRep getResource() {
        return resource;
    }

    public void setResource(NamedRelatedResourceRep resource) {
        this.resource = resource;
    }

    @XmlElement (name = "message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement (name = "state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @XmlElement (name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement (name = "start_time")
    @XmlJavaTypeAdapter(CalendarAdapter.class)
    public Calendar getStartTime() {
        return startTime;
    }

    public void setStartTime(Calendar startTime) {
        this.startTime = startTime;
    }

    @XmlElement (name = "end_time")
    @XmlJavaTypeAdapter(CalendarAdapter.class)
    public Calendar getEndTime() {
        return endTime;
    }

    public void setEndTime(Calendar endTime) {
        this.endTime = endTime;
    }

    @XmlElement(name = "service_error")
    public ServiceErrorRestRep getServiceError() {
        return serviceError;
    }

    public void setServiceError(ServiceErrorRestRep serviceError) {
        this.serviceError = serviceError;
    }
}
