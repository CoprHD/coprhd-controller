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
    // performance data
    private StringMap metrics;
    // If the port group membership could be changed
    private Boolean mutable;

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
        if(storagePorts == null) {
            storagePorts = new StringSet();
        }
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

    @Name("metrics")
    public StringMap getMetrics() {
        if (this.metrics == null) {
            this.metrics = new StringMap();
        }
        return this.metrics;
    }

    public void setMetrics(StringMap metrics) {
        this.metrics = metrics;
        setChanged("metrics");
    }

    /**
     * Check if the port group could be used for volume export
     * 
     * @return true or false
     */
    public boolean isUsable() {
        boolean result = true;
        if (getInactive() ||
                !RegistrationStatus.REGISTERED.name().equalsIgnoreCase(getRegistrationStatus()) ||
                checkInternalFlags(Flag.INTERNAL_OBJECT)) {
            result = false;
        }
        return result;
    }

    @Name("mutable")
    public Boolean getMutable() {
        return mutable;
    }

    public void setMutable(Boolean mutable) {
        this.mutable = mutable;
        setChanged("mutable");
    }
}
