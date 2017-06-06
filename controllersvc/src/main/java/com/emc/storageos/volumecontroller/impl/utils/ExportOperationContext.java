/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxExportOperationContext;
import com.emc.storageos.workflow.WorkflowService;

/**
 * This object contains status information about export operations that occur
 * within a single workflow step. It benefits rollback operations by indicating
 * exactly what substeps occurred during creation that need to be rolled back.
 */
public class ExportOperationContext implements Serializable {

    private static final long serialVersionUID = 3452808872942655033L;
    private static final Logger _log = LoggerFactory.getLogger(VmaxExportOperationContext.class);
    List<ExportOperationContextOperation> operations;

    public ExportOperationContext() {
        super();
    }

    public class ExportOperationContextOperation implements Serializable {
        private static final long serialVersionUID = -4135846269841199964L;
        private String operation;

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public List<Object> getArgs() {
            return args;
        }

        public void setArgs(List<Object> args) {
            this.args = args;
        }

        private List<Object> args;

        @Override
        public String toString() {
            return "Operation [operation="
                    + operation + ", args=" + args + "]";
        }
    }

    public void insertOperation(String operation, Object... args) {
        // Order is important, put this at the end of the list.
        ExportOperationContextOperation op = new ExportOperationContextOperation();
        op.setOperation(operation);
        List<Object> opArgs = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                opArgs.add(arg);
            }
        }
        op.setArgs(opArgs);
        if (operations == null) {
            operations = new ArrayList<>();
        }
        operations.add(op);
        _log.info(String.format("Operation %s has been recorded for the benefit of potential rollback "
                + "in the event of overall failure.  %d operation%s been recorded for overall rollback.",
                operation, operations.size(), operations.size() == 1 ? " has" : "s have"));
    }

    public List<ExportOperationContextOperation> getOperations() {
        return operations;
    }

    public void setOperations(List<ExportOperationContextOperation> operations) {
        this.operations = operations;
    }

    @Override
    public String toString() {
        return "ExportOperationContext [operations=" + operations + "]";
    }

    /**
     * Inserts an operation into the step context so rollback will be done properly.
     *
     * @param taskCompleter task completer
     * @param args arguments needed to perform rollback for this individual operation.
     */
    public static void insertContextOperation(TaskCompleter taskCompleter, String operation, Object... args) {
        if (taskCompleter != null) {
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(taskCompleter.getOpId());
            if (context != null) {
                context.insertOperation(operation, args);
                WorkflowService.getInstance().storeStepData(taskCompleter.getOpId(), context);
            } else {
                _log.warn("Rollback context was not found for op: " + taskCompleter.getOpId());
            }
        }
    }
}