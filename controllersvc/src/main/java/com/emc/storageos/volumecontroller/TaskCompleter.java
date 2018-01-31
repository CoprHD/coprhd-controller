/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
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

    @XmlTransient
    private boolean asynchronous;

    @XmlTransient
    private boolean completed;

    @XmlTransient
    private boolean rollingBack;

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

    public boolean isAsynchronous() {
        return asynchronous;
    }

    public void setAsynchronous(boolean asynchronous) {
        this.asynchronous = asynchronous;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isRollingBack() {
        return rollingBack;
    }

    public void setRollingBack(boolean rollingBack) {
        this.rollingBack = rollingBack;
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
     * @throws DeviceControllerException
     *             TODO
     */
    public void ready(DbClient dbClient) throws DeviceControllerException {
        ready(dbClient, (ControllerLockingService) null);
    }

    public void ready(DbClient dbClient, ControllerLockingService locker) throws DeviceControllerException {
        try {
            if (locker == null) {
                complete(dbClient, Status.ready, (ServiceCoded) null);
            } else {
                complete(dbClient, locker, Status.ready, (ServiceCoded) null);
            }
        } finally {
            clearAllTasks(dbClient, Status.ready, (ServiceCoded) null);
            setCompleted(true);
        }
    }

    /**
     * Update the Operation status of the task to "error" and the current workflow step to "error" too (if any)
     * 
     * @param dbClient      Database client
     * @param serviceCoded  Service code
     * @throws DeviceControllerException
     */
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException {
        error(dbClient, (ControllerLockingService) null, serviceCoded);
        setCompleted(true);
    }

    public void error(DbClient dbClient, ControllerLockingService locker, ServiceCoded serviceCoded) throws DeviceControllerException {
        try {
            if (locker == null) {
                complete(dbClient, Status.error, serviceCoded != null ? serviceCoded : DeviceControllerException.errors.unforeseen());
            } else {
                complete(dbClient, locker, Status.error,
                        serviceCoded != null ? serviceCoded : DeviceControllerException.errors.unforeseen());
            }
        } finally {
            clearAllTasks(dbClient, Status.error, serviceCoded);
            setCompleted(true);
        }
    }

    public void suspendedNoError(DbClient dbClient, ControllerLockingService locker) throws DeviceControllerException {
        try {
            complete(dbClient, locker, Status.suspended_no_error, (ServiceCoded) null);
        } finally {
            clearAllTasks(dbClient, Status.suspended_no_error, (ServiceCoded) null);
        }
    }

    public void suspendedError(DbClient dbClient, ControllerLockingService locker, ServiceCoded serviceCoded)
            throws DeviceControllerException {
        try {
            complete(dbClient, locker, Status.suspended_error,
                serviceCoded != null ? serviceCoded : DeviceControllerException.errors.unforeseen());
        } finally {
            clearAllTasks(dbClient, Status.suspended_error, serviceCoded);
        }
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
        setStatus(_clazz, _ids, dbClient, status, coded, message);
    }

    protected void setStatus(Class<? extends DataObject> clazz, List<URI> ids, DbClient dbClient, Operation.Status status,
            ServiceCoded coded, String message) throws DeviceControllerException {

        // for debugging purpose. will remove later.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new Throwable().printStackTrace(pw);
        String stackTraceStr = sw.toString();

        _logger.info("============== setStatus get called. Stack Trace is {}", stackTraceStr);
        switch (status) {
            case error:
            for (URI id : ids) {
                dbClient.error(clazz, id, _opId, coded);
                }
                break;
            case ready:
            for (URI id : ids) {
                    if (message == null) {
                    dbClient.ready(clazz, id, _opId);
                    } else {
                    dbClient.ready(clazz, id, _opId, message);
                    }
                }
                break;
            case suspended_no_error:
            for (URI id : ids) {
                    if (message == null)
                    dbClient.suspended_no_error(clazz, id, _opId);
                    else
                    dbClient.suspended_no_error(clazz, id, _opId, message);
                }
                break;
            case suspended_error:
            for (URI id : ids) {
                dbClient.suspended_error(clazz, id, _opId, coded);
                }
                break;
            default:
                if (message != null) {
                for (URI id : ids) {
                    dbClient.pending(clazz, id, _opId, message);
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
     * @param state
     *            Workflow.StepState
     * @param coded
     * @throws WorkflowException
     */
    protected void updateWorkflowState(Workflow.StepState state, ServiceCoded coded)
            throws WorkflowException {
        switch (state) {
            case SUSPENDED_ERROR:
                WorkflowStepCompleter.stepSuspendedError(getOpId(), coded);
                break;
            case SUSPENDED_NO_ERROR:
                WorkflowStepCompleter.stepSuspendedNoError(getOpId());
                break;
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
     * @param status
     *            Operation.Status
     * @param coded
     * @throws WorkflowException
     */
    protected void updateWorkflowStatus(Operation.Status status, ServiceCoded coded)
            throws WorkflowException {
        switch (status) {
            case suspended_no_error:
                WorkflowStepCompleter.stepSuspendedNoError(getOpId());
                break;
            case suspended_error:
                WorkflowStepCompleter.stepSuspendedError(getOpId(), coded);
                break;
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
     * @param context
     *            context information
     */
    public void updateWorkflowStepContext(Object context) {
        WorkflowService.getInstance().storeStepData(getOpId(), context);
        _logger.info("Storing rollback context to op: " + getOpId() + " context: " + context.toString());
    }

    /**
     * Set the error status of the dataObject referred by the uri
     * 
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param uri
     *            [in] - URI of clazz
     * @param coded
     *            [in] - ServiceCoded containing error message reference
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
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param dObject
     *            [in] - DataObject of clazz
     * @param coded
     *            [in] - ServiceCoded containing error message reference
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
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param uri
     *            [in] - URI of clazz
     */
    protected void setReadyOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, URI uri) {
        if (!NullColumnValueGetter.isNullURI(uri)) {
            dbClient.ready(clazz, uri, getOpId());
        }
    }

    /**
     * Set the ready status on the dataObject
     * 
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param dObject
     *            [in] - DataObject of clazz
     */
    protected void setReadyOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, DataObject dObject) {
        if (dObject != null) {
            dbClient.ready(clazz, dObject.getId(), getOpId());
        }
    }

    /**
     * Set the suspended error status of the dataObject referred by the uri
     * 
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param uri
     *            [in] - URI of clazz
     */
    protected void setSuspendedErrorOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, URI uri, ServiceCoded coded) {
        if (!NullColumnValueGetter.isNullURI(uri)) {
            dbClient.suspended_error(clazz, uri, getOpId(), coded);
        }
    }

    /**
     * Set the suspended error status on the dataObject
     * 
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param dObject
     *            [in] - DataObject of clazz
     */
    protected void setSuspendedErrorOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, DataObject dObject, ServiceCoded coded) {
        if (dObject != null) {
            dbClient.suspended_error(clazz, dObject.getId(), getOpId(), coded);
        }
    }

    /**
     * Set the suspended no-error status of the dataObject referred by the uri
     * 
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param uri
     *            [in] - URI of clazz
     */
    protected void setSuspendedNoErrorOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, URI uri) {
        if (!NullColumnValueGetter.isNullURI(uri)) {
            dbClient.suspended_no_error(clazz, uri, getOpId());
        }
    }

    /**
     * Set the suspended no-error status on the dataObject
     * 
     * @param dbClient
     *            [in] - Database Client
     * @param clazz
     *            [in] - Class in DataObject hierarchy
     * @param dObject
     *            [in] - DataObject of clazz
     */
    protected void setSuspendedNoErrorOnDataObject(DbClient dbClient, Class<? extends DataObject> clazz, DataObject dObject) {
        if (dObject != null) {
            dbClient.suspended_no_error(clazz, dObject.getId(), getOpId());
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
                case suspended_error:
                    setSuspendedErrorOnDataObject(dbClient, BlockConsistencyGroup.class, consistencyGroupId, coded);
                    break;
                case suspended_no_error:
                    setSuspendedNoErrorOnDataObject(dbClient, BlockConsistencyGroup.class, consistencyGroupId);
                    break;
            }
        }
    }

    /**
     * clears all tasks by querying all tasks with the task id
     */
    private void clearAllTasks(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        if (_opId != null) {
            List<Task> tasksForTaskId = TaskUtils.findTasksForRequestId(dbClient, _opId);
            Map<Class<? extends DataObject>, List<URI>> resourceMap = new HashMap<Class<? extends DataObject>, List<URI>>();
            for (Task task : tasksForTaskId) {
                if (!task.getCompletedFlag()) {
                    URI resourceId = task.getResource().getURI();
                    Class<? extends DataObject> resourceType = URIUtil.getModelClass(resourceId);
                    if (resourceMap.get(resourceType) == null) {
                        resourceMap.put(resourceType, new ArrayList<URI>());
                    }
                    resourceMap.get(resourceType).add(resourceId);
                }
            }

            for (Entry<Class<? extends DataObject>, List<URI>> entry : resourceMap.entrySet()) {
                // if error, ready
                setStatus(entry.getKey(), entry.getValue(), dbClient, status, serviceCoded != null ? serviceCoded
                        : DeviceControllerException.errors.unforeseen(), null);
            }
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("_clazz", _clazz)
                .add("_opId", _opId)
                .add("_ids", _ids)
                .add("_consistencyGroupIds", _consistencyGroupIds)
                .add("_volumeGroupIds", _volumeGroupIds)
                .add("notifyWorkflow", notifyWorkflow)
                .add("asynchronous", asynchronous)
                .add("completed", completed)
                .toString();
    }
}
