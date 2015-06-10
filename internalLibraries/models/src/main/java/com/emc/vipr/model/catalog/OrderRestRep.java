/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "order")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class OrderRestRep extends DataObjectRestRep {
    
    public static final String PENDING = "PENDING";
    public static final String EXECUTING = "EXECUTING"; 
    public static final String SUCCESS = "SUCCESS"; 
    public static final String PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    public static final String ERROR = "ERROR"; 
    public static final String SCHEDULED = "SCHEDULED"; 
    public static final String CANCELLED = "CANCELLED"; 
    public static final String APPROVAL = "APPROVAL"; 
    public static final String APPROVED = "APPROVED"; 
    public static final String REJECTED = "REJECTED"; 
    
    /**
     * Number of this order
     */
    private String orderNumber;                 
    
    /**
     * User who submitted this order
     */
    private String submittedBy;                 
    
    /**
     * Summary for this order
     */
    private String summary;                     
    
    /**
     * System generated message relating to this order
     */
    private String message;                     
    
    /**
     * Date the order completed
     */
    private Date dateCompleted;                 
    
    /**
     * Order Status. One of: PENDING, EXECUTING, SUCCESS, ERROR, SCHEDULED, CANCELLED, APPROVAL, APPROVED, REJECTED
     */
    private String orderStatus;                    
    
    /**
     * Parameters to an order
     */
    private List<Parameter> parameters;         
    
    /**
     * Service that this order will execute
     */
    private RelatedResourceRep catalogService;             
    
    /**
     * Execution window to run this order in
     */
    private RelatedResourceRep executionWindow;
    
    private RelatedResourceRep tenant;
    
    private Date lastUpdated;
    
    @XmlElement(name = "order_number")
    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    @XmlElement(name = "date_completed")
    public Date getDateCompleted() {
        return dateCompleted;
    }

    public void setDateCompleted(Date dateCompleted) {
        this.dateCompleted = dateCompleted;
    }

    @XmlElement(name = "execution_window")
    public RelatedResourceRep getExecutionWindow() {
        return executionWindow;
    }

    public void setExecutionWindow(RelatedResourceRep executionWindow) {
        this.executionWindow = executionWindow;
    }

    @XmlElement(name = "message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement(name = "catalog_service")
    public RelatedResourceRep getCatalogService() {
        return catalogService;
    }

    public void setCatalogService(RelatedResourceRep service) {
        this.catalogService = service;
    }

    @XmlElement(name = "order_status")
    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String status) {
        this.orderStatus = status;
    }

    @XmlElement(name = "submitted_by")
    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    @XmlElement(name = "summary")
    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "last_updated")
    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @XmlElementWrapper(name = "parameters")
    @XmlElement(name = "parameter")
    public List<Parameter> getParameters() {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return String.format("Order %s (%s) %s - %s %s", orderNumber, getId(), summary, orderStatus, message);
    }
}
