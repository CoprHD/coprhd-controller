/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.beans.Transient;
import java.io.Serializable;
import java.net.URI;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.util.ExceptionUtils;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.google.common.collect.Maps;

/**
 * Operation status
 */
@XmlRootElement(name = "operation")
public class Operation extends AbstractSerializableNestedObject implements ClockIndependentValue,
        Serializable {

    private static final Logger _log = LoggerFactory.getLogger(Operation.class);

    // enumeration of status value
    public enum Status {
        pending, ready, error, suspended_no_error, suspended_error;

        public static Status toStatus(String status) {
            try {
                return valueOf(status.toLowerCase());
            } catch (Exception e) {
                throw new IllegalArgumentException("status: " + status + " is not a valid status");
            }
        }
    }

    static final String STATUS_FIELD = "status";
    static final String PROGRESS_FIELD = "progress";
    static final String MESSAGE_FIELD = "message";
    static final String NAME_FIELD = "name";
    static final String DESCRIPTION_FIELD = "description";
    static final String START_TIME_FIELD = "starttime";
    static final String END_TIME_FIELD = "endtime";
    static final String SERVICE_CODE_FIELD = "servicecode";
    static final String ASSOCIATED_RESOURCES_FIELD = "associated";

    // track - set of fields modified
    protected Set<String> _changedFields;

    // ID of the actual TASK from the Task CF
    private Map<URI, Task> tasks = Maps.newHashMap();

    public Operation() {
        _changedFields = new HashSet<String>();
        updateStatus(Status.pending.name());
    }

    @Deprecated
    public Operation(String status) {
        this(status, null, null);
    }

    @Deprecated
    public Operation(String status, ServiceCode code) {
        this(status, code, null);
    }

    @Deprecated
    public Operation(String status, String message) {
        this(status, null, message);
    }

    @Deprecated
    public Operation(String status, ServiceCode code, String message) {
        setStatus(status);
        if (message != null) {
            setMessage(message);
        }
        if (code != null) {
            setServiceCode(code.getCode());
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
     * This method sets the status of the operation to "ready" and updates progress to be 100%
     * 
     * @return
     */
    public void ready(String message) {
        setMessage(message);
        setProgress(100);
        updateStatus(Status.ready.name());
    }
    
    public void suspendedNoError() {
        suspendedNoError("Operation has been suspended due to request or configuration");
    }
    
    public void suspendedNoError(String message) {
        setMessage(message);
        updateStatus(Status.suspended_no_error.name());
    }
    
    public void suspendedError(String message) {
        setMessage(message);
        updateStatus(Status.suspended_error.name());
    }    
    
    /**
     * This method sets the status of the operation to "error"
     * 
     * @return
     */
    public void suspendedError(ServiceCoded sc) {
        if (sc != null) {
            setServiceCode(sc.getServiceCode().getCode());
            setMessage(sc.getMessage());
        }
        updateStatus(Status.suspended_error.name());
        if (sc instanceof Exception) {
            _log.info("Setting operation to suspended with error due to an exception {}",
                    ExceptionUtils.getExceptionMessage((Exception) sc));
            _log.info("Caused by: ", (Exception) sc);
        }
    }

    public void pending()  {
        setMessage("Operation has been restarted");
        updateStatus(Status.pending.name());
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
        updateStatus(Status.error.name());
        if (sc instanceof Exception) {
            _log.info("Setting operation to error due to an exception {}",
                    ExceptionUtils.getExceptionMessage((Exception) sc));
            _log.info("Caused by: ", (Exception) sc);
        }
    }

    public ServiceError getServiceError() {
        ServiceCode serviceCode = ServiceCode.toServiceCode(getServiceCode());
        ServiceError serviceError = ServiceError.buildServiceError(serviceCode, getMessage());
        return serviceError;
    }

    @Override
    public int ordinal() {
        return Status.valueOf(getStatus()).ordinal();
    }

    /**
     * Get status
     * 
     * @return
     */
    @XmlElement
    public String getStatus() {
        return getStringField(STATUS_FIELD);
    }

    /**
     * Convenience for setting the description and message in a uniform way
     * in an operation object.
     * 
     * @param resourceType input resource type
     */
    public void setResourceType(ResourceOperationTypeEnum resourceType) {
        setName(resourceType.getName());
        setDescription(resourceType.getDescription());
    }

    /**
     * Use methods "ready(...)" or "error(...)" to set the status instead
     * 
     * @param status
     * @throws IllegalArgumentException
     */
    @Deprecated
    public void setStatus(String status) throws IllegalArgumentException {
        if (isValidStatus(status) == false) {
            throw new IllegalArgumentException("status: " + status + " is not a valid status");
        }
        setField(STATUS_FIELD, status);
        updateChangedField(STATUS_FIELD);
    }

    private void updateStatus(String status) throws IllegalArgumentException {
        if (isValidStatus(status) == false) {
            throw new IllegalArgumentException("status: " + status + " is not a valid status");
        }
        setField(STATUS_FIELD, status);
        updateChangedField(STATUS_FIELD);
    }

    /**
     * Get progress
     * 
     * @return null if no progress information is available
     */
    @XmlElement
    public Integer getProgress() {
        return getIntField(PROGRESS_FIELD);
    }

    public void setProgress(int progress) {
        setField(PROGRESS_FIELD, progress);
        updateChangedField(PROGRESS_FIELD);
    }

    @XmlElement
    public String getDescription() {
        return getStringField(DESCRIPTION_FIELD);
    }

    public void setDescription(String description) {
        if (getDescription() == null) {
            setField(DESCRIPTION_FIELD, description);
            updateChangedField(DESCRIPTION_FIELD);
        }
    }

    @XmlElement
    public String getName() {
        return getStringField(NAME_FIELD);
    }

    public void setName(String name) {
        if (getName() == null) {
            setField(NAME_FIELD, name);
            updateChangedField(NAME_FIELD);
        }
    }

    @XmlElement
    public Calendar getStartTime() {
        return getDateField(START_TIME_FIELD);
    }

    public void setStartTime(Calendar time) {
        setField(START_TIME_FIELD, time);
        updateChangedField(START_TIME_FIELD);
    }

    @XmlElement
    public Calendar getEndTime() {
        return getDateField(END_TIME_FIELD);
    }

    public void setEndTime(Calendar time) {
        setField(END_TIME_FIELD, time);
        updateChangedField(END_TIME_FIELD);
    }

    /**
     * Get any message for operation
     * 
     * @return
     */
    @XmlElement
    public String getMessage() {
        return getStringField(MESSAGE_FIELD);
    }

    public void setMessage(String message) {
        setField(MESSAGE_FIELD, message);
        updateChangedField(MESSAGE_FIELD);
    }

    /**
     * Get service code
     * 
     * @return null if no service code is available
     */
    @XmlElement
    public Integer getServiceCode() {
        return getIntField(SERVICE_CODE_FIELD);
    }

    public void setServiceCode(int code) {
        setField(SERVICE_CODE_FIELD, code);
        updateChangedField(SERVICE_CODE_FIELD);
    }

    @XmlElement
    public List<String> getAssociatedResourcesField() {
        return getListOfStringsField(ASSOCIATED_RESOURCES_FIELD);
    }

    public void setAssociatedResourcesField(String associatedIds) {
        setField(ASSOCIATED_RESOURCES_FIELD, associatedIds);
        updateChangedField(ASSOCIATED_RESOURCES_FIELD);
    }

    public String rawAssociatedResources() {
        return getStringField(ASSOCIATED_RESOURCES_FIELD);
    }

    // During the request, holds the task from the Task CF that maps to this Operation
    @Transient
    public Task getTask(URI id) {
        return tasks.get(id);
    }

    public void addTask(URI id, Task task) {
        tasks.put(id, task);
    }

    @Override
    public final String toString() {
        return String
                .format("Operation(Status:%s, Progress:%s, Name:%s, Message:%s, Description:%s, StartTime:%s, EndTime:%s, ServiceCode:%s)",
                        getStatus(), getProgress(), getName(), getMessage(), getDescription(), getStartTime(),
                        getEndTime(), getServiceCode());
    }

    private void updateChangedField(String fieldName) {
        if (_changedFields == null) {
            _changedFields = new HashSet<String>();
        }
        _changedFields.add(fieldName);
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
    
    /**
     * Verifies the Terminal state of the task status
     * @param status Status of task
     * @return True if status is error or ready. Else false
     */
    public static boolean isTerminalState(Status status) {
    	return (status == Status.ready || status == Status.error);
    }
}
