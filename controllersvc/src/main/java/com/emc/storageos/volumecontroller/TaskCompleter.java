/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * Initiator param for block export operations.
 */
@XmlRootElement
public abstract class TaskCompleter implements Serializable {

    private static final Logger _logger = LoggerFactory.getLogger(TaskCompleter.class);
    private static final long serialVersionUID = -1520175533121538383L;

    @XmlElement
    private Class _clazz;

    @XmlElement
    protected String _opId;

    @XmlElement
    private final List<URI> _ids = new ArrayList<URI>();

    @XmlElement
    private final Set<URI> _consistencyGroupIds = new HashSet<>();

    @XmlElement
    private final Set<URI> _volumeGroupIds = new HashSet<>();

    // Whether to notify workflow when task is complete
    @XmlTransient
    private boolean notifyWorkflow = true;

    /**
     * JAXB requirement
     */
    public TaskCompleter() {
    }

    public TaskCompleter(Class clazz, URI id, String opId) {
        _clazz = clazz;
        _ids.add(id);
        _opId = opId;
    }

    public TaskCompleter(Class clazz, List<URI> ids, String opId) {
        _clazz = clazz;
        _ids.addAll(ids);
        _opId = opId;
    }

    public TaskCompleter(AsyncTask task) {
        _clazz = task._clazz;
        _ids.add(task._id);
        _opId = task._opId;
    }

    public Class getType() {
        return _clazz;
    }

    public List<URI> getIds() {
        return _ids;
    }

    public void addIds(Collection<URI> ids) {
        if (ids != null) {
            for (URI uri : ids) {
                if (!_ids.contains(uri)) {
                    _ids.add(uri);
                }
            }
        }
    }

    public URI getId(int index) {
        if ((index >= 0) && (index < _ids.size())) {
            return _ids.get(index);
        }
        else {
            return null;
        }
    }

    public URI getId() {
        return getId(0);
    }

    public String getOpId() {
        return _opId;
    }

    public void setOpId(String taskId) {
        _opId = taskId;
    }

    public Set<URI> getConsistencyGroupIds() {
        return _consistencyGroupIds;
    }

    public boolean addConsistencyGroupId(URI consistencyGroupId) {
        if (consistencyGroupId != null) {
            return _consistencyGroupIds.add(consistencyGroupId);
        }
        return false;
    }

    public Set<URI> getVolumeGroupIds() {
        return _volumeGroupIds;
    }

    public boolean addVolumeGroupId(URI volumeGroupId) {
        if (volumeGroupId != null) {
            return _volumeGroupIds.add(volumeGroupId);
        }
        return false;
    }

    public boolean isNotifyWorkflow() {
        return notifyWorkflow;
    }

    public void setNotifyWorkflow(boolean notifyWorkflow) {
        this.notifyWorkflow = notifyWorkflow;
    }

    /**
     * Update the Operation status of the overall task to "ready" and the current workflow step to "success" (if any)
     * 
     * @param dbClient
     * @throws DeviceControllerException TODO
     */
    public void ready(DbClient dbClient) throws DeviceControllerException {
        complete(dbClient, Status.ready, null);
    }

    public void ready(DbClient dbClient, ControllerLockingService locker) throws DeviceControllerException {
        complete(dbClient, locker, Status.ready, null);
    }

    /**
     * Update the Operation status of the task to "error" and the current workflow step to "error" too (if any)
     * 
     * @param dbClient
     * @param message String message from controller
     * @throws DeviceControllerException
     */
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException {
        complete(dbClient, Status.error, serviceCoded != null ? serviceCoded : DeviceControllerException.errors.unforeseen());
    }

    public void error(DbClient dbClient, ControllerLockingService locker, ServiceCoded serviceCoded) throws DeviceControllerException {
        complete(dbClient, locker, Status.error, serviceCoded != null ? serviceCoded : DeviceControllerException.errors.unforeseen());
    }

    public void statusReady(DbClient dbClient) throws DeviceControllerException {
        setStatus(dbClient, Status.ready, (ServiceCoded) null, (String) null);
    }

    public void statusReady(DbClient dbClient, String message) throws DeviceControllerException {
        setStatus(dbClient, Status.ready, null, message);
    }

    public void statusPending(DbClient dbClient, String message) throws DeviceControllerException {
        setStatus(dbClient, Status.pending, null, message);
    }

    public void statusError(DbClient dbClient, ServiceCoded serviceCoded)
            throws DeviceControllerException {
        setStatus(dbClient, Status.error, serviceCoded);
    }

    protected void setStatus(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        setStatus(dbClient, status, coded, null);
    }

