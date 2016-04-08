/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;


import java.util.List;

public class ITL {

    List<Initiator> initiators;
    List<StorageVolume> volumes;
    List<StoragePort> ports;


    public List<Initiator> getInitiators() {
        return initiators;
    }

    public void setInitiators(List<Initiator> initiators) {
        this.initiators = initiators;
    }

    public List<StorageVolume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<StorageVolume> volumes) {
        this.volumes = volumes;
    }

    public List<StoragePort> getPorts() {
        return ports;
    }

    public void setPorts(List<StoragePort> ports) {
        this.ports = ports;
    }
}
