package com.emc.storageos.computesystemorchestrationcontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;

public interface ComputeSystemOrchestrationController extends Controller {
    public final static String COMPUTE_SYSTEM_ORCHESTRATION_DEVICE = "compute-system-orchestration";

    public void mountDevice(URI hostId, URI resId, String subDirectory, String security, String mountPath, String fsType, String opId)
            throws ControllerException;

    public void unmountDevice(URI hostId, URI resId, String mountPath, String opId) throws ControllerException;
}
