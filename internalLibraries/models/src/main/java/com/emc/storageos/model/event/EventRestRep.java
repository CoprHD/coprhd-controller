/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.event;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.adapters.CalendarAdapter;

/**
 * REST Response representing an Event.
 */
@XmlRootElement(name = "event")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EventRestRep extends DataObjectRestRep {

    private String description;
    private String warning;
    private NamedRelatedResourceRep resource;
    private String eventStatus;
    private String eventCode;
    private List<RelatedResourceRep> taskIds;
    private List<String> approveDetails;
    private List<String> declineDetails;
    private Calendar eventExecutionTime;

    private RelatedResourceRep tenant;

    public EventRestRep() {
    }

    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "warning")
    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    @XmlElement(name = "resource")
    public NamedRelatedResourceRep getResource() {
        return resource;
    }

    public void setResource(NamedRelatedResourceRep resource) {
        this.resource = resource;
    }

    @XmlElement(name = "event_status")
    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String status) {
        this.eventStatus = status;
    }

    @XmlElement(name = "event_code")
    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    @XmlElementWrapper(name = "task_ids")
    @XmlElement(name = "task_id")
    public List<RelatedResourceRep> getTaskIds() {
        if (taskIds == null) {
            taskIds = new ArrayList<RelatedResourceRep>();
        }
        return taskIds;
    }

    public void setTaskIds(List<RelatedResourceRep> taskIds) {
        this.taskIds = taskIds;
    }

    @XmlElementWrapper(name = "approve_details")
    @XmlElement(name = "approve_detail")
    public List<String> getApproveDetails() {
        return approveDetails;
    }

    public void setApproveDetails(List<String> approveDetails) {
        this.approveDetails = approveDetails;
    }

    @XmlElementWrapper(name = "decline_details")
    @XmlElement(name = "decline_detail")
    public List<String> getDeclineDetails() {
        return declineDetails;
    }

    public void setDeclineDetails(List<String> declineDetails) {
        this.declineDetails = declineDetails;
    }

    @XmlElement(name = "event_execution_time")
    @XmlJavaTypeAdapter(CalendarAdapter.class)
    public Calendar getEventExecutionTime() {
        return eventExecutionTime;
    }

    public void setEventExecutionTime(Calendar eventExecutionTime) {
        this.eventExecutionTime = eventExecutionTime;
    }
}
