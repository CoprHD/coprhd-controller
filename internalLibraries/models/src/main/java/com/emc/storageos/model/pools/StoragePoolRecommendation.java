/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_pool_recommendation")
public class StoragePoolRecommendation {
    private String dataSourceType;
    private List<StorageDeviceStoragePoolResource> storagePools;

    public StoragePoolRecommendation() {
    }

    public StoragePoolRecommendation(String type, List<StorageDeviceStoragePoolResource> pools) {
        this.storagePools = pools;
        this.dataSourceType = type;
    }

    /**
     * data source type
     * 
     */
    @XmlElement(name = "data_source_type")
    public String getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
    }
    
    /**
     * storage pools
     * 
     */
    @XmlElement(name = "storage_device_pools")
    public List<StorageDeviceStoragePoolResource> getStoragePools() {
        if (storagePools == null) {
        	storagePools = new ArrayList<StorageDeviceStoragePoolResource>();
        }
        return storagePools;
    }

    public void setStoragePools(List<StorageDeviceStoragePoolResource> storagePools) {
        this.storagePools = storagePools;
    }
}
