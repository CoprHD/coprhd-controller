/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.search.Tags;

@XmlRootElement
public class OrderInfo extends ModelInfo {

    /**
     * Number of this order
     */
    private String orderNumber;

    /**
     * User who submitted this order
     */
    private String submittedBy;

    /**
     * Service that this order will execute
     */
    private Reference service;

    /**
     * Execution window to run this order in
     */
    private String executionWindow;

    /**
     * Summary for this order
     */
    private String summary;

    /**
     * System generated message relating to this order
     */
    private String message;

    /**
     * Date the order was placed
     */
    private Date createdDate;

    /**
     * Date the order completed
     */
    private Date dateCompleted;

    /**
     * Order Status. One of: PENDING, EXECUTING, PARTIAL_SUCCESS, SUCCESS, ERROR, SCHEDULED, CANCELLED, APPROVAL, APPROVED, REJECTED
     */
    private String status;

    /**
     * Reference to the execution information
     */
    private Reference execution;

    /**
     * Parameters to an order
     */
    private List<Parameter> parameters;

    /**
     * Tags on the order object
     */
    private Tags tags;

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getDateCompleted() {
        return dateCompleted;
    }

    public void setDateCompleted(Date dateCompleted) {
        this.dateCompleted = dateCompleted;
    }

    public Reference getExecution() {
        return execution;
    }

    public void setExecution(Reference execution) {
        this.execution = execution;
    }

    public String getExecutionWindow() {
        return executionWindow;
    }

    public void setExecutionWindow(String executionWindow) {
        this.executionWindow = executionWindow;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Reference getService() {
        return service;
    }

    public void setService(Reference service) {
        this.service = service;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

    public void setTags(Tags tags) {
        this.tags = tags;
    }

    public Tags getTags() {
        return this.tags;
    }

    @Override
    public String toString() {
        return String.format("Order %s (%s) %s - %s %s", orderNumber, id, summary, status, message);
    }

}
