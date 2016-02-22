/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common;

import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.commandgenerator.Command;
import com.emc.storageos.plugins.common.commandgenerator.CommandGenerator;
import com.emc.storageos.plugins.common.domainmodel.Namespace;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMArgument;
import javax.cim.CIMProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMIExecutor- responsible for executing SMICommands
 * 
 */
public abstract class Executor {
    /**
     * Logger.
     */
    protected static final Logger _LOGGER = LoggerFactory.getLogger(Executor.class);
    private static final String NEWLINE = "\n";
    private static final String SEMICOLON = "; ";
    private static final String TAB = "\t";
    /**
     * Discover Util.
     */
    protected Util _util;
    /**
     * Command Objects.
     */
    protected List<Command> _commandObjects;
    protected CommandGenerator _generator;

    /**
     * Generator.
     */
    private ExecutorService execService = null;
    /**
     * keyMap.
     */
    protected Map<String, Object> _keyMap;

    public void setUtil(Util _util) {
        this._util = _util;
    }

    public Util getUtil() {
        return _util;
    }

    public void setKeyMap(Map<String, Object> _keyMap) {
        this._keyMap = _keyMap;
    }

    public Map<String, Object> getKeyMap() {
        return _keyMap;
    }

    public void setGenerator(CommandGenerator _generator) {
        this._generator = _generator;
    }

    public CommandGenerator getGenerator() {
        return _generator;
    }

    public Executor() {
        execService = Executors.newFixedThreadPool(10);
    }

    public ExecutorService getExecService() {
        return execService;
    }

    /**
     * This execute method is common for all the plugins. It checks whether each
     * operation in Domain Logic has to get executed, then execute and move it
     * to processor.It loops through each operation in Domain Logic and sends to
     * executeoperation(), where the actual conversion of operation to Command
     * happens and each Command gets executed. The List of Result Objects will
     * then be sent to the Processor.
     * 
     * @throws BaseCollectionException
     *             ex.
     */
    public void execute(Namespace ns) throws BaseCollectionException {
        assert ns != null;
        for (Object operationobj : ns.getOperations()) {
            Operation operation = (Operation) operationobj;
            executeOperation(operation);
        }
    }

    /**
     * Move the result to Processor. Processor can be of type CIMProcessor,
     * DirectorMetrics Apply different types of Decorators above the processed
     * Result Multiple Decorators can be applied on the processed Result.
     * Multiple Processors can be applied on the result got from Executor.
     * 
     * @param operation
     *            : Domain Logic operation.
     * @param result
     *            : Result Object got from 3rd party instance.
     * @throws BaseCollectionException
     *             ex.
     */
    protected void processResult(final Operation operation, final Object result, final Command commandObj)
            throws BaseCollectionException {
        Processor _processor = null;
        // processor is being shared across multiple Threads in case of parallel
        // processing.
        // But still, processResult is not synchronized intentionally, as I want
        // multiple Threads to complete its job without waiting.
        // Things which get shared across threads is only the ConcurrentMap,
        // hence haven't made it synchronized.
        _processor = operation.getProcessor();
        if (null != _processor) {
            List<Object> argsList = new ArrayList<Object>();
            argsList.add(Util.normalizedReadArgs(_keyMap, commandObj.retreiveArguments()));
            argsList.add(commandObj.getCommandIndex());

            _processor.setPrerequisiteObjects(argsList);
            _processor.processResult(operation, result, _keyMap);
        } else {
            _LOGGER.debug("No Processors found to execute. ");
        }
    }

    /**
     * Every Plugin's Executor should override this method, and throw their own
     * Custom Exceptions with Messages.
     * 
     * @param e
     * @throws BaseCollectionException
     */
    protected abstract void customizeException(Exception e, Operation operation)
            throws BaseCollectionException;

