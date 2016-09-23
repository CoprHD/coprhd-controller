/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_pools")
public class StorageDeviceStoragePoolResource {
    private NamedRelatedResourceRep storagePool;
    private NamedRelatedResourceRep storageDevice;

    public StorageDeviceStoragePoolResource() {
    }

    public StorageDeviceStoragePoolResource(NamedRelatedResourceRep pool, NamedRelatedResourceRep storage) {
        this.storagePool = pool;
        this.storageDevice = storage;
    }

    /**
     * storage pool
     * 
     */
    @XmlElement(name = "storage_pool")
    public NamedRelatedResourceRep getStoragePool() {
        if (storagePool == null) {
        	storagePool = new NamedRelatedResourceRep();
        }
        return storagePool;
    }

    public void setStoragePool(NamedRelatedResourceRep pool) {
        this.storagePool = pool;
    }
    
    /**
     * storage device
     * 
     */
    @XmlElement(name = "storage_device")
    public NamedRelatedResourceRep getStorageDevice() {
        if (storageDevice == null) {
        	storageDevice = new NamedRelatedResourceRep();
        }
        return storageDevice;
    }

    public void setStorageDevice(NamedRelatedResourceRep storageDevice) {
        this.storageDevice = storageDevice;
    }
}
