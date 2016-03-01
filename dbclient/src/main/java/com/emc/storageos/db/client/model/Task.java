/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.Transient;
import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

/**
 * Represents a Resource Task
 */
@Cf("Task")
@NoInactiveIndex
// Because ViPR will create/delete lots of this object, this prevents too many tombstones in Decommissioned index
public class Task extends DataObject {
    private static final Logger _log = LoggerFactory.getLogger(Operation.class);

    private NamedURI resource;
    private String requestId;
    private String status;
    private Integer progress;
    private String message;
    private String description;
    private Integer serviceCode;
    private Calendar startTime;
    private Calendar endTime;
    private String associatedResources;
    private URI tenant;
    private boolean completedFlag;
    private URI workflow;
    private Calendar queuedStartTime;
    private String queueName;

    // enumeration of status value
    public enum Status {
        queued, pending, ready, error;

        public static Status toStatus(String status) {
            try {
                return valueOf(status.toLowerCase());
            } catch (Exception e) {
                throw new IllegalArgumentException("status: " + status + " is not a valid status");
            }
        }
    }

    public Task() {
    }

    @NamedRelationIndex(cf = "TaskResource", types={Volume.class, VirtualPool.class})
    @Name("resource")
    public NamedURI getResource() {
        return resource;
    }

    public void setResource(NamedURI resource) {
        if (!Objects.equals(this.resource, resource)) {
            this.resource = resource;
            setChanged("resource");
        }
    }

    @Name("requestId")
    @AlternateId("TaskRequestIds")
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        if (!Objects.equals(this.requestId, requestId)) {
            this.requestId = requestId;
            setChanged("requestId");
        }
    }

    @Name("taskStatus")
    @AggregatedIndex(cf = "AggregatedIndex", groupBy = "tenant")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (!isValidStatus(status)) {
            throw new IllegalArgumentException("status: " + status + " is not a valid status");
        }

        this.status = status;
        setChanged("taskStatus");
    }

    @Name("progress")
    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
        setChanged("progress");
    }

    @Name("message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        setChanged("message");
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("serviceCode")
    public Integer getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(Integer serviceCode) {
        this.serviceCode = serviceCode;
        setChanged("serviceCode");
    }

    @Name("startTime")
    public Calendar getStartTime() {
        return startTime;
    }

    public void setStartTime(Calendar startTime) {
        this.startTime = startTime;
        setChanged("startTime");
    }

    @Name("endTime")
    public Calendar getEndTime() {
        return endTime;
    }

    public void setEndTime(Calendar endTime) {
        this.endTime = endTime;
        setChanged("endTime");
        if (endTime != null) {
            setCompletedFlag(true);
        }
    }

    @Name("queuedStartTime")
    public Calendar getQueuedStartTime() {
        return queuedStartTime;
    }

    public void setQueuedStartTime(Calendar queuedStartTime) {
        this.queuedStartTime = queuedStartTime;
        setChanged("queuedStartTime");
    }

    @Name("queueName")
    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
        setChanged("queueName");
    }

    /**
     * Used to indicate that a task has completed
     * 
     * This places an entry into a timeseries index that can later be queried
     * 
     * Do not call directly, use {@link #setEndTime(java.util.Calendar)} instead
     */
    @Name("completedFlag")
    @DecommissionedIndex("timeseriesIndex")
    public Boolean getCompletedFlag() {
        return completedFlag;
    }

    public void setCompletedFlag(Boolean completedFlag) {
        if (!Objects.equals(this.completedFlag, completedFlag)) {
            this.completedFlag = completedFlag;
            setChanged("completedFlag");
        }
    }

    @Name("associatedResources")
    public String getAssociatedResources() {
        return associatedResources;
    }

    public void setAssociatedResources(String associatedResources) {
        this.associatedResources = associatedResources;
        setChanged("associatedResources");
    }

    @Transient
    public List<URI> getAssociatedResourcesList() {
        List<URI> resources = Lists.newArrayList();

        if (!StringUtils.isBlank(getAssociatedResources())) {
            StringTokenizer tokenizer = new StringTokenizer(getAssociatedResources(), ",");
            while (tokenizer.hasMoreElements()) {
                resources.add(URI.create(tokenizer.nextToken()));
            }
        }

        return resources;
    }

    @Transient
    public void setAssociatedResourcesList(List<URI> associatedResourcesList) {
        List<String> resources = Lists.transform(associatedResourcesList, new Function<URI, String>() {
            @Override
            public String apply(URI input) {
                return input.toString();
            }
        });

        setAssociatedResources(StringUtils.join(resources.toArray(), ","));
    }

    @Name("tenant")
    @RelationIndex(type = TenantOrg.class, cf = "RelationIndex")
    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        if (!Objects.equals(this.tenant, tenant)) {
            this.tenant = tenant;
            setChanged("tenant");
        }
    }

    @Name("workflow")
    public URI getWorkflow() {
        return workflow;
    }

    public void setWorkflow(URI workflow) {
        if (!Objects.equals(this.workflow, workflow)) {
            this.workflow = workflow;
            setChanged("workflow");
        }
    }

    /**
     * This method sets the status of the operation to "ready"
     * 
     * @return
     */
    public void ready() {
        ready("Operation completed successfully");
    }

    /**
     * This method sets the status of the operation to "ready"
     * 
     * @return
     */
    public void ready(String message) {
        setMessage(message);
        setStatus(Status.ready.name());
    }

    public boolean isQueued() {
        String status = getStatus();
        return Status.queued.name().equals(status);
    }

    public boolean isPending() {
        String status = getStatus();
        return status != null && status.equals(Status.pending.name());
    }

    public boolean isError() {
        return getStatus().equals(Status.error.name());
    }

    public boolean isReady() {
        return getStatus().equals(Status.ready.name());
    }

    /**
     * This method sets the status of the operation to "error"
     * 
     * @return
     */
    public void error(ServiceCoded sc) {

        if (sc != null) {
            setServiceCode(sc.getServiceCode().getCode());
            setMessage(sc.getMessage());
        }
        setStatus(Status.error.name());
        if (sc instanceof Exception) {
            _log.info("Setting operation to error due to an exception");
            _log.info("Caused by: ", (Exception) sc);
        }
    }

    /**
     * Checks to see whether the provided status is one of the defined valid
     * status(es).
     * 
     * @param statusStr - Status String
     * @return true, if the provided status is valid. otherwise false.
     */
    private boolean isValidStatus(String statusStr) {
        boolean valid = false;
        Status[] validStatus = Status.values();

        for (Status status : validStatus) {
            if (status.name().toUpperCase().equals(statusStr.toUpperCase())) {
                valid = true;
                break;
            }
        }
        return valid;
    }

}
