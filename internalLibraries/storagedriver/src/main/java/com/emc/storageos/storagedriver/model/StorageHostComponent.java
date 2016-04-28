/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver.model;

import java.util.Set;

/**
 * Host component which is part of Storage System.
 * For example SDC host in scaleio cluster
 */
public class StorageHostComponent extends StorageObject {

    private String type;
    private String hostName;
    // Indicates if driver supports firmware version of the system.
    private boolean isSupportedVersion;

    // Host initiators
    private Set<Initiator> initiators;


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public boolean isSupportedVersion() {
        return isSupportedVersion;
    }

    public void setIsSupportedVersion(boolean isSupportedVersion) {
        this.isSupportedVersion = isSupportedVersion;
    }

    public Set<Initiator> getInitiators() {
        return initiators;
    }

    public void setInitiators(Set<Initiator> initiators) {
        this.initiators = initiators;
    }
}
