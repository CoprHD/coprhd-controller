/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.workflow;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.DistributedOwnerLockService;
import com.emc.storageos.locking.LockRetryException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.Dispatcher;
import com.emc.storageos.workflow.Workflow.Step;
import com.emc.storageos.workflow.Workflow.StepState;
import com.emc.storageos.workflow.Workflow.StepStatus;

/**
 * A singleton WorkflowService is created on each Bourne node to manage Workflows.
 * It has these functions:
 * 1. Managing zookeeper persistence
 * 2. Receiving status update messages sent by the WorkflowStepCompleter and updating
 * the appropraite Step states (unLblocking other Steps if necessary).
 * 3. Initiating rollback (including creating rollback steps and reversing the
 * dependency graph)
 * 4. Logging state changes to Cassandra
 * 
 * @author Watson
 */
public class WorkflowService implements WorkflowController {
    private static final Logger _log = LoggerFactory.getLogger(WorkflowService.class);
    private static final Long MILLISECONDS_IN_SECOND = 1000L;
    private static volatile WorkflowService _instance = null;
    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    private DistributedDataManager _dataManager;
    private Dispatcher _dispatcher;
    private ControllerLockingService _locker;
    private DistributedOwnerLockService _ownerLocker;
    private WorkflowScrubberExecutor _scrubber;

    // Config properties
    private final String WORKFLOW_SUSPEND_ON_ERROR_PROPERTY = "workflow_suspend_on_error";
    private final String WORKFLOW_SUSPEND_ON_CLASS_METHOD_PROPERTY = "workflow_suspend_on_class_method";

    // Zookeeper paths, all proceeded by /workflow which is ZkPath.WORKFLOW
    private String _zkWorkflowPath = ZkPath.WORKFLOW.toString() + "/workflows/%s/%s/%s";
    private String _zkWorkflowData = "/data/%s";
    private String _zkStepDataPath = ZkPath.WORKFLOW.toString() + "/stepdata/%s";
    private String _zkStepToWorkflowPath = ZkPath.WORKFLOW.toString() + "/step2workflow/%s";
    private String _zkStepToWorkflow = ZkPath.WORKFLOW.toString() + "/step2workflow";

    // Test-provided suspend variables that override system variables during unit testing.
    private String _suspendClassMethodTestOnly = null;
    private Boolean _suspendOnErrorTestOnly = null;

    /**
     * Returns the ZK path for workflow state. This node has a child for each Step.
     * 
     * @param workflow
     * @return
     */
    private String getZKWorkflowPath(Workflow workflow) {
        String path = String.format(_zkWorkflowPath, workflow._orchControllerName,
                workflow._orchMethod, workflow._workflowURI);
        return path;
    }

    /**
     * Returns the ZK path for a step state. The parent node represents a Workflow.
     * 
     * @param workflow
     * @param step
     * @return
     */
    private String getZKStepPath(Workflow workflow, Step step) {
        String path = getZKWorkflowPath(workflow);
        path = path + "/" + step.stepId;
        return path;
    }

    /**
     * Returns the path of a Step to Workflow path node
     * 
     * @param stepId
     * @return
     */
    private String getZKStep2WorkflowPath(String stepId) {
        String path = String.format(_zkStepToWorkflowPath, stepId);
        return path;
    }

