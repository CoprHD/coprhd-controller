/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.db.client.model.uimodels;

import java.net.URI;
import java.util.Calendar;
import java.util.Date;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.valid.EnumType;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.RelationIndex;

@Cf("Order")
public class Order extends ModelObject implements TenantDataObject {
    public static final String SUBMITTED_BY_USER_ID = "submittedByUserId";
    public static final String SUBMITTED = "indexed";
    public static final String CATALOG_SERVICE_ID = "catalogServiceId";
    public static final String EXECUTION_STATE_ID = "executionStateId";
    public static final String SUMMARY = "summary";
    public static final String MESSAGE = "message";
    public static final String DATE_COMPLETED = "dateCompleted";
    public static final String ORDER_STATUS = "orderStatus";
    public static final String EXECUTION_WINDOW_ID = "executionWindowId";
    public static final String TENANT = TenantDataObject.TENANT_COLUMN_NAME;
    public static final String ORDER_NUMBER = "orderNumber";
    public static final String SCHEDULED_EVENT_ID = "scheduledEventId";
    public static final String SCHEDULED_TIME = "scheduledTime";
    public static final String WORKFLOW_DOCUMENT = "workflowDocument";

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

    private URI scheduledEventId;

    private Calendar scheduledTime;
    
    private String workflowDocument;

    /**
     * Field used for indexing updated time
     */
    private Boolean indexed;

    @Name(CATALOG_SERVICE_ID)
    public URI getCatalogServiceId() {
        return catalogServiceId;
    }

    public void setCatalogServiceId(final URI catalogServiceId) {
        this.catalogServiceId = catalogServiceId;
        setChanged(CATALOG_SERVICE_ID);
    }

    @Name(ORDER_NUMBER)
    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(final String orderNumber) {
        this.orderNumber = orderNumber;
        setChanged(ORDER_NUMBER);
    }

    @Name(SUMMARY)
    public String getSummary() {
        return summary;
    }

    public void setSummary(final String summary) {
        this.summary = summary;
        setChanged(SUMMARY);
    }

    @Name(MESSAGE)
    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
        setChanged(MESSAGE);
    }

    @Name(DATE_COMPLETED)
    public Date getDateCompleted() {
        return dateCompleted;
    }

    public void setDateCompleted(final Date dateCompleted) {
        this.dateCompleted = dateCompleted;
        setChanged(DATE_COMPLETED);
    }

    @AlternateId("OrderStatusToOrder")
    @EnumType(OrderStatus.class)
    @Name(ORDER_STATUS)
    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(final String status) {
        this.orderStatus = status;
        setChanged(ORDER_STATUS);
    }

    @ClassNameTimeSeries("UserToOrdersByTimeStamp")
    @Name(SUBMITTED_BY_USER_ID)
    public String getSubmittedByUserId() {
        return submittedByUserId;
    }

    public void setSubmittedByUserId(final String submittedBy) {
        this.submittedByUserId = submittedBy;
        setChanged(SUBMITTED_BY_USER_ID);
    }

    @Name(EXECUTION_STATE_ID)
    public URI getExecutionStateId() {
        return executionStateId;
    }

    public void setExecutionStateId(final URI executionStateId) {
        this.executionStateId = executionStateId;
        setChanged(EXECUTION_STATE_ID);
    }

    @Name(EXECUTION_WINDOW_ID)
    public NamedURI getExecutionWindowId() {
        return executionWindowId;
    }

    public void setExecutionWindowId(final NamedURI executionWindowId) {
        this.executionWindowId = executionWindowId;
        setChanged(EXECUTION_WINDOW_ID);
    }

    @Override @AlternateId("TenantToOrder")
    @Name(TENANT)
    public String getTenant() {
        return tenant;
    }

    @Override public void setTenant(final String tenant) {
        this.tenant = tenant;
        setChanged(TENANT);
    }

    @RelationIndex(cf = "RelationIndex", type = ScheduledEvent.class)
    @Name(SCHEDULED_EVENT_ID)
    public URI getScheduledEventId() {
        return scheduledEventId;
    }

    public void setScheduledEventId(final URI scheduledEventId) {
        this.scheduledEventId = scheduledEventId;
        setChanged(SCHEDULED_EVENT_ID);
    }

    @Name(SCHEDULED_TIME)
    public Calendar getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(final Calendar scheduledTime) {
        this.scheduledTime = scheduledTime;
        setChanged(SCHEDULED_TIME);
    }

    @Name(WORKFLOW_DOCUMENT)
    public String getWorkflowDocument() {
        return workflowDocument;
    }
    
    public void setWorkflowDocument (final String workflowDocument) {
        this.workflowDocument = workflowDocument;
        setChanged(WORKFLOW_DOCUMENT);
    }
    @Override
    public void markUpdated() {
        super.markUpdated();
        if (indexed == null ) {
            setIndexed(Boolean.TRUE);
        }
    }

    /**
     * Return value of indexed field
     *
     * @return
     */
    @Name("indexed")
    @TimeSeriesAlternateId("AllOrdersByTimeStamp")
    public Boolean getIndexed() {
        return indexed;
    }

    public void setIndexed(final Boolean indexed) {
        this.indexed = indexed;
        setChanged("indexed");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\nOrderId:")
                .append(getId())
                .append("\nOrder Number:")
                .append(getOrderNumber())
                .append("\nSubmitted By:")
                .append(getSubmittedByUserId())
                .append("\nDate Submitted:")
                .append(getCreationTime().getTime())
                .append("\nDate Completed:")
                .append(getDateCompleted() == null ? null : getDateCompleted().getTime())
                .append("\nMessage:")
                .append(getMessage())
                .append("\nStatus:")
                .append(getOrderStatus())
                .append("\nCatalog ID:")
                .append(getCatalogServiceId())
                .append("\nTenant ID:")
                .append(getTenant())
                .append("\nScheduled Event ID:")
                .append(getScheduledEventId())
                .append("\n");

        return builder.toString();
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getLabel(),
                getOrderNumber(), getTenant(), getId() };
    }
}
