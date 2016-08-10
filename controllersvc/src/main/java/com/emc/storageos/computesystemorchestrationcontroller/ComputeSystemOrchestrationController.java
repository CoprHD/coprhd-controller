/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemorchestrationcontroller;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;

public interface ComputeSystemOrchestrationController extends Controller {
    public final static String COMPUTE_SYSTEM_ORCHESTRATION_DEVICE = "compute-system-orchestration";

    /**
     * mount file system export to host
     * 
     * @param hostId
     *            URI of the host
     * @param resId
     *            resource id, file system or snapshot
     * @param subDirectory
     *            sub directory
     * @param security
     *            security type
     * @param mountPath
     *            mount path
     * @param fsType
     *            fsType
     * @param taskId
     *            task id created by the API
     * @throws ControllerException
     */
    public void mountDevice(URI hostId, URI resId, String subDirectory, String security, String mountPath, String fsType, String opId)
            throws ControllerException;

    /**
     * Unmount file system export from host
     * 
     * @param hostId
     *            URI of the host
     * @param resId
     *            resource id, file system or snapshot
     * @param mountPath
     *            mount path
     * @param taskId
     *            task id created by the API
     * @throws ControllerException
     */
    public void unmountDevice(URI hostId, URI resId, String mountPath, String opId) throws ControllerException;
}
