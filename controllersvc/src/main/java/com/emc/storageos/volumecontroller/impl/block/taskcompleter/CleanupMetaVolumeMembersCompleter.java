/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

import java.io.Serializable;
import java.net.URI;

public class CleanupMetaVolumeMembersCompleter extends VolumeTaskCompleter {

    private static final long serialVersionUID = 1L;

    URI volumeURI;
    boolean isWFStep;
    String opId;
    String sourceStepId;

    boolean isSuccess = true;
    private ServiceError error;

    public CleanupMetaVolumeMembersCompleter(URI volumeURI, boolean WFStep, String sourceStepId, String opId) {
        super(Volume.class, volumeURI, opId);
        this.volumeURI = volumeURI;
        isWFStep = WFStep;
        this.opId = opId;
        this.sourceStepId = sourceStepId;
    }


    public URI getVolumeURI() {
        return volumeURI;
    }

    public boolean isWFStep() {
        return isWFStep;
    }

    public String getOpId() {
        return opId;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getSourceStepId() {
        return sourceStepId;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public void complete(Workflow.StepState state, ServiceCoded serviceCoded) throws WorkflowException {
            switch (state) {
                case ERROR:
                    // update only if this is workflow step
                    if (isWFStep()) {
                       WorkflowStepCompleter.stepFailed(opId, serviceCoded);
                    }
                    break;

                case SUCCESS:
                default:
                    // update only if this is workflow step
                    if (isWFStep()) {
                        WorkflowStepCompleter.stepSucceded(getOpId());
                    }
            }
    }


    public ServiceError getError() {
        return error;
    }

    public void setError(ServiceError error) {
        this.error = error;
    }
}