    /**
     * Returns the path to a data space per step.
     * 
     * @param step
     * @return
     */
    private String getZKStepDataPath(String step) {
        String path = String.format(_zkStepDataPath, step);
        return path;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this._dispatcher = dispatcher;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this._coordinator = coordinator;
    }

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        this._locker = locker;
    }

    /**
     * Start the service.
     */
    public void start() {
        _log.info("WorkflowService starting up");
        _instance = this;
        try {
            _dataManager = _coordinator.getWorkflowDataManager();
            _dataManager.setConnectionStateListener(_connectionStateListener);
        } catch (Exception ex) {
            _log.error("Can't get a DistributedDataManager", ex);
        }
        if (!scrubberStarted) {
            getScrubber().start();
            scrubberStarted = true;
        }
    }

    private static boolean scrubberStarted = false;

    /**
     * Stop the service.
     */
    public void stop() {
        try {
            _dataManager.setListener(null);
            _dataManager.setConnectionStateListener(null);
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
        }
    }

    /**
     * Log connection states in case they cause trouble with locking.
     */
    private final ConnectionStateListener _connectionStateListener = new ConnectionStateListener() {
        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            switch (newState) {
                default:
                    _log.info("ZK connection: " + newState.name());
                    break;
            }
        }
    };

    private class CancelledException extends Exception {
    }

    /**
     * Return the singleton instance for the Workflow Service.
     * 
     * @return
     */
    public static WorkflowService getInstance() {
        return _instance;
    }

    /**
     * Given a stepId, find the main workflow of the step and return its URI. If the
     * step is in a nested workflow, this function will recursively look for the
     * parent workflow until the main workflow is found.
     * 
     * @param stepId
     *            -- the step Id
     * @return the main workflow URI is in String form.
     */
    private String getMainWorkflowUri(String stepId) {
        String workflowPath = null;
        Workflow workflow = null;
        String uri = null;
        // find the path in step2workflow of this step
        String step2WorkflowPath = getZKStep2WorkflowPath(stepId);
        try {
            while (_dataManager.checkExists(step2WorkflowPath) != null) {
                // get the step workflow path
                workflowPath = (String) _dataManager.getData(step2WorkflowPath, false);
                // load the workflow
                workflow = (Workflow) _dataManager.getData(workflowPath, false);
                uri = workflow.getWorkflowURI().toString();
                // if the workflow is nested, then it is a step in another workflow
                if (workflow._nested) {
                    // get the path in step2workflow of the step corresponding to the
                    // nested workflow and recurse
                    step2WorkflowPath = getZKStep2WorkflowPath(workflow.getOrchTaskId());
                } else {
                    // this is a main workflow, end the recursion
                    break;
                }
            }
        } catch (Exception ex) {
            _log.error("Can't get main workflow for stepId: " + stepId, ex);
            uri = null;
        }
        return uri;
    }

    /**
     * Saves data in the workflow to be used by other steps. This allows steps
     * to store data for use by other steps. The data is stored under
     * /workflow/stepdata/{workflowURI}/data/{key} where workflowURI is the URI
     * of the main workflow regardless of whether the step belongs in the main
     * workflow or one of its nested workflows.
     * <p>
     * Additional enhancements of this function are to allow the caller to specify what to do if data already exists
     * (override or fail) or if an exception should be ignored or propagated.
     * 
     * @param stepId
     *            -- The step identifier of one of the workflow steps or one
     *            of its nested workflow steps.
     * @param key
     *            -- the key under which the data is stored
     * @param data
     *            -- A Java Serializable object.
     */
    public void storeWorkflowData(String stepId, String key, Object data) {
        String workflowUri = getMainWorkflowUri(stepId);
        try {
            if (workflowUri == null) {
                return;
            }
            String dataPath = String.format(_zkStepDataPath, workflowUri) + String.format(_zkWorkflowData, key);
            _dataManager.putData(dataPath, data);
        } catch (Exception ex) {
            // so far this is used to improve performance by caching data, if this fails do not fail the call
            String exMsg = "Exception adding global data to workflow from stepId: " + stepId + ": " + ex.getMessage();
            _log.error(exMsg);
        }
    }

    /**
     * Gets the step workflow data stored under /workflow/stepdata/{workflowURI}/data/{key}
     * where workflowURI is the URI of the main workflow regardless of whether the
     * step belongs in the main workflow or one of its nested workflows.
     * 
     * @param stepId
     *            -- The step identifier.
     * @param key
     *            -- the key under which the data is stored
     * @return -- A Java serializable object.
     */
    public Object loadWorkflowData(String stepId, String key) {
        Object data = null;
        String workflowUri = getMainWorkflowUri(stepId);
        try {
            // do not fail, this is a best effort
            if (workflowUri != null) {
                String dataPath = String.format(_zkStepDataPath, workflowUri) + String.format(_zkWorkflowData, key);
                if (_dataManager.checkExists(dataPath) != null) {
                    data = _dataManager.getData(dataPath, false);
                }
            }
        } catch (Exception ex) {
            // so far this is used to improve performance by caching data, if this fails do not fail the call
            String exMsg = "Exception adding global data to workflow from stepId: " + stepId + ": " + ex.getMessage();
            _log.error(exMsg);
            data = null;
        }
        return data;
    }

    /**
     * Saves data on behalf of a step.
     * 
     * @param stepId
     *            -- The step identifier.
     * @param data
     *            -- A Java Serializable object.
     */
    public void storeStepData(String stepId, Object data) {
        String path = getZKStepDataPath(stepId);
        try {
            _dataManager.putData(path, data);
        } catch (Exception ex) {
            _log.error("Can't save step state for path: " + path, ex);
        }
    }

    /**
     * Retrieve step data for a class.
     * 
     * @param stepId
     *            -- The step identifier.
     * @return -- A Java serializable object.
     * @throws Exception
     */
    public Object loadStepData(String stepId) {
        String path = getZKStepDataPath(stepId);
        try {
            Object data = _dataManager.getData(path, false);
            return data;
        } catch (Exception ex) {
            _log.error("Can't load step state for path: " + path);
            return null;
        }
    }

    @Deprecated
    public static void completerUpdateStep(String stepId,
            StepState state, String message) throws WorkflowException {
        // _instance.completerCallback(stepId, state, message);
        _instance.updateStepStatus(stepId, state, state.getServiceCode(), message);
    }

    /**
     * See {@link #updateStepStatus(String, StepState, ServiceCode, String, boolean)} . Do automatic rollback in case of
     * workflow error
     * 
     * @param stepId
     * @param state
     * @param code
     * @param message
     * @throws WorkflowException
     */
    private void updateStepStatus(String stepId, StepState state, ServiceCode code, String message) throws WorkflowException {
        updateStepStatus(stepId, state, code, message, true);
    }

    /**
     * Given a ZK path to a Callback node, get the data which is a StatusUpdateMessage
     * and update the appropriate step status.
     * 
     * @param stepId
     *            -- The Step Id of the step.
     * @param state
     * @param code
     * @param message
     * @param automaticRollback
     *            whether to rollback in case of error at the end of workflow
     * @throws WorkflowException
     * 
     */
    private void updateStepStatus(String stepId, StepState state, ServiceCode code, String message, boolean automaticRollback)
            throws WorkflowException {
        // String path = getZKCallbackPath(stepId);
        String workflowPath = getZKStep2WorkflowPath(stepId);
        Workflow workflow = null;
        boolean workflowDeleted = false;
        InterProcessLock lock = null;
        try {
            // Get the workflow path from ZK
            workflowPath = (String) _dataManager.getData(workflowPath, false);
            // It is not an error to try and update using a non-existent stepId
            if (workflowPath == null) {
                return;
            }
            // Load the Workflow state from ZK
            workflow = (Workflow) _dataManager.getData(workflowPath, false);
            if (workflow == null) {
                throw WorkflowException.exceptions.workflowNotFound(workflowPath);
            }
            // Lock the Workflow
            lock = lockWorkflow(workflow);
            // Load the entire workflow state including the steps
            workflow = loadWorkflow(workflow);
            if (workflow == null) {
                throw WorkflowException.exceptions.workflowNotFound(workflowPath);
            }
            synchronized (workflow) {
                // Update the StepState structure
                StepStatus status = workflow.getStepStatus(stepId);

                // If an error is reported, and we're supposed to suspend on error, suspend
                // Do not suspend rollback steps.
                Step step = workflow.getStepMap().get(stepId);
                if (StepState.ERROR == state && workflow.isSuspendOnError() && !workflow.isRollbackState()) {
                    state = StepState.SUSPENDED_ERROR;
                    step.suspendStep = false;
                }

                // if this is a rollback step that ran as a result of a SUSPENDED_ERROR step, move over the
                // SUSPENDED_ERROR step to ERROR
                if (step.isRollbackStep()) {
                    if (step.foundingStepId != null) {
                        if (workflow.getStepMap().get(step.foundingStepId) != null) {
                            Step foundingStep = workflow.getStepMap().get(step.foundingStepId);
                            StepStatus foundingStatus = workflow.getStepStatus(step.foundingStepId);
                            if (StepState.SUSPENDED_ERROR.equals(foundingStatus.state)) {
                                foundingStatus.updateState(StepState.ERROR, code, message);
                                persistWorkflowStepUpdate(workflow, foundingStep);
                            }
                        }
                    }
                }

                _log.info(String.format("Updating workflow step: %s state %s : %s", stepId, state, message));
                status.updateState(state, code, message);
                // Persist the updated step state
                persistWorkflowStepUpdate(workflow, step);
                if (status.isTerminalState()) {
                    // release any step level locks held.
                    boolean releasedLocks = _ownerLocker.releaseLocks(stepId);
                    if (!releasedLocks) {
                        _log.info("Unable to release StepLocks for step: " + stepId);
                    }
                    // Check for any blocked steps and unblock them
                    checkBlockedSteps(workflow, stepId);
                }

                // Check to see if the workflow might be finished, or need a rollback.
                if (workflow.allStatesTerminal()) {
                    workflowDeleted = doWorkflowEndProcessing(workflow, automaticRollback, lock);
                    if (workflowDeleted) {
                        // lock is released by end processing if the workflow is deleted
                        lock = null;
                    }
                }
            }
        } catch (Exception ex) {
            String exMsg = "Exception processing updateStepStatus stepId: " + stepId + ": " + ex.getMessage();
            _log.error(exMsg);
            throw new WorkflowException(exMsg, ex);
        } finally {
            unlockWorkflow(workflow, lock);
            if (workflowDeleted) {
                deleteWorkflowLock(workflow);
            }
        }
    }

    /**
     * End of Workflow processing that used to be in WorkflowExecutor.
     * Initiates rollback if necessary, does final task completer.
     * 
     * @param workflow
     * @param automaticRollback
     * @param workflowLock
     *            -- released only if workflow is deleted
     * @return deleted
     * @throws DeviceControllerException
     */
    private boolean doWorkflowEndProcessing(Workflow workflow, boolean automaticRollback,
            InterProcessLock workflowLock) throws DeviceControllerException {
        Map<String, StepStatus> statusMap = workflow.getStepStatusMap();

        // Print out the status of each step into the log.
        printStepStatuses(statusMap.values());
        // Get the WorkflowState
        WorkflowState state = workflow.getWorkflowStateFromSteps();

        // Clear the suspend step so we will execute if resumed.
        if (statusMap != null && workflow.getSuspendSteps() != null) {
            for (Map.Entry<String, StepStatus> statusEntry : statusMap.entrySet()) {
                if (statusEntry.getValue() != null && statusEntry.getValue().state != null &&
                        (statusEntry.getValue().state == StepState.SUSPENDED_ERROR ||
                                statusEntry.getValue().state == StepState.SUSPENDED_NO_ERROR)) {
                    _log.info("Removing step " + statusEntry.getValue().description + " from the suspended steps list in workflow "
                            + workflow._workflowURI.toString());
                    URI suspendStepURI = workflow.getStepMap().get(statusEntry.getKey()).workflowStepURI;
                    workflow.getSuspendSteps().remove(suspendStepURI);
                    persistWorkflow(workflow);
                }
            }
        }

        // Get composite status and status message
        if (workflow._successMessage == null) {
            workflow._successMessage = String.format(
                    "Operation %s for task %s completed successfully",
                    workflow._orchMethod,
                    workflow._orchTaskId);
        }
        String[] errorMessage = new String[] { workflow._successMessage };
        _log.info(String.format("Workflow %s overall state: %s (%s)",
                workflow.getOrchTaskId(), state, errorMessage[0]));
        ServiceError error = Workflow.getOverallServiceError(statusMap);

        // Initiate rollback if needed.
        if (automaticRollback && workflow.isRollbackState() == false &&
                (state == WorkflowState.ERROR || state == WorkflowState.SUSPENDED_ERROR)) {
            boolean rollBackStarted = false;
            if (workflow.isSuspendOnError()) {
                _log.info(String.format("Suspending workflow %s on error, no rollback initiation", workflow.getWorkflowURI()));
            } else {
                rollBackStarted = initiateRollback(workflow);
            }
            if (rollBackStarted) {
                // Return now, wait until the rollback completions come here again.
                workflow.setWorkflowState(WorkflowState.ROLLING_BACK);
                persistWorkflow(workflow);
                logWorkflow(workflow, true);
                _log.info(String.format("Rollback initiated workflow %s", workflow.getWorkflowURI()));
                return false;
            } else {
                // Enter the suspend state on error
                state = WorkflowState.SUSPENDED_ERROR;
            }
        }
        // Save the updated workflow state
        workflow.setWorkflowState(state);
        persistWorkflow(workflow);
        logWorkflow(workflow, true);

        try {
            // Check if rollback completed.
            if (workflow.isRollbackState()) {
                if (workflow._rollbackHandler != null) {
                    workflow._rollbackHandler.rollbackComplete(workflow,
                            workflow._rollbackHandlerArgs);
                }
            }

            // Check for workflow completer callback.
            if (workflow._callbackHandler != null) {
                workflow._callbackHandler.workflowComplete(workflow,
                        workflow._callbackHandlerArgs);
            }

            // Throw task completer if supplied.
            if (workflow._taskCompleter != null) {
                switch (state) {
                    case ERROR:
                        workflow._taskCompleter.error(_dbClient, _locker, error);
                        break;
                    case SUCCESS:
                        workflow._taskCompleter.ready(_dbClient, _locker);
                        break;
                    case SUSPENDED_ERROR:
                        workflow._taskCompleter.suspendedError(_dbClient, _locker, error);
                        break;
                    case SUSPENDED_NO_ERROR:
                        workflow._taskCompleter.suspendedNoError(_dbClient, _locker);
                        break;
                    default:
                        break;
                }
            }
        } finally {
            logWorkflow(workflow, true);
            // Release the Workflow's locks, if any.
            boolean removed = _ownerLocker.releaseLocks(workflow.getWorkflowURI().toString());
            if (!removed) {
                _log.error("Unable to release workflow locks for: " + workflow.getWorkflowURI().toString());
            }
            // Remove the workflow from ZK unless it is suspended (either for an error, or no error)
            if (workflow.getWorkflowState() != WorkflowState.SUSPENDED_ERROR
                    && workflow.getWorkflowState() != WorkflowState.SUSPENDED_NO_ERROR) {
                removed = false;
                if (!workflow._nested) {
                    // Remove the workflow from ZK unless it is suspended (either for an error, or no error)
                    if (workflow.getWorkflowState() != WorkflowState.SUSPENDED_ERROR
                            && workflow.getWorkflowState() != WorkflowState.SUSPENDED_NO_ERROR) {
                        unlockWorkflow(workflow, workflowLock);
                        destroyWorkflow(workflow);
                        return true;
                    }
                } else {
                    if (isExistingWorkflow(workflow)) {
                        _log.info(String.format(
                                "Workflow %s is nested, destruction deferred until parent destroys",
                                workflow.getWorkflowURI()));
                    }
                    logWorkflow(workflow, true);
                }
            }
        }
        return false;
    }

    /**
     * Get a new workflow that is associated with a taskId.
     * 
     * @param controller
     *            -- Orchestration controller.
     * @param method
     *            -- Orchestration method.
     * @param rollbackContOnError
     *            - Keep rolling back even if there's a rollback error
     * @param taskId
     *            -- Orchestration taskId from API service.
     * @return Workflow
     */
    public Workflow getNewWorkflow(Controller controller, String method, Boolean rollbackContOnError, String taskId) {
        return getNewWorkflow(controller, method, rollbackContOnError, taskId, null);
    }

    /**
     * Get a new workflow that is associated with a taskId.
     * 
     * @param controller
     *            -- Orchestration controller.
     * @param method
     *            -- Orchestration method.
     * @param rollbackContOnError
     *            - Keep rolling back even if there's a rollback error
     * @param taskId
     *            -- Orchestration taskId from API service.
     * @param workflowURI
     *            -- If non-null, will use the passed UIR parameter for the workflowURI
     * @return Workflow
     */
    public Workflow getNewWorkflow(Controller controller, String method, Boolean rollbackContOnError, String taskId, URI workflowURI) {
        Workflow workflow = new Workflow(this, controller.getClass().getSimpleName(),
                method, taskId, workflowURI);
        workflow.setRollbackContOnError(rollbackContOnError);
        workflow.setSuspendOnError(_suspendOnErrorTestOnly != null ? _suspendOnErrorTestOnly : Boolean.valueOf(ControllerUtils
                .getPropertyValueFromCoordinator(_coordinator, WORKFLOW_SUSPEND_ON_ERROR_PROPERTY)));
        // logWorkflow assigns the workflowURI.
        logWorkflow(workflow, false);
        // Keep track if it's a nested Workflow
        workflow._nested = associateToParentWorkflow(workflow);
        return workflow;
    }

    /**
     * Remove workflow from Zookeeper if necessary.
     * 
     * @param workflow
     */
    public void destroyWorkflow(Workflow workflow) {
        String id = workflow.getOrchTaskId();
        try {
            destroyNestedWorkflows(workflow);
            // Remove all the Step data nodes.
            for (Step step : workflow.getStepMap().values()) {
                // Remove the back pointer from step id to workflow.
                String dataPath = getZKStep2WorkflowPath(step.stepId);
                Stat stat = _dataManager.checkExists(dataPath);
                if (stat != null) {
                    _dataManager.removeNode(dataPath);
                }
                // Remove the step state.
                dataPath = getZKStepDataPath(step.stepId);
                stat = _dataManager.checkExists(dataPath);
                if (stat != null) {
                    _dataManager.removeNode(dataPath);
                }
            }
            // Destroy workflow data under /workflow/stepdata/{workflowId} directory
            String workflowDataPath = String.format(_zkStepDataPath, workflow.getWorkflowURI().toString());
            _dataManager.removeNode(workflowDataPath, true);

            // Destroy the workflow under /workflow/workflows
            String path = getZKWorkflowPath(workflow);
            Stat stat = _dataManager.checkExists(path);
            if (stat != null) {
                _dataManager.removeNode(path);
                _log.info("Removed ZK workflow: " + workflow.getWorkflowURI());
            }
        } catch (Exception ex) {
            _log.error("Cannot destroy Workflow: " + id);
        }
    }

    /**
     * Destroy any nested workflows a parent might have. (recursive)
     * 
     * @param parent
     *            Workflow
     */
    private void destroyNestedWorkflows(Workflow parent) {
        Set<URI> childWorkflowSet = parent._childWorkflows;
        if (childWorkflowSet == null || childWorkflowSet.isEmpty()) {
            return;
        }
        _log.info("Destroying child workflows: " + childWorkflowSet.toString());
        for (URI childWorkflowURI : childWorkflowSet) {
            Workflow childWorkflow = loadWorkflowFromUri(childWorkflowURI);
            if (childWorkflow != null) {
                if (childWorkflow.allStatesTerminal()) {
                    destroyWorkflow(childWorkflow);
                } else {
                    // Not all states terminal, even though parent is being destroyed. Very odd.
                    _log.warn(String.format(
                            "Child workflow %s still executing but parent %s being destroyed; may need to be manually removed from ZK",
                            childWorkflow.getWorkflowURI(), parent.getWorkflowURI()));
                }
            }
        }
    }

    /**
     * Update the Step State in ZK. No more updates are done after the path is deleted.
     * 
     * @param workflow
     * @param step
     * @throws WorkflowException
     */
    private void persistWorkflowStepUpdate(Workflow workflow, Step step)
            throws WorkflowException {
        try {
            logStep(workflow, step);
            String path = getZKStepPath(workflow, step);
            Stat stat = _dataManager.checkExists(path);
            if (stat != null) {
                _dataManager.putData(path, step);
                _log.debug("Updated step status: " + step.stepId);
            }
        } catch (Exception ex) {
            throw new WorkflowException("Cannot update step: " + step.stepId, ex);
        }
    }

    /**
     * Save a Workflow Step for the first time in Zookeeper. This happens when queueStep()
     * is called.
     * 
     * @param workflow
     * @param step
     * @throws WorkflowException
     */
    private void persistWorkflowStep(Workflow workflow, Step step)
            throws WorkflowException {
        try {
            logStep(workflow, step);
            // Make sure the workflow path exists.
            String workflowPath = getZKWorkflowPath(workflow);
            Stat stat = _dataManager.checkExists(workflowPath);
            if (stat == null) {
                _dataManager.createNode(workflowPath, false);
            }
            // Save the step state.
            String path = getZKStepPath(workflow, step);
            _dataManager.putData(path, step);
            stat = _dataManager.checkExists(path);
            _log.debug("Created path " + path + " bytes " + stat.getDataLength());
            // Make a stepToWorkflowPath node
            path = getZKStep2WorkflowPath(step.stepId);
            _dataManager.putData(path, workflowPath);
            _log.debug("Created step path: " + path);
        } catch (Exception ex) {
            throw new WorkflowException("Cannot persist step in ZK", ex);
        }
    }

    /**
     * Returns false if this workflow doesn't exist.
     * 
     * @param workflow
     * @return true if workflow exists, false if could not locate workflow
     */
    private boolean isExistingWorkflow(Workflow workflow) {
        try {
            String path = getZKWorkflowPath(workflow);
            // If there us a ZK node for the Workflow, it exists.
            if (_dataManager.checkExists(path) != null) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * This method sets up the workflow from ZK data if the workflow already exists.
     * The state for each of the Steps is loaded from ZK.
     * This is called from updateStepStatus().
     * 
     * @param workflow
     *            , or null if nothing could be loaded from ZK
     */
    private Workflow loadWorkflow(Workflow workflow) throws WorkflowException {
        try {
            if (!isExistingWorkflow(workflow)) {
                return workflow;
            }
            String path = getZKWorkflowPath(workflow);
            // WorkflowPersisted persisted = (WorkflowPersisted) _dataManager.getData(path, false);
            // workflow = persisted.getWorkflow(this, _dispatcher);
            workflow = (Workflow) _dataManager.getData(path, false);
            workflow._stepMap = new HashMap<String, Step>();
            workflow._stepStatusMap = new HashMap<String, StepStatus>();
            workflow._service = this;
            // Load all the step states.
            List<String> children = _dataManager.getChildren(path);
            for (String child : children) {
                String childPath = path + "/" + child;
                Object stepObj = _dataManager.getData(childPath, false);
                if (stepObj == null || false == (stepObj instanceof Step)) {
                    continue;
                }
                Step step = (Step) stepObj;
                workflow.getStepMap().put(step.stepId, step);
                if (step.stepGroup != null) {
                    if (workflow.getStepGroupMap().get(step.stepGroup) == null) {
                        workflow.getStepGroupMap().put(step.stepGroup,
                                new HashSet<String>());
                    }
                    workflow.getStepGroupMap().get(step.stepGroup).add(step.stepId);
                }
                StepStatus status = step.status;
                workflow._stepStatusMap.put(step.stepId, status);
                _log.debug(String.format(
                        "Loaded step %s state %s for workflow %s",
                        step.stepId, step.status.state, workflow._orchTaskId));
            }
            return workflow;
        } catch (Exception ex) {
            _log.error("Unable to load workflow: " + workflow._orchTaskId);
            throw new WorkflowException("Unable to restart workflow: "
                    + workflow._orchTaskId, ex);
        }
    }

    /**
     * Persits the workflow to Zookeeper.
     * 
     * @param workflow
     * @throws WorkflowException
     */
    public void persistWorkflow(Workflow workflow) throws WorkflowException {
        try {
            String path = getZKWorkflowPath(workflow);
            _dataManager.putData(path, workflow);
        } catch (Exception ex) {
            throw new WorkflowException("Cannot persist workflow data in ZK", ex);
        }
    }

    /**
     * Execute the workflow. It is saved here and control is passed to WorkflowExecutor.
     * 
     * @param workflow
     */
    public void executePlan(Workflow workflow) throws WorkflowException {
        InterProcessLock lock = null;
        try {
            if (!workflow.getStepMap().isEmpty()) {
                _log.info("Executing workflow plan: " + workflow.getWorkflowURI() + " " + workflow.getOrchTaskId());
                workflow.setWorkflowState(WorkflowState.RUNNING);

                // Mark steps that should be suspended in the workflow for later.
                suspendStepsMatchingProperty(workflow);

                persistWorkflow(workflow);

                for (Step step : workflow.getStepMap().values()) {
                    persistWorkflowStep(workflow, step);
                }

                // Check suspended state and modify states
                if (checkSuspendedSteps(workflow)) {
                    _log.info("Workflow is suspended: " + workflow.getWorkflowURI());
                    // release any workflow locks
                    for (Step step : workflow.getStepMap().values()) {
                        if (step.status.state == StepState.SUSPENDED_NO_ERROR) {
                            completerStepSuspendedNoError(step.stepId);
                        }
                    }
                } else {
                    /**
                     * Lock the workflow.
                     */
                    lock = lockWorkflow(workflow);

                    /**
                     * Queue any steps that have not been queued.
                     */
                    for (Step step : workflow.getStepMap().values()) {
                        if (step.status.state == StepState.CREATED) {
                            queueWorkflowStep(workflow, step);
                        }
                    }
                }
            } else {
                _log.info("Workflow executed with no steps: " + workflow.getWorkflowURI());
                // release any workflow locks
                releaseAllWorkflowLocks(workflow);
                // If no steps are to process, then just exit properly
                if (workflow._taskCompleter != null) {
                    workflow._taskCompleter.ready(_dbClient);
                }
            }

        } finally {
            unlockWorkflow(workflow, lock);
        }
    }

    /**
     * Marks steps as suspend-able when they are encountered and unblocked.
     * workflow_suspend_on_class_method, such as "MaskingWorkflowEntryPoints.doExportGroupAddVolumes"
     * 
     * @param workflow
     *            workflow to scan
     */
    private void suspendStepsMatchingProperty(Workflow workflow) {
        // Load the current workflow property to suspend on class/method
        String suspendOn = _coordinator.getPropertyInfo().getProperty(WORKFLOW_SUSPEND_ON_CLASS_METHOD_PROPERTY);

        // If unit testing, get this value from the unit tester.
        if (_suspendClassMethodTestOnly != null) {
            suspendOn = _suspendClassMethodTestOnly;
        }

        String suspendClass = null;
        String suspendMethod = null;
        if (suspendOn != null && !suspendOn.trim().isEmpty()) {
            _log.info("suspend on class/method is SET to: " + suspendOn);
            if (suspendOn.contains(".")) {
                suspendClass = suspendOn.substring(0, suspendOn.indexOf("."));
                suspendMethod = suspendOn.substring(suspendOn.indexOf(".") + 1);
            } else {
                suspendClass = suspendOn;
                suspendMethod = "*";
            }

            // Scan all steps for class and methods that should be set to the suspended state.
            for (Step step : workflow.getStepMap().values()) {
                boolean suspendStep = false;

                // If suspend class and method are true, everything suspends all of the time.
                if (suspendClass.equals("*") && suspendMethod.equals("*")) {
                    suspendStep = true;
                } else if (step.controllerName.endsWith(suspendClass) &&
                        (step.executeMethod.methodName.equals(suspendMethod) ||
                                suspendMethod.equals("*"))) {
                    suspendStep = true;
                } else if (suspendClass.equals("*") && step.executeMethod.methodName.equals(suspendMethod)) {
                    suspendStep = true;
                }

                if (suspendStep) {
                    logStep(workflow, step);
                    if (workflow.getSuspendSteps() == null) {
                        workflow.setSuspendSteps(new HashSet<URI>());
                    }
                    _log.info("Adding step " + step.description + " to workflow list of steps to suspend: "
                            + workflow._workflowURI.toString());
                    workflow.getSuspendSteps().add(step.workflowStepURI);
                }
            }
        }
    }

    /**
     * Queue the step on the Dispatcher to execute.
     * 
     * @param workflow
     *            -- The Workflow containing this step
     * @param step
     *            -- Step step to be queued for execution
     */
    public void queueWorkflowStep(Workflow workflow, Step step)
            throws WorkflowException {
        synchronized (workflow) {
            StepState state = StepState.QUEUED; // default is to go into QUEUED state
            try {
                if (isBlocked(workflow, step)) {
                    // We are blocked waiting on a prerequisite step
                    state = StepState.BLOCKED;
                } else if (isStepMarkedForSuspend(workflow, step)) {
                    state = StepState.SUSPENDED_NO_ERROR;
                    step.suspendStep = false;
                }
            } catch (CancelledException cancelEx) {
                state = StepState.CANCELLED;
            }

            // Persist the Workflow and the Steps in Zookeeper
            workflow.getStepStatus(step.stepId).updateState(state, null, "");
            persistWorkflowStep(workflow, step);
            _log.info(String.format("%s step: %s queued state %s", step.description,
                    step.stepId, state));

            // If step is suspended, call the update status to initiate other steps to be cancelled
            if (state == StepState.SUSPENDED_NO_ERROR) {
                // A suspended step doesn't actually run, so call the update status here.
                completerStepSuspendedNoError(step.stepId);
            }

            // If step is ready to run, send it to the Dispatcher.
            if (state == StepState.QUEUED) {
                dispatchStep(step, workflow._nested);
            }
        }
    }

    /**
     * Send a step to the Dispatcher for execution. Must be in the QUEUED state.
     * 
     * @param step
     *            Step to be dispatched.
     * @param isNested
     *            True if this Workflow is nested within another workflow
     * @throws WorkflowException
     */
    private void dispatchStep(Step step, boolean isNested) throws WorkflowException {
        assert (step.status.state == StepState.QUEUED);
        // The stepId is automatically added as the last argument to the step.
        List<Object> argList = new ArrayList<Object>(
                Arrays.asList(step.executeMethod.args));
        argList.add(step.stepId);

        // Look up the controller
        Controller controller = _dispatcher.getControllerMap().get(step.controllerName);
        if (controller == null) {
            throw new WorkflowException("Cannot locate controller for: "
                    + step.controllerName);
        }
        // Queue the step for via the dispatcher. If nested we use a different Dispatcher queue
        // than if we're the top-level Workflow.
        try {
            _dispatcher.queue(
                    (isNested ? Dispatcher.QueueName.workflow_inner : Dispatcher.QueueName.workflow_outer),
                    step.deviceURI, step.deviceType, step.lockDevice, controller,
                    step.executeMethod.methodName, argList.toArray());
        } catch (InternalException ex) {
            throw new WorkflowException(String.format(
                    "Cannot queue step %s for controller %s method %s",
                    step.stepId, step.controllerName, step.executeMethod.methodName), ex);
        }
    }

    /**
     * Checks the workflow for any BLOCKED steps that have become unblocked,
     * and dispatches them or cancels them if necessary.
     * 
     * @param workflow
     *            -- The Workflow to be checked.
     * @param fromStepId
     *            -- The Step that has changed state.
     */
    private void checkBlockedSteps(Workflow workflow, String fromStepId) {
        boolean again;
        Set<String> suspendedSteps = new HashSet<String>();
        do {
            again = false; // only loop again if made change
            for (Step step : workflow.getStepMap().values()) {
                if (step.status.state != StepState.BLOCKED) {
                    continue;
                }
                try {
                    try {
                        if (!isBlocked(workflow, step)) {
                            again = true;
                            if (isStepMarkedForSuspend(workflow, step)) {
                                changeStepToSuspendedNoErrorState(workflow, suspendedSteps, step);
                            } else {
                                step.status.updateState(StepState.QUEUED, null, "Unblocked by step: " + fromStepId);
                                persistWorkflowStepUpdate(workflow, step);
                                _log.info(String.format("Step %s has been unblocked by step %s", step.stepId, fromStepId));
                                dispatchStep(step, workflow._nested);
                            }
                        }
                    } catch (CancelledException ex) {
                        again = true;
                        // If we got a CancelledException, this step needs to be cancelled.
                        step.status.updateState(StepState.CANCELLED, null, "Cancelled by step: " + fromStepId);
                        _log.info(String.format("Step %s has been cancelled by step %s", step.stepId, fromStepId));
                        persistWorkflowStepUpdate(workflow, step);
                    }
                } catch (Exception ex) {
                    _log.error("Exception" + ex.getMessage());
                }
            }
        } while (again == true);
    }

    /**
     * Checks the workflow for any steps marked for suspension and marked them for suspension and cancels remaining
     * steps.
     * 
     * @param workflow
     *            -- The Workflow to be checked.
     * @return
     *         -- true if the entire workflow is suspended, false if there's something worth queueing
     */
    private boolean checkSuspendedSteps(Workflow workflow) {
        boolean again;
        String fromStepId = "None";
        Set<String> suspendedSteps = new HashSet<String>();
        do {
            again = false; // only loop again if made change
            for (Step step : workflow.getStepMap().values()) {
                if (step.status.state == StepState.SUSPENDED_NO_ERROR || step.status.state == StepState.CANCELLED) {
                    continue;
                }
                try {
                    try {
                        if (!isBlocked(workflow, step) && isStepMarkedForSuspend(workflow, step)) {
                            again = true;
                            changeStepToSuspendedNoErrorState(workflow, suspendedSteps, step);
                            fromStepId = step.stepId;
                        }
                    } catch (CancelledException ex) {
                        again = true;
                        // If we got a CancelledException, this step needs to be cancelled.
                        step.status.updateState(StepState.CANCELLED, null, "Cancelled by step: " + fromStepId);
                        _log.info(String.format("Step %s has been cancelled by step %s", step.stepId, fromStepId));
                        persistWorkflowStepUpdate(workflow, step);
                    }
                } catch (Exception ex) {
                    _log.error("Exception" + ex.getMessage());
                }
            }
        } while (again == true);

        for (Step step : workflow.getStepMap().values()) {
            if (step.status.state == StepState.CREATED || step.status.state == StepState.BLOCKED || step.status.state == StepState.QUEUED) {
                // There's a reason to go into the queueing loop to attempt to dispatch steps
                return false;
            }
        }

        // Don't bother dispatching steps. Call the completer for the workflow as suspended.
        return true;
    }

    /**
     * Convenience method that sets all of the expected fields associated with a step going from one state to
     * the SUSPENDED_NO_ERROR state.
     * 
     * @param workflow
     *            the workflow
     * @param suspendedSteps
     *            the suspended steps list to add the new step to
     * @param step
     *            step to suspend
     */
    private void changeStepToSuspendedNoErrorState(Workflow workflow, Set<String> suspendedSteps, Step step) {
        // Transitioning the step to suspended state, shut off this flag if it was set.
        step.suspendStep = false;
        // Persist the step information in cassandra
        logStep(workflow, step);
        // Change the status of the step to suspended with no error, create a good message for the user here.
        // It would be better if we had step-specific user messages that are cataloged and I18N'able.
        String message = String.format("Task has been suspended during step \"" + step.description +
                "\".  The user has the opportunity to perform any manual validation before this step is executed.  " +
                "The user may choose to rollback the operation if manual validation failed.");
        step.status.updateState(StepState.SUSPENDED_NO_ERROR, null, message);
        // Persist the workflow information to ZK
        persistWorkflowStepUpdate(workflow, step);
        // Add the step to the list of steps that are to be suspended
        suspendedSteps.add(step.stepId);
    }

    /**
     * Convenience Method to determine if a step in a workflow is marked to be suspended when it's
     * time to run.
     * 
     * @param workflow
     *            workflow
     * @param step
     *            workflow step to analyze
     * @return true if the step is marked to be suspended
     */
    private boolean isStepMarkedForSuspend(Workflow workflow, Step step) {
        return step.suspendStep || (workflow.getSuspendSteps() != null && !workflow.getSuspendSteps().isEmpty()
                && (workflow.getSuspendSteps().contains(workflow.getWorkflowURI())
                        || workflow.getSuspendSteps().contains(step.workflowStepURI)));
    }

    /**
     * Determine if a workflow step is blocked. A step is blocked if it has a waitFor clause
     * pointing to a step or step group that is not in the SUCCESS state.
     * If a pre-requisite step has errored or been cancelled, a CancelledException is thrown.
     * 
     * @param workflow
     *            Workflow containing the Step
     * @param step
     *            Step checked.
     * @return true if the step is blocked waiting on a pre-requiste step to complete, false if runnable now.
     * @throws CancelledException
     *             if a prerequisite step has had an error or has been cancelled
     *             or if this step (or all steps) should be cancelled because of suspend request.
     */
    boolean isBlocked(Workflow workflow, Step step) throws WorkflowException,
            CancelledException {
        // The step cannot be blocked if waitFor is null (which means not specified)
        if (step.waitFor == null) {
            return false;
        }
        Map<String, StepStatus> statusMap = new HashMap<String, StepStatus>();
        try {
            StepStatus status = workflow.getStepStatus(step.waitFor);
            statusMap.put(step.waitFor, status);
        } catch (WorkflowException ex1) {
            try {
                statusMap = workflow.getStepGroupStatus(step.waitFor);
            } catch (WorkflowException ex2) {
                throw new WorkflowException(
                        String.format(
                                "Workflow step %s waitFor %s invalid, must be stepId or stepGroup name",
                                step.stepId, step.waitFor));
            }
        }
        String[] errorMessage = new String[1];
        StepState state = Workflow.getOverallState(statusMap, errorMessage);
        switch (state) {
            case SUSPENDED_NO_ERROR:
            case SUSPENDED_ERROR:
            case CANCELLED:
                throw new CancelledException();
            case ERROR:
                if ((workflow.getRollbackContOnError()) && (workflow.isRollbackState())) {
                    _log.info("Allowing rollback to continue despite failure in previous rollback step.");
                    return false;
                }
                throw new CancelledException();
            case SUCCESS:
                return false;
            case CREATED:
            case BLOCKED:
            case QUEUED:
            case EXECUTING:
            default:
                return true;
        }
    }

    /**
     * Initiate a rollback of the entire workflow.
     * 
     * @param workflow
     *            - The workflow to be rolled back.
     * @return true if rollback initiated, false if suspended.
     */
    public boolean initiateRollback(Workflow workflow) throws WorkflowException {
        // Verify all existing steps are in a terminal state.
        Map<String, StepStatus> statusMap = workflow.getAllStepStatus();
        for (StepStatus status : statusMap.values()) {
            if (false == status.isTerminalState()) {
                throw new WorkflowException("Step: " + status.stepId
                        + " is not in a terminal state: " + status.state);
            }
        }

        // Make sure all non-cancelled nodes have a rollback method.
        // TODO: handle null rollback methods better.
        boolean norollback = false;
        for (Step step : workflow.getStepMap().values()) {
            if (step.status.state != StepState.CANCELLED && step.rollbackMethod == null) {
                _log.error(String
                        .format("Cannot rollback step %s because it does not have a rollback method",
                                step.stepId));
                norollback = true;
            }
        }
        if (norollback) {
            return false;
        }

        _log.info("Generating rollback steps for workflow: " + workflow.getWorkflowURI());

        // Going to try and initiate the rollback.
        if (workflow._rollbackHandler != null) {
            workflow._rollbackHandler.initiatingRollback(workflow,
                    workflow._rollbackHandlerArgs);
        }

        // Determine the steps that need to be rolled back.
        // Maps step original stepId to rollback Step.
        Map<String, Step> rollbackStepMap = new HashMap<String, Step>();
        // Contains dependencies for the rollback Steps organized into Step Groups..
        Map<String, Set<String>> rollbackStepGroupMap = new HashMap<String, Set<String>>();
        // Map of step ids or stepGroups names to execution step ids having a dependence on this step/group
        Map<String, List<String>> dependenceMap = new HashMap<String, List<String>>();
        // Map of StepGroup to nodes having a dependence on StepGroup

        for (Step step : workflow.getStepMap().values()) {
            // Don't process cancelled nodes, they don't need to be rolled back.
            if (step.status.state == StepState.CANCELLED) {
                continue;
            }
            // If we have a dependence, put it in the dependence map
            if (step.waitFor != null) {
                if (dependenceMap.get(step.waitFor) == null) {
                    dependenceMap.put(step.waitFor, new ArrayList<String>());
                }
                // Step is dependent on the indicated waitFor
                dependenceMap.get(step.waitFor).add(step.stepId);
            }

            // Compute the corresponding rollback node.
            Step rb = step.generateRollbackStep();
            rollbackStepMap.put(step.stepId, rb);
        }

        // For each rollbackStep rs1, create a stepGroup that contains the dependencies that
        // need to be satisfied before it executes. If it's corresponding executeStep is es1,
        // then the dependency step group for rs1 contains all rollbackSteps rsx whose corresponding
        // execution step esx was dependent on es1. Thus esx can either be directly dependent on es1,
        // or it can be dependent on the stepGroup containing es1.
        for (Step executeStep : workflow.getStepMap().values()) {
            if (executeStep.status.state == StepState.CANCELLED) {
                continue;
            }
            Step rollbackStep = rollbackStepMap.get(executeStep.stepId);
            String stepGroupKey = "_rollback_" + rollbackStep.stepId;
            rollbackStepGroupMap.put(stepGroupKey, new HashSet<String>());
            // rollback nodes corresponding to the direct dependents of executeStep
            List<String> dependentList = dependenceMap.get(executeStep.stepId);
            if (dependentList != null) {
                for (String dependentId : dependentList) {
                    Step dependentRollbackStep = rollbackStepMap.get(dependentId);
                    if (dependentRollbackStep == null) {
                        continue;
                    }
                    rollbackStepGroupMap.get(stepGroupKey).add(
                            dependentRollbackStep.stepId);
                }
            }
            // rollback nodes corresponding to the dependents in the executeStep's stepGroup
            dependentList = dependenceMap.get(executeStep.stepGroup);
            if (dependentList != null) {
                for (String dependentId : dependentList) {
                    Step dependentRollbackStep = rollbackStepMap.get(dependentId);
                    if (dependentRollbackStep == null) {
                        continue;
                    }
                    rollbackStepGroupMap.get(stepGroupKey).add(
                            dependentRollbackStep.stepId);
                }
            }
            // If we have dependencies, then set the waitFor to point to our group.
            if (false == rollbackStepGroupMap.get(stepGroupKey).isEmpty()) {
                rollbackStep.waitFor = stepGroupKey;
            }
        }

        // Print what is being added.
        for (Step step : rollbackStepMap.values()) {
            _log.info(String.format("Adding rollback node %s (%s) waitFor: %s",
                    step.stepId, step.description, step.waitFor));
        }
        for (String key : rollbackStepGroupMap.keySet()) {
            _log.info(String.format("Adding group %s members %s", key,
                    rollbackStepGroupMap.get(key)));
        }

        // Add all the rollback Steps and new dependence Groups
        for (Step rollbackStep : rollbackStepMap.values()) {
            StepStatus status = new StepStatus();
            status.stepId = rollbackStep.stepId;
            status.state = StepState.CREATED;
            status.description = rollbackStep.description;
            rollbackStep.status = status;
            workflow.getStepMap().put(rollbackStep.stepId, rollbackStep);
            workflow.getStepStatusMap().put(rollbackStep.stepId, status);
        }
        workflow.getStepGroupMap().putAll(rollbackStepGroupMap);
        workflow.setRollbackState(true);
        workflow.setWorkflowState(WorkflowState.ROLLING_BACK);

        // Persist the workflow since we added the rollback groups
        persistWorkflow(workflow);
        logWorkflow(workflow, true);

        // Now queue all the new steps.
        for (Step step : rollbackStepMap.values()) {
            queueWorkflowStep(workflow, step);
        }
        return true;
    }

    /**
     * Persist the Cassandra logging record for the Workflow
     * 
     * @param workflow
     * @param completed
     *            - If true, assumes the Workflow has been completed
     *            (reached a terminal state).
     */
    void logWorkflow(Workflow workflow, boolean completed) {
        try {
            boolean created = false;
            com.emc.storageos.db.client.model.Workflow logWorkflow = null;
            if (workflow._workflowURI != null) {
                logWorkflow = _dbClient.queryObject(
                        com.emc.storageos.db.client.model.Workflow.class,
                        workflow._workflowURI);
            } else {
                workflow._workflowURI = URIUtil.createId(com.emc.storageos.db.client.model.Workflow.class);
            }
            // Are we updating or adding?
            if (logWorkflow == null) {
                created = true;
                logWorkflow = new com.emc.storageos.db.client.model.Workflow();
                logWorkflow.setId(workflow._workflowURI);
                logWorkflow.setCreationTime(Calendar.getInstance());
                logWorkflow.setCompleted(false);
            }
            logWorkflow.setOrchControllerName(workflow._orchControllerName);
            logWorkflow.setOrchMethod(workflow._orchMethod);
            logWorkflow.setOrchTaskId(workflow._orchTaskId);
            logWorkflow.setCompleted(completed);
            if (completed) {
                // If completed, log the final state and error message.
                try {
                    Map<String, StepStatus> statusMap = workflow.getAllStepStatus();
                    String[] errorMessage = new String[] { workflow._successMessage };
                    Workflow.getOverallState(statusMap, errorMessage);
                    WorkflowState state = workflow.getWorkflowState();
                    logWorkflow.setCompletionState(state.name());
                    logWorkflow.setCompletionMessage(errorMessage[0]);
                } catch (WorkflowException ex) {
                    _log.error(ex.getMessage(), ex);
                }
            }
            if (created) {
                _dbClient.createObject(logWorkflow);
            } else {
                _dbClient.updateObject(logWorkflow);
            }

            if (workflow.getOrchTaskId() != null) {
                List<Task> tasks = TaskUtils.findTasksForRequestId(_dbClient,
                        workflow.getOrchTaskId());
                if (tasks != null && false == tasks.isEmpty()) {
                    for (Task task : tasks) {
                        task.setWorkflow(workflow.getWorkflowURI());
                    }
                    _dbClient.updateObject(tasks);
                }
            }
        } catch (DatabaseException ex) {
            _log.error("Cannot persist Cassandra Workflow record " + workflow.getWorkflowURI().toString(), ex);
        }
    }

    /**
     * Persist the Cassandra logging record for the Step. This is called for each state change.
     * 
     * @param workflow
     * @param step
     */
    void logStep(Workflow workflow, Step step) {
        try {
            boolean create = false;
            com.emc.storageos.db.client.model.WorkflowStep logStep = null;
            if (step.workflowStepURI == null) {
                create = true;
                logStep = new com.emc.storageos.db.client.model.WorkflowStep();
                logStep.setId(URIUtil
                        .createId(com.emc.storageos.db.client.model.WorkflowStep.class));
                step.workflowStepURI = logStep.getId();
                logStep.setWorkflowId(workflow._workflowURI);
                logStep.setCreationTime(Calendar.getInstance());
                logStep.setStepId(step.stepId);
            } else {
                logStep = _dbClient.queryObject(
                        com.emc.storageos.db.client.model.WorkflowStep.class,
                        step.workflowStepURI);
            }
            logStep.setControllerName(step.controllerName);
            logStep.setDescription(step.description);
            logStep.setSystemId(step.deviceURI);
            logStep.setSystemType(step.deviceType);
            logStep.setEndTime(step.status.endTime);
            logStep.setExecuteMethod(step.executeMethod.methodName);
            logStep.setMessage(step.status.message);
            if (step.rollbackMethod != null) {
                logStep.setRollbackMethod(step.rollbackMethod.methodName);
            }
            logStep.setStartTime(step.status.startTime);
            logStep.setState(step.status.state.name());
            logStep.setStepGroup(step.stepGroup);
            logStep.setStepId(step.stepId);
            logStep.setWaitFor(step.waitFor);
            logStep.setSuspendStep(step.suspendStep);
            if (create) {
                _dbClient.createObject(logStep);
            } else {
                _dbClient.updateObject(logStep);
            }
        } catch (DatabaseException ex) {
            _log.error("Cannot persist Cassandra WorkflowEntry record");
        }
    }

    /**
     * Get the InterProcessLock for a Workflow.
     * 
     * @param workflow
     *            -- Used to get the workflowURI() that names the semaphore.
     * @return InterProcessLock
     * @throws WorkflowException
     */
    private InterProcessLock getWorkflowLock(Workflow workflow) throws WorkflowException {
        try {
            assert (workflow.getWorkflowURI() != null);
            InterProcessLock lock = _coordinator.getLock(getLockName(workflow));
            return lock;
        } catch (Exception ex) {
            _log.error("Could not get workflow semaphore: " + workflow.getOrchTaskId(), ex);
            throw new WorkflowException("Could not get workflow semaphore: " + workflow.getOrchTaskId(), ex);
        }
    }

    /**
     * Locks a Workflow using ZK
     * 
     * @param workflow
     * @return true if lock acquired
     * @throws WorkflowException
     */
    private InterProcessLock lockWorkflow(Workflow workflow) throws WorkflowException {
        boolean acquired = false;
        InterProcessLock lock = getWorkflowLock(workflow);
        try {
            acquired = lock.acquire(60, TimeUnit.MINUTES);
        } catch (Exception ex) {
            _log.error("Exception locking workflow: " + workflow.getWorkflowURI().toString(), ex);
            throw new WorkflowException("Exception locking workflow: " + workflow.getWorkflowURI().toString(), ex);
        }
        if (acquired == false) {
            _log.error("Unable to acquire workflow lock: " + workflow.getWorkflowURI().toString());
            throw new WorkflowException("Unable to acquire workflow lock: " + workflow.getWorkflowURI().toString());
        }
        return lock;
    }

    /**
     * Unlocks a workflow using ZK
     * 
     * @param workflow
     * @throws WorkflowException
     */
    private void unlockWorkflow(Workflow workflow, InterProcessLock lock) throws WorkflowException {
        try {
            if (lock != null) {
                lock.release();
            }
        } catch (Exception ex) {
            _log.error("Exception unlocking workflow: " + workflow.getWorkflowURI().toString(), ex);
            throw new WorkflowException("Exception unlocking workflow: " + workflow.getWorkflowURI().toString(), ex);
        }
    }

    /**
     * Delete's a Workflow's lock.
     * 
     * @param workflow
     */
    private void deleteWorkflowLock(Workflow workflow) {
        try {
            String lockPath = getLockPath(workflow);
            _dataManager.removeNode(lockPath);
        } catch (Exception ex) {
            _log.error("Exception removing lock for workflow: " + workflow.getWorkflowURI().toString(), ex);
        }
    }

    private String getLockName(Workflow workflow) {
        return "workflows/" + workflow.getWorkflowURI().toString();
    }

    private String getLockPath(Workflow workflow) {
        String lockPath = ZKPaths.makePath(ZkPath.MUTEX.toString(), getLockName(workflow));
        return lockPath;
    }

    private int getZkStepToWorkflowSize() throws Exception {
        Stat stat = _dataManager.checkExists(_zkStepToWorkflow);
        if (stat == null) {
            return 0;
        } else {
            return stat.getNumChildren();
        }
    }

    /**
     * Returns total number of step2workflow that needs to be executed across all workflows
     * 
     * @return number of step2workflow
     * @throws Exception
     */
    public static int getZkStep2WorkflowSize() throws Exception {
        return _instance.getZkStepToWorkflowSize();
    }

    /**
     * Associates workflow to a parent (outer) workflow (if any), i.e.
     * this Workflow is nested within the outer one.
     * Depends on the Worflow's orchestration task id being a step in the outer workflow.
     * 
     * @param workflow
     *            -- potential nested Workflow
     * @return true if a parent association was made.
     */
    private boolean associateToParentWorkflow(Workflow workflow) {
        try {
            String parentPath = getZKStep2WorkflowPath(workflow.getOrchTaskId());
            if (_dataManager.checkExists(parentPath) != null) {
                // Record our workflow URI as a child in the parent Workflow URI.
                // Get the parent workflow path from ZK
                parentPath = (String) _dataManager.getData(parentPath, false);
                // Load the Workflow state from ZK
                if (parentPath != null) {
                    InterProcessLock parentLock = null;
                    Workflow parentWorkflow = (Workflow) _dataManager.getData(parentPath, false);
                    try {
                        parentLock = lockWorkflow(parentWorkflow);
                        parentWorkflow = (Workflow) _dataManager.getData(parentPath, false);
                        parentWorkflow._childWorkflows.add(workflow.getWorkflowURI());
                        persistWorkflow(parentWorkflow);
                    } finally {
                        unlockWorkflow(parentWorkflow, parentLock);
                    }
                }
                return true;
            }
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
        }
        return false;
    }

    /**
     * Given a Workflow step id, search ZK and return the immediate parent Workflow.
     *
     * @param stepId
     *            Workflow step id
     * @return Workflow
     */
    public Workflow getWorkflowFromStepId(String stepId) {
        try {
            String parentPath = getZKStep2WorkflowPath(stepId);
            if (_dataManager.checkExists(parentPath) != null) {
                parentPath = (String) _dataManager.getData(parentPath, false);
                if (parentPath != null) {
                    return (Workflow) _dataManager.getData(parentPath, false);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * Acquires locks on behalf of a Workflow. If successfully acquired,
     * they are saved in the Workflow state and will be released when the
     * workflow completes.
     * 
     * @param workflow
     * @param lockKeys
     * @param time
     * @return true if locks acquired
     */
    public boolean acquireWorkflowLocks(Workflow workflow, List<String> lockKeys, long time) {
        boolean gotLocks = false;
        try {
            List<String> locksToAcquire = new ArrayList<String>(lockKeys);
            // Remove any locks this workflow has already acquired,
            // so as not to acquire them multiple times.
            locksToAcquire.removeAll(_ownerLocker.getLocksForOwner(workflow.getWorkflowURI().toString()));
            if (locksToAcquire.isEmpty()) {
                return true;
            }
            gotLocks = _ownerLocker.acquireLocks(locksToAcquire,
                    workflow.getWorkflowURI().toString(), getOrchestrationIdStartTime(workflow), time);
        } catch (LockRetryException ex) {
            _log.info(String.format("Lock retry exception key: %s remaining time %d", ex.getLockIdentifier(),
                    ex.getRemainingWaitTimeSeconds()));
            throw ex;
        } catch (Exception ex) {
            _log.error("Unable to acquire workflow locks", ex);
        }
        return gotLocks;
    }

    /**
     * Acquires locks on behalf of a workflow step. The locks will be released at the
     * end of the step, i.e. when the step is completed. This should only be called
     * from within the executing workflow step.
     * Note that if the same lock is already held by the workflow, it will not be
     * reacquired, and will not be released until the workflow completes.
     * 
     * @param stepId
     *            - Workflow step id.
     * @param lockKeys
     *            - List of lock keys to be acquired
     * @param time
     *            - Maximum wait time, 0 means poll
     * @return
     */
    public boolean acquireWorkflowStepLocks(String stepId, List<String> lockKeys, long time) {
        String workflowPath = getZKStep2WorkflowPath(stepId);
        boolean gotLocks = false;
        try {
            // Get the workflow path from ZK
            workflowPath = (String) _dataManager.getData(workflowPath, false);
            // It is not an error to try and update using a non-existent stepId
            if (workflowPath == null) {
                return false;
            }
            // Load the Workflow state from ZK
            Workflow workflow = (Workflow) _dataManager.getData(workflowPath, false);
            if (workflow == null) {
                throw new WorkflowException("Could not load workflow for step: " + stepId);
            }
            Long stepStartTimeSeconds = System.currentTimeMillis();
            StepStatus stepStatus = workflow.getStepStatusMap().get(stepId);
            if (stepStatus != null && stepStatus.startTime != null) {
                stepStartTimeSeconds = stepStatus.startTime.getTime() / MILLISECONDS_IN_SECOND;
            }
            List<String> locksToAcquire = new ArrayList<String>(lockKeys);
            // Remove any locks this workflow has already acquired,
            // so as not to acquire them multiple times.
            locksToAcquire.removeAll(_ownerLocker.getLocksForOwner(workflow.getWorkflowURI().toString()));
            // Also remove all locks already acquired in this step.
            locksToAcquire.removeAll(_ownerLocker.getLocksForOwner(stepId));
            if (locksToAcquire.isEmpty()) {
                return true;
            }
            gotLocks = _ownerLocker.acquireLocks(locksToAcquire, stepId, stepStartTimeSeconds, time);
        } catch (LockRetryException ex) {
            _log.info(String.format("Lock retry exception key: %s remaining time %d", ex.getLockIdentifier(),
                    ex.getRemainingWaitTimeSeconds()));
            WorkflowStepCompleter.stepQueued(stepId);
            throw ex;
        } catch (Exception ex) {
            _log.info("Exception acquiring WorkflowStep locks: ", ex);
        }
        return gotLocks;
    }

    /**
     * Releases all locks held by the workflow if workflow non-null.
     * No-op and returns true if workflow null.
     * 
     * @param workflow
     * @return true if locks removed
     */
    public boolean releaseAllWorkflowLocks(Workflow workflow) {
        if (workflow == null) {
            return true;
        }
        boolean releasedLocks = _ownerLocker.releaseLocks(workflow.getWorkflowURI().toString());
        if (!releasedLocks) {
            _log.error("Unable to release Workflow locks for workflow: " + workflow.getWorkflowURI().toString());
        }
        return releasedLocks;
    }

    @Override
    public void suspendWorkflowStep(URI workflowURI, URI stepURI, String taskId)
            throws ControllerException {
        WorkflowTaskCompleter completer = new WorkflowTaskCompleter(workflowURI, taskId);
        _log.info(String.format("Suspend request workflow: %s step: %s", workflowURI, stepURI));
        Workflow workflow = loadWorkflowFromUri(workflowURI);
        if (workflow.getSuspendSteps() == null) {
            workflow.setSuspendSteps(new HashSet<URI>());
        }
        if (NullColumnValueGetter.isNullURI(stepURI)) {
            // In this case, we want to suspend any step trying to unblock.
            workflow.getSuspendSteps().add(workflowURI);
        } else {
            // In this case, we want to suspend only when we reach designated step.
            workflow.getSuspendSteps().add(stepURI);
        }
        persistWorkflow(workflow);
        completer.ready(_dbClient);
    }

    @Override
    public void resumeWorkflow(URI uri, String taskId)
            throws ControllerException {
        Workflow workflow = null;
        InterProcessLock workflowLock = null;
        WorkflowTaskCompleter completer = new WorkflowTaskCompleter(uri, taskId);
        try {
            _log.info(String.format("Resume request workflow: %s", uri));
            workflow = loadWorkflowFromUri(uri);
            if (workflow == null) {
                // Cannot resume non-existent workflow
                throw WorkflowException.exceptions.workflowNotFound(uri.toString());
            }
            WorkflowState state = workflow.getWorkflowState();
            if (state != WorkflowState.SUSPENDED_ERROR
                    && state != WorkflowState.SUSPENDED_NO_ERROR) {
                // Cannot resume a workflow that is not suspended
                _log.info(String.format("Child workflow %s state %s is not suspended and will not be resumed", uri, state));
                return;
            }

            if (workflow._taskCompleter != null) {
                workflow._taskCompleter.statusPending(_dbClient, "Resuming workflow");
            }

            workflowLock = lockWorkflow(workflow);
            Map<String, com.emc.storageos.db.client.model.Workflow> childWFMap = getChildWorkflowsMap(workflow);
            removeRollbackSteps(workflow);
            queueResumeSteps(workflow, childWFMap);
            // Resume the child workflows if applicable.
            for (com.emc.storageos.db.client.model.Workflow child : childWFMap.values()) {
                resumeWorkflow(child.getId(), null);
            }
            completer.ready(_dbClient);
        } catch (WorkflowException ex) {
            completer.error(_dbClient, ex);
            ;
        } finally {
            unlockWorkflow(workflow, workflowLock);
        }
    }

    @Override
    public void rollbackWorkflow(URI uri, String taskId)
            throws ControllerException {
        WorkflowTaskCompleter completer = new WorkflowTaskCompleter(uri, taskId);
        try {
            _log.info(String.format("Rollback requested workflow: %s", uri));
            Workflow workflow = loadWorkflowFromUri(uri);
            if (workflow == null) {
                throw WorkflowException.exceptions.workflowNotFound(uri.toString());
            }

            if (workflow.getWorkflowURI() == null) {
                workflow.setWorkflowURI(uri);
                logWorkflow(workflow, false);
                persistWorkflow(workflow);
            }

            completer.statusPending(_dbClient, "Rollback requested on workflow: " + uri.toString());
            removeRollbackSteps(workflow);

            // See if there are child Workflows that need to be rolled back.
            // These are roll-backed first.
            Map<String, com.emc.storageos.db.client.model.Workflow> childWFMap = getChildWorkflowsMap(workflow);
            for (Entry<String, com.emc.storageos.db.client.model.Workflow> entry : childWFMap.entrySet()) {
                String parentStepId = entry.getKey();
                Workflow child = loadWorkflowFromUri(entry.getValue().getId());
                WorkflowState state = child.getWorkflowState();
                switch (state) {
                    case SUSPENDED_ERROR:
                    case SUSPENDED_NO_ERROR:
                        _dbClient.pending(com.emc.storageos.db.client.model.Workflow.class,
                                child.getWorkflowURI(), parentStepId, "rolling back sub-workflow");
                        rollbackWorkflow(child.getWorkflowURI(), entry.getKey());
                        Status status = waitOnOperationComplete(com.emc.storageos.db.client.model.Workflow.class,
                                child.getWorkflowURI(), parentStepId);
                        _log.info(String.format("Child rollback task %s completed with state %s", taskId, status.name()));
                        ;
                        // TODO: should we go forward if unable to roll back child?
                        break;
                    default:
                        continue;
                }
            }

            // Now try to start rollback of top level Workflow
            _dbClient.pending(com.emc.storageos.db.client.model.Workflow.class,
                    uri, workflow.getOrchTaskId(), "rolling back top-level-workflow");
            InterProcessLock workflowLock = null;
            try {
                workflowLock = lockWorkflow(workflow);
                boolean rollBackStarted = initiateRollback(workflow);
                if (rollBackStarted) {
                    _log.info(String.format("Rollback initiated workflow %s", uri));
                } else {
                    // We were unable to initiate rollback for some reason, this is an error.
                    WorkflowState state = workflow.getWorkflowStateFromSteps();
                    switch (state) {
                        case SUCCESS:
                            completer.ready(_dbClient);
                            ;
                            break;
                        default:
                            WorkflowException ex = WorkflowException.exceptions.workflowRollbackNotInitiated(uri.toString());
                            completer.error(_dbClient, ex);
                            ;
                    }
                }
            } finally {
                unlockWorkflow(workflow, workflowLock);
            }
        } catch (WorkflowException ex) {
            _log.info("Exception rolling back workflow: ", ex.getMessage(), ex);
        }
    }

    /**
     * Queue steps to resume workflow.
     * 
     * @param workflow
     */
    private void queueResumeSteps(Workflow workflow,
            Map<String, com.emc.storageos.db.client.model.Workflow> childWFMap) {
        // Get a map of orchestration task id to child workflow URI.

        // Clear any error steps. Mark back to CREATED.
        for (String stepId : workflow.getStepMap().keySet()) {
            StepState state = workflow.getStepStatus(stepId).state;
            switch (state) {
                case ERROR:
                    // If there is a suspended child WF for a step, we set it to executing rather than created.
                    // resumeWorkflow will resume the appropriate child workflows.
                    if (childWFMap.containsKey(stepId)) {
                        Workflow child = loadWorkflowFromUri(childWFMap.get(stepId).getId());
                        if (child.getWorkflowState() == WorkflowState.SUSPENDED_ERROR
                                || child.getWorkflowState() == WorkflowState.SUSPENDED_NO_ERROR) {
                            workflow.getStepStatus(stepId).updateState(StepState.EXECUTING, null, "");
                            break;
                        }
                    }
                    workflow.getStepStatus(stepId).updateState(StepState.CREATED, null, "");
                    break;
                case BLOCKED:
                case CREATED:
                case SUSPENDED_NO_ERROR:
                case SUSPENDED_ERROR:
                case CANCELLED:
                case EXECUTING:
                    workflow.getStepStatus(stepId).updateState(StepState.CREATED, null, "");
                    break;
                case QUEUED:
                case SUCCESS:
                    break;
            }
        }

        // Queue the newly recreated steps
        for (String stepId : workflow.getStepMap().keySet()) {
            Step step = workflow.getStepMap().get(stepId);
            if (step.status.state == StepState.CREATED) {
                queueWorkflowStep(workflow, step);
                persistWorkflowStep(workflow, step);
            }
        }
        workflow.setWorkflowState(WorkflowState.RUNNING);
        persistWorkflow(workflow);
        logWorkflow(workflow, true);
    }

    /**
     * This call will rollback a child workflow given the parent's workflow URI and the step-id
     * of the parent step which is the child workflow's orchestration task id.
     * <p>
     * The idea is that if step of a parent workflow creates a child workflow, which completes successfully, but then a
     * later step in the parent workflow fails, initiating rollback, we need an easy way to rollback the entire child
     * workflow in the rollback method of the step that created the child workflow.
     * <p>
     * So this method should only be called from a parent workflow's rollback method for the step that initiated the
     * child workflow. In order to be eligible to be rolled back, the child workflow must have completed successfully.
     * It will be completely rolled back (i.e. all steps in the child workflow) will be rolled back.
     * 
     * @param parentURI
     * @param childOrchestrationTaskId
     * @param stepId
     */
    public void rollbackChildWorkflow(URI parentURI, String childOrchestrationTaskId, String stepId) {
        Workflow parentWorkflow = loadWorkflowFromUri(parentURI);
        if (parentWorkflow == null) {
            _log.info("Could not locate parent workflow %s (%s), possibly it was already deleted");
            ServiceCoded coded = WorkflowException.exceptions.workflowNotFound(parentURI.toString());
            WorkflowStepCompleter.stepFailed(stepId, coded);
        }
        for (URI childURI : parentWorkflow._childWorkflows) {
            Workflow childWorkflow = loadWorkflowFromUri(childURI);
            if (childWorkflow == null) {
                _log.info("Could not locate child workflow %s (%s), possibly it was already deleted");
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }
            // TODO: This is a short-term fix for 12858. A more appropriate fix would be to detect that the zk copy of
            // the WF does not
            // exist.
            if (!NullColumnValueGetter.isNullValue(childWorkflow.getOrchTaskId())
                    && childWorkflow.getOrchTaskId().equals(childOrchestrationTaskId)) {
                // Rolling back the specified workflow.
                rollbackInnerWorkflow(childWorkflow, stepId);
                return;
            }
        }
        // Didn't find a Workflow to rollback.
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    /**
     * Rolls back a workflow that is assumed to be a child of the given stepId.
     * Updates the step status to EXECUTING if workflow is successfully initiated,
     * and aranges for a rollback completer to mark the step as SUCCESS when
     * the rollback completes.
     * NOTE: The current state of the child workflow must be SUCCESS in order
     * for rollback to be invoked.
     * 
     * @param workflow
     *            -- the Inner workflow
     * @param stepId
     *            -- assumed to be a stepId of the outer workflow
     */
    private void rollbackInnerWorkflow(Workflow workflow, String stepId) {
        URI uri = workflow.getWorkflowURI();
        _log.info(String.format("Rollback requested workflow: %s", uri));

        // Get the workflow state.
        String[] message = new String[1];
        message[0] = "";
        StepState state = Workflow.getOverallState(workflow.getStepStatusMap(), message);

        // Update the rollback handlers. We do this in order to be able to fire a completer at the end of the workflow.
        Object[] args;
        if (workflow._rollbackHandler != null) {
            // Nested rollback handler, add our arguments to the end.
            // Our rollback handler will call the nested handler.
            args = new Object[workflow._rollbackHandlerArgs.length
                    + NestedWorkflowRollbackHandler.NUMBER_OF_ADDED_ARGS];
            for (int i = 0; i < workflow._rollbackHandlerArgs.length; i++) {
                args[i] = workflow._rollbackHandlerArgs[i]; // copy original arguments
            }
            args[NestedWorkflowRollbackHandler.indexOfNestedHandler(args)] = workflow._rollbackHandler; // append our
                                                                                                        // new
                                                                                                        // arguments,
                                                                                                        // original
                                                                                                        // rollback
                                                                                                        // handler
            args[NestedWorkflowRollbackHandler.indexOfParentStepId(args)] = stepId; // append stepId for completion

        } else {
            // No nested rollback handler.
            args = new Object[NestedWorkflowRollbackHandler.NUMBER_OF_ADDED_ARGS];
            args[NestedWorkflowRollbackHandler.indexOfNestedHandler(args)] = null;
            args[NestedWorkflowRollbackHandler.indexOfParentStepId(args)] = stepId;
        }
        workflow._rollbackHandler = new NestedWorkflowRollbackHandler();
        workflow._rollbackHandlerArgs = args;

        // Determine if the workflow already attempted a rollback.
        // If so, attempt to restart the rollback's error and cancelled steps.
        boolean rollBackCompleted = determineIfRollbackCompleted(workflow);
        if (rollBackCompleted) {
            _log.info(String.format("Rollback already completed workflow %s", workflow.getWorkflowURI()));
            WorkflowStepCompleter.stepSucceded(stepId);
            return;
        }

        // See if can restart the previous rollback.
        InterProcessLock workflowLock = null;
        try {
            workflowLock = lockWorkflow(workflow);
            boolean rollBackStarted = resumePreviousRollback(workflow);
            if (rollBackStarted) {
                _log.info(String.format(
                        "Previous rollback resumed; errored/cancelled rollback steps queued; workflow %s",
                        workflow.getWorkflowURI()));
            } else {
                // Otherwise, attempt to initiate a new rollback.
                if (workflow._rollbackHandler != null) {
                    workflow._rollbackHandler.initiatingRollback(workflow,
                            workflow._rollbackHandlerArgs);
                }
                rollBackStarted = initiateRollback(workflow);
                if (rollBackStarted) {
                    _log.info(String.format("New rollback initiated workflow %s", workflow.getWorkflowURI()));
                }
            }

            if (rollBackStarted) {
                // Return now, wait until the rollback completions fire the completer.
                persistWorkflow(workflow);
                logWorkflow(workflow, true);
                WorkflowStepCompleter.stepExecuting(stepId);

            } else {
                ServiceCoded coded = WorkflowException.exceptions.workflowRollbackNotInitiated(uri.toString());
                WorkflowStepCompleter.stepFailed(stepId, coded);
            }
        } finally {
            unlockWorkflow(workflow, workflowLock);
        }
    }

    /**
     * Returns true if all the Rollback StepStates are SUCCESS.
     * Returns false if no Rollback was never initiated or some rollback states did not complete.
     * 
     * @param workflow
     *            URI
     * @return true iff all the Rollback StepStates are SUCCESS.
     */
    private boolean determineIfRollbackCompleted(Workflow workflow) {
        // If haven't initiated rollback, then return false.
        if (workflow.isRollbackState() == false) {
            return false;
        }
        boolean rollbackComplete = true;
        Map<String, Step> stepMap = workflow.getStepMap();
        for (Step step : stepMap.values()) {
            // Do not consider non-rollback steps
            if (!step.isRollbackStep()) {
                continue;
            }
            StepStatus status = workflow.getStepStatus(step.stepId);
            if (status.isTerminalState() == false || status.state != StepState.SUCCESS) {
                _log.info(String.format("Rollback step %s not successful, state %s",
                        step.stepId, status.state.name()));
                rollbackComplete = false;
            }
        }
        return rollbackComplete;
    }

    /**
     * Resume the error/cancelled steps in a previous rollback if possible.
     * Returns true if rollback restarted; false if there was no previous rollback.
     * 
     * @param workflow
     *            URI
     * @return true iff a previous rollback was restarted
     */
    private boolean resumePreviousRollback(Workflow workflow) {
        // If haven't initiated rollback, then return false.
        if (workflow.isRollbackState() == false) {
            return false;
        }
        Map<String, Step> stepMap = workflow.getStepMap();
        // Determine what steps need to be re-executed.
        for (Step step : stepMap.values()) {
            // Do not consider non-rollback steps
            if (!step.isRollbackStep()) {
                continue;
            }
            // If the rollback step's status is ERROR or CANCELLED try to run it again
            // by setting it to CREATE. We should not have any non-terminal states.
            if (step.status.state == StepState.ERROR
                    || step.status.state == StepState.CANCELLED) {
                step.status.updateState(StepState.CREATED, null, "");
            }
        }
        // Now queue all the steps to be restarted.
        for (Step step : stepMap.values()) {
            if (step.isRollbackStep() && step.status.state == StepState.CREATED) {
                _log.info(String.format("Retrying previous rollback step %s : %s",
                        step.stepId, step.description));
                queueWorkflowStep(workflow, step);
            }
        }
        return true;
    }

    /**
     * Attempts to intuit the start time for a provisioning operation from the orchestrationId.
     * This may be either a step in an outer workflow, or a task. The Workflow itself is not used
     * because when retrying for a workflow lock, a new workflow is created every time.
     * 
     * @param workflow
     *            Workflow
     * @return start time in seconds
     */
    private Long getOrchestrationIdStartTime(Workflow workflow) {
        Long timeInSeconds = 0L;
        String orchestrationId = workflow._orchTaskId;
        if (workflow._nested) {
            String parentPath = getZKStep2WorkflowPath(orchestrationId);
            try {
                if (_dataManager.checkExists(parentPath) != null) {
                    parentPath = (String) _dataManager.getData(parentPath, false);
                    // Load the Workflow state from ZK
                    if (parentPath != null) {
                        Workflow parentWorkflow = (Workflow) _dataManager.getData(parentPath, false);
                        // Get the StepStatus for our step.
                        StepStatus status = parentWorkflow.getStepStatus(orchestrationId);
                        if (status != null && status.startTime != null) {
                            timeInSeconds = status.startTime.getTime() / MILLISECONDS_IN_SECOND;
                        }
                    }
                }
            } catch (Exception ex) {
                _log.error("An error occurred", ex);
            }
        }
        if (timeInSeconds == 0) {
            // See if there is a task with this id.
            List<Task> tasks = TaskUtils.findTasksForRequestId(_dbClient, orchestrationId);
            for (Task task : tasks) {
                timeInSeconds = task.getStartTime().getTimeInMillis() / MILLISECONDS_IN_SECOND;
            }
        }
        if (timeInSeconds == 0) {
            // Last resort - current time
            timeInSeconds = System.currentTimeMillis() / MILLISECONDS_IN_SECOND;
        }
        return timeInSeconds;
    }

    /**
     * Sets the workflow's rollback continue on error flag given a stepId in the workflow.
     * The normal use for this method is to be called from a step in the workflow when it
     * is decided we no longer want to continue rollback due to rollback errors.
     * To use this you should:
     * 1.Call the setWorkflowRollbackContOnError flag setting value to false.
     * 2.Terminate the step with an ERROR condition.
     * After this if any rollback step reports an error (including the current step
     * if it is a rollback step), this will cause cancellation of any further rollback steps.
     * 
     * @param stepId
     * @param value
     */
    public void setWorkflowRollbackContOnError(String stepId, boolean value) {
        Workflow workflow = loadWorkflowFromStepId(stepId);
        workflow.setRollbackContOnError(value);
        _log.info("Setting rollback continue on error to {} for workflow {}", value, workflow.getWorkflowURI());
        persistWorkflow(workflow);
    }

    /**
     * Check to see if this workflow has already been created for this step. Used to ensure
     * that we don't create it again if the workflow step is re-entered.
     * 
     * @param stepId
     *            step ID
     * @param workflowKey
     *            identifies this workflow from other workflows that this step may create
     * @return true if the workflow has already been created
     */
    public boolean hasWorkflowBeenCreated(String stepId, String workflowKey) {
        // Check to see if we are re-entering this step after a previous execution already created the export workflow.
        // If this is the case, do not create it again.
        try {
            String stepData = (String) WorkflowService.getInstance().loadStepData(generateWorkflowCreatedKey(stepId, workflowKey));
            if (stepData != null && stepData.equalsIgnoreCase(Boolean.TRUE.toString())) {
                _log.info("Idempotency check: we already created this workflow and therefore will not create it again.");
                return true;
            }
        } catch (ClassCastException e) {
            // This will never, ever happen.
            _log.info("Step {} has stored workflow step data other than String. Exception: {}", stepId, e);
        }
        return false;
    }

    /**
     * Marks a workflow as being created by a step so future retries of that step will not create it again.
     * 
     * @param stepId
     *            step ID
     * @param workflowKey
     *            identifies thsi workflow from other workflows that this step may create
     */
    public void markWorkflowBeenCreated(String stepId, String workflowKey) {
        // Mark this workflow as created/executed so we don't do it again on retry/resume
        WorkflowService.getInstance().storeStepData(generateWorkflowCreatedKey(stepId, workflowKey), Boolean.TRUE.toString());
    }

    /**
     * Generate a unique key for a workflow created by a workflow step.
     * 
     * @param stepId
     *            step ID
     * @param workflowKey
     *            identifies this workflow from other workflows that this step may create
     * @return a unique key
     */
    private String generateWorkflowCreatedKey(String stepId, String workflowKey) {
        return stepId + ":" + workflowKey;
    }

    /**
     * Given a step id in a workflow, will return the Workflow.
     * 
     * @param stepId
     *            -- A step id of the workflow to be located
     * @return Workflow object, or throws workflowNotFound exception
     */
    private Workflow loadWorkflowFromStepId(String stepId) {
        String workflowPath = getZKStep2WorkflowPath(stepId);
        Workflow workflow = null;
        try {
            // Get the workflow path from ZK
            workflowPath = (String) _dataManager.getData(workflowPath, false);
            // It is not an error to try and update using a non-existent stepId
            if (workflowPath == null) {
                throw WorkflowException.exceptions.workflowNotFound(stepId);
            }
            // Load the Workflow state from ZK
            workflow = (Workflow) _dataManager.getData(workflowPath, false);
            if (workflow == null) {
                throw WorkflowException.exceptions.workflowNotFound(workflowPath);
            }
            return workflow;
        } catch (Exception ex) {
            throw WorkflowException.exceptions.workflowNotFound(stepId);
        }
    }

    /**
     * Load the Workflow from Zookeeper using the URI as a starting point by looking it up in the database.
     * 
     * @param workflowURI
     * @return
     * @throws ControllerException
     */
    private Workflow loadWorkflowFromUri(URI workflowURI) throws ControllerException {
        com.emc.storageos.db.client.model.Workflow dbWorkflow = _dbClient.queryObject(com.emc.storageos.db.client.model.Workflow.class,
                workflowURI);
        if (dbWorkflow != null) {
            Workflow workflow = new Workflow(this, dbWorkflow.getOrchControllerName(), dbWorkflow.getOrchMethod(), workflowURI);
            workflow = loadWorkflow(workflow);
            return workflow;
        }
        _log.info("Workflow not found in db: " + workflowURI.toString());
        throw WorkflowException.exceptions.workflowNotFound(workflowURI.toString());
    }

    /**
     * Removes all rollback steps from the Workflow. Used in resuming a workflow.
     * 
     * @param workflow
     *            Workflow
     */
    private void removeRollbackSteps(Workflow workflow) {
        Set<String> rollbackStepIds = new HashSet<String>();
        Map<String, Step> stepMap = workflow.getStepMap();
        // Determine rollback steps
        for (Step step : stepMap.values()) {
            if (step.isRollbackStep) {
                rollbackStepIds.add(step.stepId);
                if (!NullColumnValueGetter.isNullURI(step.workflowStepURI)) {
                    // Remove the rollback step from the database
                    com.emc.storageos.db.client.model.WorkflowStep dbStep = _dbClient.queryObject(
                            com.emc.storageos.db.client.model.WorkflowStep.class, step.workflowStepURI);
                    if (dbStep != null)
                        _dbClient.markForDeletion(dbStep);
                }
            }
        }
        // Remove each rollback step from StepMap, StepStatusMap, StepGroupMap members
        for (String stepId : rollbackStepIds) {
            workflow.getStepMap().remove(stepId);
            workflow.getStepStatusMap().remove(stepId);
            for (String stepGroup : workflow.getStepGroupMap().keySet()) {
                workflow.getStepGroupMap().get(stepGroup).remove(stepId);
            }
        }
    }

    /**
     * Returns a map of orchestration task id to child database workflow for all the children
     * of the specified workflow.
     * 
     * @param workflow
     *            - parent Workflow
     * @return Map of orchestration task id (String) to child workflow URI
     */
    private Map<String, com.emc.storageos.db.client.model.Workflow> getChildWorkflowsMap(Workflow workflow) {
        Map<String, com.emc.storageos.db.client.model.Workflow> childWFOrchTaskId2URI = new HashMap<String, com.emc.storageos.db.client.model.Workflow>();
        Set<URI> childWorkflowURIs = workflow._childWorkflows;
        if (childWorkflowURIs == null || childWorkflowURIs.isEmpty()) {
            return childWFOrchTaskId2URI;
        }
        List<com.emc.storageos.db.client.model.Workflow> childWorkflows = _dbClient.queryObject(
                com.emc.storageos.db.client.model.Workflow.class, childWorkflowURIs);
        for (com.emc.storageos.db.client.model.Workflow child : childWorkflows) {
            if (child == null || child.getInactive() == true) {
                continue;
            }
            childWFOrchTaskId2URI.put(child.getOrchTaskId(), child);
        }
        return childWFOrchTaskId2URI;
    }

    /**
     * Waits on an operation to complete.
     * 
     * @param clazz
     *            -- Class extending DataObject
     * @param uri
     *            -- URI of object
     * @param taskId
     *            -- Task id to be examined
     * @return -- Status (returns Status.error if record could not found)
     */
    private Status waitOnOperationComplete(Class<? extends DataObject> clazz, URI uri, String taskId) {
        Status status = Status.pending;
        do {
            DataObject dobj = _dbClient.queryObject(uri);
            if (dobj == null || dobj.getInactive() || dobj.getOpStatus() == null || !dobj.getOpStatus().containsKey(taskId)) {
                return Status.error;
            }
            Operation operation = dobj.getOpStatus().get(taskId);
            status = Status.toStatus(operation.getStatus());
        } while (status == Status.pending);
        return status;
    }

    private void printStepStatuses(Collection<StepStatus> stepStatuses) {
        for (StepStatus status : stepStatuses) {
            Date startTime = status.startTime;
            Date endTime = status.endTime;
            if (startTime != null && endTime != null) {
                _log.info(String.format(
                        "Step: %s (%s) state: %s message: %s started: %s completed: %s elapsed: %d ms",
                        status.stepId, status.description,
                        status.state, status.message, status.startTime,
                        status.endTime, (status.endTime.getTime() - status.startTime.getTime())));
            } else {
                _log.info(String.format(
                        "Step: %s (%s) state: %s message: %s ",
                        status.stepId, status.description, status.state, status.message));
            }
        }
    }

    public static void completerStepSucceded(String stepId)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.SUCCESS, null, "Step completed successfully");
    }

    public static void completerStepError(String stepId, ServiceCoded coded)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.ERROR, coded.getServiceCode(), coded.getMessage());
    }

    public static void completerStepErrorWithoutRollback(String stepId, ServiceCoded coded)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.ERROR, coded.getServiceCode(), coded.getMessage(), false);
    }

    public static void completerStepCancelled(String stepId, ServiceCoded coded)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.CANCELLED, coded.getServiceCode(), coded.getMessage());
    }

    public static void completerStepBlocked(String stepId)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.BLOCKED, null, "Step is blocked");
    }

    public static void completerStepExecuting(String stepId)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.EXECUTING, null, "Step is being executed");
    }

    public static void completerStepQueued(String stepId)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.QUEUED, null, "Step has been queued to be executed");
    }

    public static void completerStepCreated(String stepId)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.CREATED, null, "Step has been created");
    }

    public static void completerStepSuspendedNoError(String stepId)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.SUSPENDED_NO_ERROR, null, "Step has been suspended due to configuration or request");
    }

    public static void completerStepSuspendedError(String stepId, ServiceCoded coded)
            throws WorkflowException {
        _instance.updateStepStatus(stepId, StepState.SUSPENDED_ERROR, coded.getServiceCode(), "Step has been suspended due to an error");
    }

    public WorkflowScrubberExecutor getScrubber() {
        return _scrubber;
    }

    public void setScrubber(WorkflowScrubberExecutor _scrubber) {
        this._scrubber = _scrubber;
    }

    public DistributedOwnerLockService getOwnerLocker() {
        return _ownerLocker;
    }

    public void setOwnerLocker(DistributedOwnerLockService _ownerLocker) {
        this._ownerLocker = _ownerLocker;
    }

    /**
     * Specific to unit testing since we should not modify system-wide properties as part of a unit tester.
     * 
     * @param classMethod
     *            "Class.Method" string
     */
    public void setSuspendClassMethodTestOnly(String classMethod) {
        _suspendClassMethodTestOnly = classMethod;
    }

    /**
     * Specific to unit testing since we should not modify system-wide properties as part of a unit tester.
     * 
     * @param _suspendOnErrorTestOnly
     *            "true" to stop steps on error
     */
    public void setSuspendOnErrorTestOnly(Boolean _suspendOnErrorTestOnly) {
        this._suspendOnErrorTestOnly = _suspendOnErrorTestOnly;
    }

}
