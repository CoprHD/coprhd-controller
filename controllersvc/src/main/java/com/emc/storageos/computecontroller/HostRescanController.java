/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller;

import java.net.URI;

import com.emc.storageos.Controller;

public interface HostRescanController extends Controller {
    final String HOST_RESCAN_DEVICE = "hostRescan";
    
    /**
     * Rescans the host storage for changes in paths, luns.
     * @param hostId -- URI of host
     * @param taskId -- taskId for step completion
     */
    public void rescanHostStorage(URI hostId, String taskId);
    
    /**
     * As part of NDM work, we wanted to expose this method directly through RMI call.
     * Currently existing method is designed to be attached as a step, but that will not help.
     * No other way other than duplicating this method
     * @param hostId
     * @param taskId
     */
    public void rescanHostStoragePaths(URI hostId, String taskId);

}
