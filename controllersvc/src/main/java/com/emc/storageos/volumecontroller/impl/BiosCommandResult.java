/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.util.List;

import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;

/**
 * Base command result to describe success/failure plus error codes/messages
 * 
 * @author burckb
 * 
 */
public class BiosCommandResult {

    protected ServiceCoded _serviceCoded;
    protected boolean _commandSuccess = false;
    protected boolean _commandPending = false;
    protected String _commandStatus;
    protected List<Object> _objectList;
    
    // TODO: Only required while migrating the code to use the non-deprecated methods
    protected String _message;

    public BiosCommandResult() {
        super();
    }

    /**
     * @deprecated use {@link #BiosCommandResult()} then {@link #error(ServiceCoded)} or {@link #success()}
     */
    @Deprecated
    public BiosCommandResult(boolean success, String status, String message) {
        this(success, status, null, message);
    }

    /**
     * @deprecated use {@link #BiosCommandResult()} then {@link #error(ServiceCoded)} or {@link #success()}
     */
    @Deprecated
    public BiosCommandResult(boolean success, String status, ServiceCode code, String message) {
        _commandSuccess = success;
        _commandStatus = status;
        _message = message;
        if (code != null) {
            _serviceCoded = ServiceError.buildServiceError(code, message);
        }
    }

    /**
     * Creates a command result with error status and the given message
     * 
     * @param message
     * @return BiosCommandResult
     * @deprecated use {@link #createErrorResult(ServiceCoded)}
     */
    @Deprecated
    public static BiosCommandResult createErrorStatus(String message) {
        return createErrorStatus(ServiceCode.CONTROLLER_ERROR, message);
    }

    /**
     * Creates a command result with error status, the specified service code
     * and the given message
     * 
     * @param code
     * @param message
     * @return BiosCommandResult
     * @deprecated use {@link #createErrorResult(ServiceCoded)}
     */
    @Deprecated
    public static BiosCommandResult createErrorStatus(ServiceCode code, String message) {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(false);
        result.setCommandStatus(Operation.Status.error.name());
        result.setServiceCoded(ServiceError.buildServiceError(code, message));
        result.setMessage(message);
        return result;
    }

    /**
     * Creates a command result with error status using the service code and
     * message from the {@link ServiceCoded}
     * 
     * @param coded
     * @return
     */
    public static BiosCommandResult createErrorResult(final ServiceCoded coded) {
        BiosCommandResult result = new BiosCommandResult();
        result.error(coded);
        return result;
    }

    /**
     * Creates a command result with ready status and the the specified message
     * 
     * @return
     */
    public static BiosCommandResult createSuccessfulResult() {
        BiosCommandResult result = new BiosCommandResult();
        result.success();
        return result;
    }

    /**
     * Creates a command result with ready status and the the specified message
     * 
     * @return
     */
    public static BiosCommandResult createPendingResult() {
        BiosCommandResult result = new BiosCommandResult();
        result.pending();
        return result;
    }

    public ServiceCoded getServiceCoded() {
        return _serviceCoded;
    }

    public void setServiceCoded(ServiceCoded coded) {
        _serviceCoded = coded;
    }

    public void error(ServiceCoded coded) {
        // TODO: Once this methods are not use outside this class, we need to make them private
        setCommandStatus(Operation.Status.error.name());
        _commandSuccess = false;
        setServiceCoded(coded);
        setMessage(coded.getMessage());
    }

    public void success() {
        // TODO: Once this methods are not use outside this class, we need to make them private
        setCommandStatus(Operation.Status.ready.name());
        _commandSuccess = true;
    }

    public void pending() {
        setCommandStatus(Operation.Status.pending.name());
        _commandPending = true;
    }

    public boolean isCommandSuccess() {
        return _commandSuccess;
    }

    /**
     * @deprecated use {@link #error(ServiceCoded)} or {@link #success()}
     */
    @Deprecated
    public void setCommandSuccess(boolean commandSuccess) {
        // TODO: Once this method is not use outside this class, we need to make it private
        _commandSuccess = commandSuccess;
    }

    public boolean getCommandSuccess() {
        return _commandSuccess;
    }

    public boolean getCommandPending() {
        return _commandPending;
    }

    public String getCommandStatus() {
        return _commandStatus;
    }

    /**
     * @deprecated use {@link #error(ServiceCoded)} or {@link #success()}
     */
    @Deprecated
    public void setCommandStatus(String commandStatus) {
        // TODO: Once this method is not use outside this class, we need to make it private
        _commandStatus = commandStatus;
        _commandSuccess = commandStatus.toLowerCase().equals(Status.ready.name());
    }

    public List<Object> getObjectList() {
        return _objectList;
    }

    public void setObjectList(List<Object> objectList) {
        _objectList = objectList;
    }

    public String getMessage() {
        return _message;
    }

    /**
     * @deprecated use {@link #error(ServiceCoded)} or {@link #success()}
     */
    @Deprecated
    public void setMessage(String message) {
        _message = message;
    }

    public Operation toOperation() {
        Operation operation = new Operation();

        if (_commandSuccess) {
            operation.ready();
        } else if (!_commandPending) {
            operation.error(_serviceCoded);
        }
        return operation;
    }

}
