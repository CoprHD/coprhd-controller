/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;

import java.util.List;

/**
 * This class describes how a storage volume is mapped to a host
 */

public class VolumeToHostExportInfo {

    String hostName; // FQDN of a host
    List<String> volumeNativeIds; // storage volumes native Ids
    List<Initiator> initiators; // List of host initiators
    List<StoragePort> targets;    // List of storage ports

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public List<String> getVolumeNativeIds() {
        return volumeNativeIds;
    }

    public void setVolumeNativeIds(List<String> volumeNativeIds) {
        this.volumeNativeIds = volumeNativeIds;
    }

    public List<StoragePort> getTargets() {
        return targets;
    }

    public void setTargets(List<StoragePort> targets) {
        this.targets = targets;
    }

    public List<Initiator> getInitiators() {
        return initiators;
    }

    public void setInitiators(List<Initiator> initiators) {
        this.initiators = initiators;
    }

}
