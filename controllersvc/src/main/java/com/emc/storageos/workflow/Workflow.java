/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.workflow;

import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.impl.GenericSerializer;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * A Workflow represents a sequence of steps that can be executed by Controllers to
 * achieve a higher level goal.
 * 
 * @author Watson 2/26/2013 revised 3/1/2013
 */
public class Workflow implements Serializable {
    // State variables in the Workflow, should not be used by clients.
    private static final long serialVersionUID = -7097852372832495267L;
    WorkflowService _service;       // WorkflowService manages this Workflow

    String _orchControllerName;     // simple name of the Orchestration Controller
    String _orchMethod;             // Orchestration Method Name we are working
    String _orchTaskId;             // Orchestration taskId
    String _successMessage;         // Message that will be emitted if successful completion
    Boolean _rollbackContOnError;   // Rollback continues even if a rollback step fails
    Boolean _rollbackState = false; // this workflow is in a rollback state
    // The current steps in the Workflow
    Map<String, Step> _stepMap = new HashMap<String, Step>();
    // maps stepGroup to step id list
    Map<String, Set<String>> _stepGroupMap = new HashMap<String, Set<String>>();
    Map<String, StepStatus> _stepStatusMap = new LinkedHashMap<String, StepStatus>();
    WorkflowCallbackHandler _callbackHandler;// a callback handler
    Object[] _callbackHandlerArgs;  // arguments for the callback handlers (serializable)
    WorkflowRollbackHandler _rollbackHandler; // rollback handlers
    Object[] _rollbackHandlerArgs;  // arguments for the rollback handlers (serializable)
    TaskCompleter _taskCompleter;   // task completer to be called at end of workflow
    Boolean _nested = false;   // this workflow is nested, run from within another workflow
    URI _workflowURI;            // URI for Cassandra logging record
    Set<URI> _childWorkflows = new HashSet<URI>();	// workflowURI of child workflows

