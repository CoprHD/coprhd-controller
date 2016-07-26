/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import java.net.URI;
import java.util.Date;

import com.emc.storageos.db.client.model.*;
import com.emc.vipr.model.catalog.OrderCommonParam;
import com.emc.vipr.model.catalog.ScheduleInfo;

/**
 * ScheduledEvent is used to derive a set of catalog orders based on the schedule info.
 */
@Cf("ScheduledEvent")
public class ScheduledEvent extends DataObject implements TenantDataObject {

    public static final String EVENT_TYPE = "eventType";
    public static final String EVENT_STATUS = "eventStatus";
    public static final String SCHEDULE_INFO = "scheduleInfo";
    public static final String LATEST_ORDER_ID = "latestOrderId";
    public static final String EXECUTION_WINDOW_ID = "executionWindowId";
    public static final String CATALOG_SERVICE_ID = "catalogServiceId";
    public static final String ORDER_COMMON_PARAM = "orderCommonParam";

    public static final String TENANT = TenantDataObject.TENANT_COLUMN_NAME;

    private ScheduledEventType eventType;

    private String scheduleInfo; // concrete schedule info

    private ScheduledEventStatus eventStatus; // even status

    // TODO: for scheduler framework later
    // private OrderCommonParam orderCommonParam; // the common order info

    private URI latestOrderId;               // the latest to-be scheduled associated orderId

    private NamedURI executionWindowId;     // the execution window Id the event should obey.

    private URI catalogServiceId;           // the associated catalog service Id

    private String tenant;                   // the owner tenant

    private String orderCommonParam;        // common order related params

    @Name(EVENT_TYPE)
    public ScheduledEventType getEventType() {
        return eventType;
    }
    public void setEventType(ScheduledEventType eventType) {
        this.eventType = eventType;
        setChanged(EVENT_TYPE);
    }

    @Name(SCHEDULE_INFO)
    public String getScheduleInfo() {
        return scheduleInfo;
    }

    public void setScheduleInfo(String scheduleInfo) {
        this.scheduleInfo = scheduleInfo;
        setChanged(SCHEDULE_INFO);
    }

    @Name(EVENT_STATUS)
    public ScheduledEventStatus getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(ScheduledEventStatus eventStatus) {
        this.eventStatus = eventStatus;
        setChanged(EVENT_STATUS);
    }

    @Name(LATEST_ORDER_ID)
    public URI getLatestOrderId() {
        return latestOrderId;
    }

    public void setLatestOrderId(URI latestOrderId) {
        this.latestOrderId = latestOrderId;
        setChanged(LATEST_ORDER_ID);
    }

    @Name(EXECUTION_WINDOW_ID)
    public NamedURI getExecutionWindowId() {
        return executionWindowId;
    }

    public void setExecutionWindowId(NamedURI executionWindowId) {
        this.executionWindowId = executionWindowId;
        setChanged(EXECUTION_WINDOW_ID);
    }

    @Name(CATALOG_SERVICE_ID)
    public URI getCatalogServiceId() {
        return catalogServiceId;
    }

    public void setCatalogServiceId(URI catalogServiceId) {
        this.catalogServiceId = catalogServiceId;
        setChanged(CATALOG_SERVICE_ID);
    }

    @AlternateId("TenantToScheduledEvent")
    @Name(TENANT)
    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
        setChanged(TENANT);
    }

    @Name(ORDER_COMMON_PARAM)
    public String getOrderCommonParam() {
        return orderCommonParam;
    }

    public void setOrderCommonParam(String orderCommonParam) {
        this.orderCommonParam = orderCommonParam;
        setChanged(ORDER_COMMON_PARAM);
    }
}
