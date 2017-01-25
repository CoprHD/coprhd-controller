/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.model.valid.EnumType;

@Cf("StoragePortGroup")
public class StoragePortGroup extends DiscoveredDataObject {

    // Storage device this storage port belongs to
    private URI storageDevice;
    // Set of storage ports in the port group
    private StringSet storagePorts;
    // Registration status
    private String registrationStatus = RegistrationStatus.REGISTERED.toString();

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        this.storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @RelationIndex(cf = "RelationIndex", type = StoragePort.class)
    @IndexByKey
    @Name("storagePorts")
    public StringSet getStoragePorts() {
        return storagePorts;
    }

    public void setStoragePorts(StringSet storagePorts) {
        this.storagePorts = storagePorts;
        setChanged("storagePorts");
    }

    @EnumType(RegistrationStatus.class)
    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

}