    // Define the serializable, persistent fields save in ZK
    private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("_orchControllerName", String.class),
            new ObjectStreamField("_orchMethod", String.class),
            new ObjectStreamField("_orchTaskId", String.class),
            new ObjectStreamField("_successMessage", String.class),
            new ObjectStreamField("_callbackHandler", WorkflowCallbackHandler.class),
            new ObjectStreamField("_callbackHandlerArgs", Object[].class),
            new ObjectStreamField("_rollbackHandler", WorkflowRollbackHandler.class),
            new ObjectStreamField("_rollbackHandlerArgs", Object[].class),
            new ObjectStreamField("_taskCompleter", TaskCompleter.class),
            new ObjectStreamField("_stepGroupMap", Map.class),
            new ObjectStreamField("_rollbackContOnError", Boolean.class),
            new ObjectStreamField("_rollbackState", Boolean.class),
            new ObjectStreamField("_workflowURI", URI.class),
            new ObjectStreamField("_childWorkflows", Set.class),
            new ObjectStreamField("_nested", Boolean.class),
            new ObjectStreamField("_stepMap", Map.class),
            new ObjectStreamField("_stepStatusMap", Map.class),
    };

    private static final Logger _log = LoggerFactory.getLogger(Workflow.class);

    /**
     * The state of a Step.
     */
    static public class Step implements Serializable {
        private static final long serialVersionUID = -3350633739681733597L;
        /** A unique UUID string identifying each step. */
        public String stepId;
        /** A human readable description of what the step does. */
        public String description;
        /** Every step belongs to a StepGroup. This is the stepGroup name. */
        public String stepGroup;
        /**
         * If non-null, a stepId or stepGroup name that must complete before
         * this step executes.
         */
        public String waitFor;
        /** The underlying device URI for this step (e.g. StorageSystem). */
        public URI deviceURI;
        /** The device type (e.g. device.getType() ) */
        public String deviceType;
        /** tells the dispatcher whether to take out a semaphore lock on the device. */
        public boolean lockDevice;
        /** The name of the controller used to execute the step. */
        public String controllerName;
        /** The method parameters used to execute the step. */
        public Method executeMethod;
        /** The Method parameters to rollback the initial call. */
        public Method rollbackMethod;
        /** The current status of the step. */
        public StepStatus status;
        /** URI of Cassandra logging record. */
        URI workflowStepURI;

        /**
         * Created COP-37 to track hashCode() implemenatation in this class.
         */
        @SuppressWarnings({ "squid:S1206" })
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Step)) {
                return false;
            }

            Step other = (Step) o;
            return this.stepId.equalsIgnoreCase(other.stepId);
        }

        // Rollback steps are in the rollback step group.
        public static final String ROLLBACK_GROUP = "_rollback_group_";

        public boolean isRollbackStep() {
            return this.stepGroup.equals(ROLLBACK_GROUP);
        }

        /**
         * Generates a rollback Step corresponding to this step.
         * 
         * @return Step
         */
        public Step generateRollbackStep() {
            Step rb = new Step();
            rb.stepId = UUID.randomUUID().toString() + UUID.randomUUID().toString();
            rb.description = "Rollback " + this.description;
            rb.waitFor = null;
            rb.deviceURI = this.deviceURI;
            rb.deviceType = this.deviceType;
            rb.controllerName = this.controllerName;
            rb.executeMethod = this.rollbackMethod;
            rb.stepGroup = ROLLBACK_GROUP;
            return rb;
        }
    };

    /**
     * The States a Step may be in.
     */
    public enum StepState implements Serializable {
        /** step has been created, but not queued */
        CREATED,
        /** step is waiting on a prerequisite step to complete before dispatching. */
        BLOCKED,
        /** step has been queued to the Dispatcher, but no reported execution */
        QUEUED,
        /** step has been reported as executing */
        EXECUTING,
        /** step was cancelled due to failure of a step this step was dependent on (terminal state) */
        CANCELLED,
        /** step was successfully completed (terminal state) */
        SUCCESS,
        /** step completed in error (terminal state) */
        ERROR;
        /** Returns the equivalent Operation.Status value */
        public Operation.Status getOperationStatus() {
            if (this == SUCCESS) {
                return Operation.Status.ready;
            }
            if (this == CANCELLED || this == ERROR) {
                return Operation.Status.error;
            }
            return Operation.Status.pending;
        }

        public ServiceCode getServiceCode() {
            switch (this) {
                case CANCELLED:
                    return ServiceCode.WORKFLOW_STEP_CANCELLED;
                case ERROR:
                    return ServiceCode.WORKFLOW_STEP_ERROR;
                default:
                    return null;
            }
        }
    }

    /**
     * The current status of a Step. This is similar to, but not dependent on, Operation.
     */
    static public class StepStatus implements Serializable {
        private static final long serialVersionUID = -9046185136835089364L;
        /** String UUID step identifier */
        public String stepId;
        /** The current StepState */
        public StepState state;
        /** A message from the underlying controller */
        public String message;
        /** Human readable description of what this step is doing */
        public String description;
        /** Time step was queued to the Dispatcher */
        public Date startTime;
        /** Time step reached a terminal state */
        public Date endTime;
        /** The service code for an error state */
        public ServiceCode serviceCode;

        StepStatus() {
        }

        StepStatus(String stepId, StepState state, String description) {
            this.stepId = stepId;
            this.state = state;
            this.description = description;
            this.state = StepState.CREATED;
        }

        /**
         * @return True if the step is in a Terminal State, i.e. CANCELLED, ERROR, or SUCCESS
         */
        boolean isTerminalState() {
            return (state == StepState.CANCELLED
                    || state == StepState.ERROR || state == StepState.SUCCESS);
        }

        ServiceError getServiceError() {
            return ServiceError.buildServiceError(serviceCode, message);
        }

        /**
         * Called to update the state when a callback message is received in
         * Zookeeper as a result of a WorkflowStepCompleter.updateState().
         * Note that if there are any threads waiting on this step, they will be
         * awakened so that they will recheck the Step's status.
         * 
         * @param newState The new state reported.
         * @param message Message from the controller.
         */
        synchronized void updateState(StepState newState, ServiceCode code, String message) {
            this.state = newState;
            this.message = message;
            this.serviceCode = code;
            if (newState == StepState.QUEUED || newState == StepState.CANCELLED) {
                this.startTime = new Date();
            }
            if (newState == StepState.CANCELLED
                    || newState == StepState.SUCCESS || newState == StepState.ERROR) {
                this.endTime = new Date();
            }
            this.serviceCode = code == null ? newState.getServiceCode() : code;
            this.notifyAll();
        }

        /**
         * Block the calling thread until this step reaches a terminal state.
         */
        synchronized void waitForTerminalState() {
            while (this.state == StepState.BLOCKED
                    || this.state == StepState.QUEUED
                    || this.state == StepState.EXECUTING) {
                try {
                    this.wait(600000); // 600 seconds, or 10 minutes
                } catch (InterruptedException ex) {
                    _log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    /**
     * Represents a method that can be called by the Workflow.
     * It would be an executeMethod or a rollbackMethod.
     * 
     */
    static public class Method implements Serializable {
        /** The methodName for this method. This is the function that will be called. */
        String methodName;
        /** Arbitrary arguments for the method. Must be serializable. */
        Object[] args;

        public Method(String methodName, Object... args) {
            this.methodName = methodName;
            this.args = args;
        }

        public void checkSerialization() throws WorkflowException {
            byte[] bytes = GenericSerializer.serialize(this);
        }
    }

    /**
     * The interface that must be provided as the workflow callback handler.
     */
    public interface WorkflowCallbackHandler {
        /** The workflow is completed. */
        public void workflowComplete(Workflow workflow, Object[] args) throws WorkflowException;
    }

    /**
     * The interface that must be provided for rollback callback handler.
     */
    public interface WorkflowRollbackHandler {
        /** The workflow is initiating rollback. */
        public void initiatingRollback(Workflow workflow, Object[] args);

        /** The workflow has completed rollback. */
        public void rollbackComplete(Workflow workflow, Object[] args);
    }

    /**
     * Constructor to be called by WorkflowService. NOT TO BE CALLED BY CLIENTS.
     * 
     * @param service - Handle to the WorkflowService
     * @param orchControllerName - The simple name of the Controller on behalf this Workflow is executing.
     * @param methodName - Method within the controller on
     * @param rollbackContOnError -- Run all rollback steps, even if a rollback ERRORs?
     * @param stepId - String taskId (UUID) representing this specific orcestration instance.
     * @param workflowURI - URI desired for workflow, or can be passed as null and will be generated
     */
    Workflow(WorkflowService service, String orchControllerName, String methodName, String taskId, URI workflowURI) {
        _service = service;
        _orchControllerName = orchControllerName;
        _orchMethod = methodName;
        _orchTaskId = taskId;
        _workflowURI = workflowURI;
    }

    /**
     * Constructor to be called by WorkflowService. NOT TO BE CALLED BY CLIENTS.
     * Used to locate Workflows by their URI.
     * 
     * @param service - Handle to the WorkflowService
     * @param orchControllerName - The simple name of the Controller on behalf this Workflow is executing.
     * @param methodName - Method within the controller on
     * @param workflowURI - URI of existing Workflow
     */
    Workflow(WorkflowService service, String orchControllerName, String methodName, URI workflowURI) {
        _service = service;
        _orchControllerName = orchControllerName;
        _orchMethod = methodName;
        _workflowURI = workflowURI;
    }

    /**
     * Destroys the persisted (Zookeeper) state of this Workflow.
     * This is a destructor called from the WorkflowController.
     */
    void destroyWorkflow() {
        _service.destroyWorkflow(this);
    }

    /**
     * Check that the method is defined in the controller.
     * 
     * @param controller
     * @param methodName
     * @throws WorkflowException
     */
    private void methodNameValidator(Class controllerClass, String methodName)
            throws WorkflowException {
        java.lang.reflect.Method[] methods = controllerClass.getMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.getName().equals(methodName)) {
                return;
            }
        }
        throw new WorkflowException(String.format(
                "In class %s there is no method matching %s",
                controllerClass.getSimpleName(), methodName));
    }

    /**
     * Creates a step id for use with createStep().
     * 
     * @return String stepId
     */
    public String createStepId() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }

    /**
     * Creates a step for execution on an internal Queue within the Workflow and
     * returns after internally generating a step UUID for the step. The step
     * is not executable until one or methods have been set
     * (setExecutableMethod or setRollbackMethod) and the Workflow.execute()
     * call has been initiated.
     * 
     * <p>
     * 
     * @param stepGroup
     *            -- Step group name this step is a member of. Other steps can
     *            wait until all the steps in the specified stepGroup have
     *            completed. Do not use UUID values for stepGroup names.
     *            You can pass null if this step should not belong to any
     *            step groups.
     * @param description
     *            -- Short textual description of the step for logging/status
     *            displays.
     * @param waitFor
     *            -- If non-null, the step will not be queued for execution in
     *            the Dispatcher until the Step or StepGroup indicated by the
     *            waitFor has completed. The waitFor may either be a string
     *            representation of a Step UUID, or the name of a StepGroup.
     * @param deviceURI
     *            -- The URI of the affected device, e.g. StorageSystem or
     *            NetworkSystem. This is a required parameter to the Dispatcher,
     *            who maintains a semaphore count on the number of outstanding
     *            operations to each device instance.
     * @param deviceType
     *            --The type of Device, used to find the controller. Typically
     *            given by device.getDeviceType(). This is a required
     *            parameter to the Dispatcher.
     * @param controllerClass -- The controller class (like
     *            NetworkDeviceController.class, BlockDeviceController.class)
     * @param executeMethod - Method name and parameters for the execution method.
     * @param rollbackMethod - Method name name parameters for the rollback method.
     * @param stepId - If non null, specifies the stepId to be used, otherwise if null a stepId is
     *            automatically generated.
     * 
     * @return String representing UUID of generated step
     */
    public String createStep(String stepGroup, String description,
            String waitFor, URI deviceURI, String deviceType,
            Class controllerClass, Method executeMethod, Method rollbackMethod,
            String stepId)
            throws WorkflowException {
        return createStep(stepGroup, description, waitFor, deviceURI, deviceType, true, controllerClass,
                executeMethod, rollbackMethod, stepId);
    }

    /**
     * Creates a step for execution on an internal Queue within the Workflow and
     * returns after internally generating a step UUID for the step. The step
     * is not executable until one or methods have been set
     * (setExecutableMethod or setRollbackMethod) and the Workflow.execute()
     * call has been initiated.
     * 
     * <p>
     * 
     * @param stepGroup
     *            -- Step group name this step is a member of. Other steps can
     *            wait until all the steps in the specified stepGroup have
     *            completed. Do not use UUID values for stepGroup names.
     *            You can pass null if this step should not belong to any
     *            step groups.
     * @param description
     *            -- Short textual description of the step for logging/status
     *            displays.
     * @param waitFor
     *            -- If non-null, the step will not be queued for execution in
     *            the Dispatcher until the Step or StepGroup indicated by the
     *            waitFor has completed. The waitFor may either be a string
     *            representation of a Step UUID, or the name of a StepGroup.
     * @param deviceURI
     *            -- The URI of the affected device, e.g. StorageSystem or
     *            NetworkSystem. This is a required parameter to the Dispatcher,
     *            who maintains a semaphore count on the number of outstanding
     *            operations to each device instance.
     * @param deviceType
     *            --The type of Device, used to find the controller. Typically
     *            given by device.getDeviceType(). This is a required
     *            parameter to the Dispatcher.
     * @param lockDevice
     *            tells the dispatcher whether to acquire a semaphore on the device
     *            before executing the step
     * @param controllerClass -- The controller class (like
     *            NetworkDeviceController.class, BlockDeviceController.class)
     * @param executeMethod - Method name and parameters for the execution method.
     * @param rollbackMethod - Method name name parameters for the rollback method.
     * @param stepId - If non null, specifies the stepId to be used, otherwise if null a stepId is
     *            automatically generated.
     * 
     * @return String representing UUID of generated step
     */
    public String createStep(String stepGroup, String description,
            String waitFor, URI deviceURI, String deviceType, boolean lockDevice,
            Class controllerClass, Method executeMethod, Method rollbackMethod,
            String stepId)
            throws WorkflowException {
        try {
            // Initialize the new step.
            Step step = new Step();
            if (stepId == null) {
                stepId = createStepId();
            }
            step.stepId = stepId;
            step.stepGroup = stepGroup;
            step.description = description;
            step.waitFor = waitFor;
            step.deviceURI = deviceURI;
            step.deviceType = deviceType;
            step.lockDevice = lockDevice;
            step.controllerName = controllerClass.getName();
            // Make a StepStatus entry for it with CREATED status
            step.status = new StepStatus(stepId, StepState.CREATED, description);

            // Save it in our local structures, the stepMap, stepStatusMap, and the stepGroup map.
            getStepMap().put(step.stepId, step);
            if (step.stepGroup != null) {
                if (getStepGroupMap().get(step.stepGroup) == null) {
                    getStepGroupMap().put(step.stepGroup, new HashSet<String>());
                }
                getStepGroupMap().get(step.stepGroup).add(step.stepId);
            }
            getStepStatusMap().put(stepId, step.status);

            // Check the execution method.
            if (executeMethod == null) {
                throw new WorkflowException("Must supply an executeMethod argument");
            }
            methodNameValidator(controllerClass, executeMethod.methodName);
            executeMethod.checkSerialization();
            step.executeMethod = executeMethod;

            // The rollback method is optional...
            if (rollbackMethod != null) {
                methodNameValidator(controllerClass, rollbackMethod.methodName);
                rollbackMethod.checkSerialization();
                step.rollbackMethod = rollbackMethod;
            }

            return step.stepId;
        } catch (Exception ex) {
            _log.error("Exception trying to create workflow step: " + ex.getMessage());
            throw new WorkflowException("Cannot create step", ex);
        }
    }

    /**
     * Invokes the WorkflowPlanExecutor to execute this workflow plan.
     */
    public void executePlan(TaskCompleter completer, String successMessage,
            WorkflowCallbackHandler callbackHandler, Object[] callbackHandlerArgs,
            WorkflowRollbackHandler rollbackHandler, Object[] rollbackHandlerArgs)
            throws WorkflowException {
        this._callbackHandler = callbackHandler;
        if (callbackHandlerArgs != null) {
            this._callbackHandlerArgs = callbackHandlerArgs.clone();
        }
        this._rollbackHandler = rollbackHandler;
        if (rollbackHandlerArgs != null) {
            this._rollbackHandlerArgs = rollbackHandlerArgs.clone();
        }
        this._taskCompleter = completer;
        this._successMessage = successMessage;
        _service.executePlan(this);
    }

    public void executePlan(TaskCompleter completer, String successMessage)
            throws WorkflowException {
        executePlan(completer, successMessage, null, null, null, null);
    }

    /**
     * Returns the current step status without waiting (i.e. even if it is in
     * the pending state). Does not block.
     * 
     * @param stepId
     * @return StepStatus
     */
    public StepStatus getStepStatus(String stepId) throws WorkflowException {
        StepStatus status = getStepStatusMap().get(stepId);
        if (status == null) {
            throw new WorkflowException("Unknown step: " + stepId);
        }
        return status;
    }

    /**
     * @return True if all Steps have reached a Terminal State
     * @throws WorkflowException
     */
    boolean allStatesTerminal() throws WorkflowException {
        for (String stepId : getStepMap().keySet()) {
            StepStatus status = getStepStatus(stepId);
            if (false == status.isTerminalState()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a Map of UUID to step Status, even if some of the steps
     * have not completed execution. Does not block.
     * 
     * @param stepGroup
     *            - The String name of a Step Group.
     * @return -- A Map of step UUID String to StepStatus structure.
     */
    public Map<String, StepStatus> getStepGroupStatus(String stepGroup)
            throws WorkflowException {
        HashMap<String, StepStatus> map = new HashMap<String, StepStatus>();
        Set<String> stepGroupList = getStepGroupMap().get(stepGroup);
        if (stepGroupList == null) {
            throw new WorkflowException("Couldn't find stepGroup: " + stepGroup);
        }
        for (String stepId : getStepGroupMap().get(stepGroup)) {
            StepStatus status = getStepStatus(stepId);
            map.put(stepId, status);
        }
        return map;
    }

    /**
     * Returns a Map of UUID to step Status for all steps in the Workflow, even if
     * some of the steps have not completed execution. Does not block.
     * 
     * @return -- A Map of step UUID String to StepResourceRep structure.
     * @throws WorkflowException
     */
    public Map<String, StepStatus> getAllStepStatus() throws WorkflowException {
        HashMap<String, StepStatus> map = new HashMap<String, StepStatus>();
        for (String stepId : getStepMap().keySet()) {
            StepStatus status = getStepStatus(stepId);
            map.put(stepId, status);
        }
        return map;
    }

    /**
     * Search through the step map and find out if one of the Step has 'methodName'
     * as its Workflow.Method
     * 
     * @param controllerClass [IN] - Controller class for the step we're searching for
     * @param deviceURI [IN] - Device URI for which the step applies
     * @param methodName [IN] - Workflow.Method.methodName to search for
     *
     * @return true, if there is a Step with Workflow.Method.methodName == 'methodName'
     */
    public boolean stepMethodHasBeenScheduled(Class controllerClass, URI deviceURI, String methodName) {
        for (String stepId : getStepMap().keySet()) {
            Step step = getStepMap().get(stepId);
            Workflow.Method method = step.executeMethod;
            if (method.methodName.equals(methodName) && step.controllerName.equals(controllerClass.getName())
                    && step.deviceURI.equals(deviceURI)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given a group of steps, determines an overall state. The precedence is:
     * 1. If any step is reporting ERROR, ERROR is returned along with that step's message.
     * 2. Otherwise if any step is reporting CANCELLED, CANCELLED is returned along with that step's message.
     * 3. Otherwise if any step is not returning a state of SUCCESS, CANCELLED, or ERROR, it's state and message are returned.
     * 4. Otherwise if all steps are returning SUCCESS, SUCCESS is returned with the original contents of errorMessage
     * (unless they were null).
     * 
     * @param statusMap
     * @param errorMessage -- Output parameter - selected error message
     * @return SUCCESS if all successful; ERROR for first error; other StepState if there is a non SUCCESS/ERROR
     */
    public static StepState getOverallState(Map<String, StepStatus> statusMap,
            String[] errorMessage)
            throws WorkflowException {
        StepState state = StepState.SUCCESS;
        StringBuilder buf = new StringBuilder();    // Buffer for error messages
        StringBuilder rbuf = new StringBuilder();    // Buffer for rollback error messages
        if (errorMessage[0] == null) {
            errorMessage[0] = "Operation successful";
        }

        for (String stepId : statusMap.keySet()) {
            StepStatus status = statusMap.get(stepId);
            switch (status.state) {
                case SUCCESS:
                    break;
                case ERROR:
                    state = StepState.ERROR;
                    if (false == status.description.startsWith("Rollback")) {
                        // Save non-rollback message
                        if (buf.length() > 0) {
                            buf.append("; ");
                        }
                        buf.append(status.message);
                    } else {
                        // Save rollback message
                        rbuf.append("; Rollback error: ");
                        rbuf.append(status.message);
                    }
                    break;
                case CANCELLED: // ERROR has higher precedence than CANCELLED
                    if (state != StepState.ERROR) {
                        state = StepState.CANCELLED;
                        errorMessage[0] = status.message;
                        break;
                    }
                default: // ERROR and CANCELLED have higher precedence than any default state
                    if (state != StepState.ERROR && state != StepState.CANCELLED) {
                        state = status.state;
                        errorMessage[0] = status.message;
                        break;
                    }
                    break;
            }
        }
        // If there's an error, replace the success message
        if (buf.length() > 0) {
            errorMessage[0] = buf.toString() + rbuf.toString();
        }
        return state;
    }

    public static ServiceError getOverallServiceError(Map<String, StepStatus> statusMap)
            throws WorkflowException {
        StepState state = null;
        ServiceError error = null;
        for (String stepId : statusMap.keySet()) {
            StepStatus status = statusMap.get(stepId);
            switch (status.state) {
                case ERROR:
                    if (state != StepState.ERROR) { // we want to record the root error, the first one
                        state = StepState.ERROR;
                        error = ServiceError.buildServiceError(status.serviceCode, status.message);
                        break;
                    }
                case CANCELLED: // ERROR has higher precedence than CANCELLED
                    if (state != StepState.ERROR) {
                        state = StepState.CANCELLED;
                        error = ServiceError.buildServiceError(status.serviceCode, status.message);
                    }
                    break;
                case SUCCESS:
                default:
                    break;
            }
        }
        return error;
    }

    Map<String, Step> getStepMap() {
        return _stepMap;
    }

    void setStepMap(Map<String, Step> stepMap) {
        this._stepMap = stepMap;
    }

    Map<String, Set<String>> getStepGroupMap() {
        return _stepGroupMap;
    }

    void setStepGroupMap(Map<String, Set<String>> stepGroupMap) {
        this._stepGroupMap = stepGroupMap;
    }

    public String getOrchControllerName() {
        return _orchControllerName;
    }

    public String getOrchMethod() {
        return _orchMethod;
    }

    public String getOrchTaskId() {
        return _orchTaskId;
    }

    Map<String, StepStatus> getStepStatusMap() {
        return _stepStatusMap;
    }

    public Boolean getRollbackContOnError() {
        return _rollbackContOnError;
    }

    public void setRollbackContOnError(Boolean rollbackContOnError) {
        this._rollbackContOnError = rollbackContOnError;
    }

    public Boolean isRollbackState() {
        return _rollbackState;
    }

    public void setRollbackState(Boolean rollbackState) {
        this._rollbackState = rollbackState;
    }

    public URI getWorkflowURI() {
        return _workflowURI;
    }

    public void setWorkflowURI(URI _workflowURI) {
        this._workflowURI = _workflowURI;
    }

    public WorkflowService getService() {
        return _service;
    }

    public void setService(WorkflowService _service) {
        this._service = _service;
    }

}
