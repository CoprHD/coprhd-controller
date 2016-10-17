/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.protectionorchestrationcontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.model.block.Copy;

public interface ProtectionOrchestrationController extends Controller {
    public final static String PROTECTION_ORCHESTRATION_DEVICE = "protection-orchestration";
    
    /**
     * Performs an arbitrary protection operation using SRDF. Then is run through a (new)
     * Orchestration Controller because depending on the operation Vplex cache flush operations
     * may need to be executed.
     * @param storageSystemId (URI)
     * @param copy - Copy object
     * @param op - String operation name
     * @param task - String task id
     */
    public void performSRDFProtectionOperation(
            URI storageSystemId, Copy copy, String op, String task);
}
