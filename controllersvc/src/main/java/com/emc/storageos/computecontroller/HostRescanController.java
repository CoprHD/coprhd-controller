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

}
