/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;

import java.util.List;

/**
 * This helper class describes host export info for a given host to a list of given storage objects.
 */

public class HostExportInfo {

    String hostName; // FQDN of a host
    List<String> storageObjectNativeIds; // storage object native Ids (volume/clone/snap/mirror)
    List<Initiator> initiators; // List of host initiators
    List<StoragePort> targets;    // List of storage ports

    public HostExportInfo(String hostName, List<String> storageObjectNativeIds, List<Initiator> initiators, List<StoragePort> targets) {
        this.hostName = hostName;
        this.storageObjectNativeIds = storageObjectNativeIds;
        this.initiators = initiators;
        this.targets = targets;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public List<String> getStorageObjectNativeIds() {
        return storageObjectNativeIds;
    }

    public void setStorageObjectNativeIds(List<String> storageObjectNativeIds) {
        this.storageObjectNativeIds = storageObjectNativeIds;
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

    @Override
    public String toString() {
        return "\n\tHost name: "+hostName+"; \n\tvolumes: "+ storageObjectNativeIds +"; \n\tinitiators: "+
                initiators+"; \n\ttargets: "+targets+"\n";
    }

}
