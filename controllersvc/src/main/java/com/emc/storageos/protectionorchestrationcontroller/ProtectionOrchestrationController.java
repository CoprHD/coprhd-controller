package com.emc.storageos.protectionorchestrationcontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.model.block.Copy;

public interface ProtectionOrchestrationController extends Controller {
    public final static String PROTECTION_ORCHESTRATION_DEVICE = "protection-orchestration";
    
    public void performSRDFProtectionOperation(
            URI storageSystemId, Copy copy, String op, String task);
}
