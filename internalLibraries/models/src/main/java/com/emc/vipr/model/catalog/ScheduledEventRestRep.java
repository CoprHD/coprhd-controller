/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "scheduled_event")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ScheduledEventRestRep extends DataObjectRestRep {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String FINISHED = "FINISHED";
    public static final String CANCELLED = "CANCELLED";

    private ScheduleInfo scheduleInfo;

    private URI latestOrderId;

    /**
     * Event Status. One of: PENDING, APPROVED, REJECTED, FINISHED, CANDELLED
     */
    private String eventStatus;

    /**
     * Service that this order will execute
     */
    private RelatedResourceRep catalogService;

    /**
     * Execution window to run this order in
     */
    private RelatedResourceRep executionWindow;

    private RelatedResourceRep tenant;

    private OrderCreateParam orderCreateParam;

    @XmlElement(name = "scheduleInfo")
    public ScheduleInfo getScheduleInfo() {
        return scheduleInfo;
    }

    public void setScheduleInfo(ScheduleInfo scheduleInfo) {
        this.scheduleInfo = scheduleInfo;
    }

    @XmlElement(name = "latestOrderId")
    public URI getLatestOrderId() {
        return latestOrderId;
    }

    public void setLatestOrderId(URI latestOrderId) {
        this.latestOrderId = latestOrderId;
    }

    @XmlElement(name = "execution_window")
    public RelatedResourceRep getExecutionWindow() {
        return executionWindow;
    }

    public void setExecutionWindow(RelatedResourceRep executionWindow) {
        this.executionWindow = executionWindow;
    }

    @XmlElement(name = "catalog_service")
    public RelatedResourceRep getCatalogService() {
        return catalogService;
    }

    public void setCatalogService(RelatedResourceRep service) {
        this.catalogService = service;
    }

    @XmlElement(name = "event_status")
    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

	@XmlElement(name = "orderCreateParam")
    public OrderCreateParam getOrderCreateParam() {
        return orderCreateParam;
    }

    public void setOrderCreateParam(OrderCreateParam orderCreateParam) {
        this.orderCreateParam = orderCreateParam;
    }

    @Override
    public String toString() {
        return String.format("Event %s (%s)", getId(), eventStatus);
    }
}