    /**
     * Execute the Operation defined in Domain Logic XML. Use Generator to
     * return Command Objects for an Operation. Then execute them either in a
     * multi-threaded fashion or singleThreaded.
     * 
     * @param operation
     * @throws BaseCollectionException
     */
    private void executeOperation(Operation operation) throws BaseCollectionException {
        try {
            if (!isSupportedOperation(operation)) {
                _LOGGER.info("Filtered the operation {} as per instructions", operation.getMessage());
                return;
            }
            _LOGGER.info(null == operation.getMessage() ? "START Executing operation"
                    : "START :" + operation.getMessage());
            _commandObjects = _generator.returnCommandObjects(operation, _keyMap);
            // only sequential processing allowed. also avoiding too many calls to the Provider at
            // the same time.
            for (Command commandObj : _commandObjects) {
                printArgs(commandObj);
                Object resultObj = null;
                try {
                    resultObj = commandObj.execute();
                    processResult(operation, resultObj, commandObj);
                } catch (Exception e) {
                    _LOGGER.error("Execution failed for :", e);
                    // We do not want 'Provider/Firmware Not Supported Error' to get suppressed. check and throw again.
                    if (e instanceof SMIPluginException) {
                        int errorCode = ((SMIPluginException) e).getErrorCode();
                        if (errorCode == SMIPluginException.ERRORCODE_PROVIDER_NOT_SUPPORTED ||
                                errorCode == SMIPluginException.ERRORCODE_FIRMWARE_NOT_SUPPORTED ||
                                errorCode == SMIPluginException.ERRORCODE_OPERATIONFAILED) {
                            throw e;
                        }
                    }
                    // We want to fail this operation if any part of UnManagedVolume discovery is incomplete
                    Object o = getKeyMap().get(Constants.ACCESSPROFILE);
                    if (null != o && o instanceof AccessProfile) {
                        AccessProfile accessProfile = (AccessProfile) o;
                        if (Discovery_Namespaces.UNMANAGED_VOLUMES.name().equalsIgnoreCase(accessProfile.getnamespace())) {
                            _LOGGER.error("SMI-S Communication Failed for UnManagedVolume Discovery.");
                            throw e;
                        }
                    }
                }
            }
        } catch (final Exception e) {
            _LOGGER.error("Operation Execution failed : ", e);
            customizeException(e, operation);
        }
        _LOGGER.debug(null == operation.getMessage() ? "END Executing operation" : "END :" + operation.getMessage());
    }

    /**
     * Method to print arguments for debug purpose
     * 
     * @param commandObject
     */
    public void printArgs(Command commandObject) {
        try {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append(NEWLINE).append("{");
            for (Object obj : commandObject.retreiveArguments()) {
                if (null == obj) {
                    logMessage.append("NULL").append(SEMICOLON);
                } else {
                    if (obj instanceof CIMArgument<?>[]) {
                        logMessage.append("Input CIMArgument Array : {");
                        CIMArgument<?>[] outputArguments = (CIMArgument<?>[]) obj;
                        for (CIMArgument<?> outArg : outputArguments) {
                            if (null != outArg) {
                                logMessage.append(TAB);
                                logMessage
                                        .append("Name:")
                                        .append(null == outArg.getName() ? "unknown"
                                                : outArg.getName()).append(SEMICOLON);
                                logMessage
                                        .append("DataType:")
                                        .append(null == outArg.getDataType() ? "unknown"
                                                : outArg.getDataType()).append(SEMICOLON);
                                logMessage
                                        .append("Value :")
                                        .append(null == outArg.getValue() ? "unknown"
                                                : outArg.getValue()).append(SEMICOLON);
                            } else {
                                logMessage.append("NULL").append(SEMICOLON);
                            }
                        }
                        logMessage.append("}").append(SEMICOLON).append(NEWLINE);
                    } else if (obj instanceof CIMProperty<?>[]) {
                        logMessage.append(NEWLINE).append("Input CIMProperty Array : {");

                        CIMProperty<?>[] args = (CIMProperty<?>[]) obj;
                        for (CIMProperty<?> p : args) {
                            if (null != p) {
                                logMessage
                                        .append("Name:")
                                        .append(null == p.getName() ? "unknown" : p
                                                .getName()).append(SEMICOLON);
                                logMessage
                                        .append("DataType:")
                                        .append(null == p.getDataType() ? "unknown" : p
                                                .getDataType()).append(SEMICOLON);
                                logMessage
                                        .append("Value :")
                                        .append(null == p.getValue() ? "unknown" : p
                                                .getValue()).append(SEMICOLON);
                            }
                        }
                        logMessage.append("}").append(SEMICOLON).append(NEWLINE);
                    } else if (obj instanceof String[]) {
                        logMessage.append(Arrays.toString((String[]) obj)).append(
                                SEMICOLON);
                    } else {
                        logMessage.append(obj.toString()).append(SEMICOLON);
                    }
                }
            }
            logMessage.append("}");
            _LOGGER.debug(logMessage.toString());

        } catch (Exception e) {
            _LOGGER.debug("Logging operations failed", e);
        }
    }

    /**
     * return true always to support other device types.
     * 
     * @param operation
     * @return
     */
    protected boolean isSupportedOperation(Operation operation) {
        return true;
    }

}