    protected void setStatus(DbClient dbClient, Operation.Status status, ServiceCoded coded, String message)
            throws DeviceControllerException {
        switch (status) {
            case error:
                for (URI id : _ids) {
                    dbClient.error(_clazz, id, _opId, coded);
                }
                break;
            case ready:
                for (URI id : _ids) {
                    if (message == null) {
                        dbClient.ready(_clazz, id, _opId);
                    } else {
                        dbClient.ready(_clazz, id, _opId, message);
                    }
                }
                break;
            default:
                if (message != null) {
                    for (URI id : _ids) {
                        dbClient.pending(_clazz, id, _opId, message);
                    }
                }
        }
    }

    /**
     * This method will be called upon the job execution finished
     * 
     * @param dbClient
     * @param status
     * @param coded
     * @throws DeviceControllerException
     */
    protected abstract void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException;

    /**
     * This method will be called upon job execution finish with a locking controller.
     * It is not expected that non-locking controllers will call this version, however we need a base
     * method so we don't need to ship around TaskLockingCompleters all over the code.
     * 
     * @param dbClient
     * @param locker
     * @param status
     * @param coded
     * @throws DeviceControllerException
     */
    protected void complete(DbClient dbClient, ControllerLockingService locker, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        complete(dbClient, status, coded);
    }

    /**
     * Update a Workflow Step State.
     * 
     * @param state Workflow.StepState
     * @param coded
     * @throws WorkflowException
     */
    protected void updateWorkflowState(Workflow.StepState state, ServiceCoded coded)
            throws WorkflowException {
        switch (state) {
            case ERROR:
                WorkflowStepCompleter.stepFailed(getOpId(), coded);
                break;
            case EXECUTING:
                WorkflowStepCompleter.stepExecuting(getOpId());
                break;
            case SUCCESS:
            default:
                WorkflowStepCompleter.stepSucceded(getOpId());
        }
    }

    /**
     * Update a Workflow Step by using the Operation.Status.
     * 
     * @param status Operation.Status
     * @param coded
     * @throws WorkflowException
     */
    protected void updateWorkflowStatus(Operation.Status status, ServiceCoded coded)
            throws WorkflowException {
        switch (status) {
            case error:
                WorkflowStepCompleter.stepFailed(getOpId(), coded);
                break;
            case pending:
                WorkflowStepCompleter.stepExecuting(getOpId());
                break;
            default:
                WorkflowStepCompleter.stepSucceded(getOpId());
        }
    }

    /**
     * Update the workflow step context
     * 
     * @param context context information
     */
    public void updateWorkflowStepContext(Object context) {
        WorkflowService.getInstance().storeStepData(getOpId(), context);
        _logger.info("Storing rollback context to op: " + getOpId() + " context: " + context.toString());
    }

    /**
     * Set the error status of the dataObject referred by the uri
     * 
     * @param dbClient [in] - Database Client
     * @param clazz [in] - Class in DataObject hierarchy
     * @param uri [in] - URI of clazz
     * @param coded [in] - ServiceCoded containing error message reference
     */
    protected void setErrorOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, URI uri,
            ServiceCoded coded) {
        if (!NullColumnValueGetter.isNullURI(uri)) {
            dbClient.error(clazz, uri, getOpId(), coded);
        }
    }

    /**
     * Set the error status of the dataObject
     * 
     * @param dbClient [in] - Database Client
     * @param clazz [in] - Class in DataObject hierarchy
     * @param dObject [in] - DataObject of clazz
     * @param coded [in] - ServiceCoded containing error message reference
     */
    protected void setErrorOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, DataObject dObject,
            ServiceCoded coded) {
        if (dObject != null) {
            dbClient.error(clazz, dObject.getId(), getOpId(), coded);
        }
    }

    /**
     * Set the ready status of the dataObject referred by the uri
     * 
     * @param dbClient [in] - Database Client
     * @param clazz [in] - Class in DataObject hierarchy
     * @param uri [in] - URI of clazz
     */
    protected void setReadyOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, URI uri) {
        if (!NullColumnValueGetter.isNullURI(uri)) {
            dbClient.ready(clazz, uri, getOpId());
        }
    }

    /**
     * Set the ready status on the dataObject
     * 
     * @param dbClient [in] - Database Client
     * @param clazz [in] - Class in DataObject hierarchy
     * @param dObject [in] - DataObject of clazz
     */
    protected void setReadyOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, DataObject dObject) {
        if (dObject != null) {
            dbClient.ready(clazz, dObject.getId(), getOpId());
        }
    }

    protected void updateConsistencyGroupTasks(DbClient dbClient, Operation.Status status, ServiceCoded coded) {
        for (URI consistencyGroupId : getConsistencyGroupIds()) {
            _logger.info("Updating consistency group task: {}", consistencyGroupId);
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockConsistencyGroup.class, consistencyGroupId, coded);
                    break;
                case ready:
                    setReadyOnDataObject(dbClient, BlockConsistencyGroup.class, consistencyGroupId);
                    break;
            }
        }
    }
}
