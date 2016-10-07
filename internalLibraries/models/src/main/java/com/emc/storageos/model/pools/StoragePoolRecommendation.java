/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "storage_pool_recommendation")
public class StoragePoolRecommendation {
    private String dataSourceType;
    private List<StorageDeviceStoragePoolResource> storagePools;
    private List<NamedRelatedResourceRep> storagePorts;
    private List<String> vplexSystems;
    
    
    

   

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
    
    @XmlElement(name = "initiators")
    public List<NamedRelatedResourceRep> getStoragePorts() {
		return storagePorts;
	}

	public void setStoragePorts(List<NamedRelatedResourceRep> storagePorts) {
		this.storagePorts = storagePorts;
	}

	@XmlElement(name = "vplex_systems")
	public List<String> getVplexSystems() {
		return vplexSystems;
	}

	public void setVplexSystems(List<String> vplexSystems) {
		this.vplexSystems = vplexSystems;
	}
	
	
}
