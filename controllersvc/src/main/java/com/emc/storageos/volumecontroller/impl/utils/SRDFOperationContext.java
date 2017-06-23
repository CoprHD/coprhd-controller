/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.WorkflowService;

/**
 * This class helps with storing workflow step data for the purpose of
 * improved SRDF rollback functionality.
 */
public class SRDFOperationContext implements Serializable {

    private static final long serialVersionUID = 4053042837548721976L;
    private List<SRDFOperationContextEntry> entries;

    public enum SRDFOperationType {
        CHANGE_VPOOL_ON_SOURCE
    }

    public class SRDFOperationContextEntry implements Serializable{

        private static final long serialVersionUID = 5743599463209441325L;
        private String operation;
        private List<Object> args;

        public SRDFOperationContextEntry(String operation, Object... args) {
            this.operation = operation;
            this.args = newArrayList(args);
        }

        public String getOperation() {
            return operation;
        }

        public List<Object> getArgs() {
            return args;
        }
    }

    public void insertOperation(String operation, Object... args) {
        SRDFOperationContextEntry entry = new SRDFOperationContextEntry(operation, args);
        getEntries().add(entry);
    }

    public List<SRDFOperationContextEntry> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public static void insertContextOperation(TaskCompleter completer, SRDFOperationType operation, Object... args) {
        checkNotNull(completer);

        WorkflowService service = WorkflowService.getInstance();
        SRDFOperationContext ctx = (SRDFOperationContext) service.loadStepData(completer.getOpId());

        if (ctx == null) {
            ctx = new SRDFOperationContext();
        }

        ctx.insertOperation(operation.toString(), args);
        service.storeStepData(completer.getOpId(), ctx);
    }
}
