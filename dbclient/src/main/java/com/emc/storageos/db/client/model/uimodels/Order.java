/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.valid.EnumType;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import java.net.URI;
import java.util.Date;

@Cf("Order")
public class Order extends ModelObject implements TenantDataObject {

    public static final String SUBMITTED_BY_USER_ID = "submittedByUserId";
    public static final String CATALOG_SERVICE_ID = "catalogServiceId";
    public static final String EXECUTION_STATE_ID = "executionStateId";
    public static final String SUMMARY = "summary";
    public static final String MESSAGE = "message";
    public static final String DATE_COMPLETED = "dateCompleted";
    public static final String ORDER_STATUS = "orderStatus";
    public static final String EXECUTION_WINDOW_ID = "executionWindowId";
    public static final String TENANT = TenantDataObject.TENANT_COLUMN_NAME;
    public static final String ORDER_NUMBER = "orderNumber";

    /** User friendly Order number */
    private String orderNumber;

    /** The user that submitted the order. */
    private String submittedByUserId;

    private URI catalogServiceId;

    /** The order execution state. */
    private URI executionStateId;

    private NamedURI executionWindowId;

    private String summary;

    private String message;

    private Date dateCompleted;

    private String orderStatus;

    private String tenant;

    /**
     * Field used for indexing updated time
     */
    private Boolean indexed;

    @Name(CATALOG_SERVICE_ID)
    public URI getCatalogServiceId() {
        return catalogServiceId;
    }

    public void setCatalogServiceId(URI catalogServiceId) {
        this.catalogServiceId = catalogServiceId;
        setChanged(CATALOG_SERVICE_ID);
    }

    @Name(ORDER_NUMBER)
    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        setChanged(ORDER_NUMBER);
    }

    @Name(SUMMARY)
    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
        setChanged(SUMMARY);
    }

    @Name(MESSAGE)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        setChanged(MESSAGE);
    }

    @Name(DATE_COMPLETED)
    public Date getDateCompleted() {
        return dateCompleted;
    }

    public void setDateCompleted(Date dateCompleted) {
        this.dateCompleted = dateCompleted;
        setChanged(DATE_COMPLETED);
    }

    @AlternateId("OrderStatusToOrder")
    @EnumType(OrderStatus.class)
    @Name(ORDER_STATUS)
    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String status) {
        this.orderStatus = status;
        setChanged(ORDER_STATUS);
    }

    @AlternateId("UserToOrders")
    @Name(SUBMITTED_BY_USER_ID)
    public String getSubmittedByUserId() {
        return submittedByUserId;
    }

    public void setSubmittedByUserId(String submittedBy) {
        this.submittedByUserId = submittedBy;
        setChanged(SUBMITTED_BY_USER_ID);
    }

    @Name(EXECUTION_STATE_ID)
    public URI getExecutionStateId() {
        return executionStateId;
    }

    public void setExecutionStateId(URI executionStateId) {
        this.executionStateId = executionStateId;
        setChanged(EXECUTION_STATE_ID);
    }

    @Name(EXECUTION_WINDOW_ID)
    public NamedURI getExecutionWindowId() {
        return executionWindowId;
    }

    public void setExecutionWindowId(NamedURI executionWindowId) {
        this.executionWindowId = executionWindowId;
        setChanged(EXECUTION_WINDOW_ID);
    }

    @AlternateId("TenantToOrder")
    @Name(TENANT)
    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
        setChanged(TENANT);
    }

    @Override
    public void markUpdated() {
        super.markUpdated();
        setIndexed(Boolean.TRUE);
    }

    /**
     * Return value of indexed field
     * 
     * @return
     */
    @Name("indexed")
    @DecommissionedIndex("timeseriesIndex")
    public Boolean getIndexed() {
        return indexed;
    }

    public void setIndexed(Boolean indexed) {
        this.indexed = indexed;
        setChanged("indexed");
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getLabel(),
                getOrderNumber(), getTenant(), getId() };
    }
}
