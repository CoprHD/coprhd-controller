/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

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
