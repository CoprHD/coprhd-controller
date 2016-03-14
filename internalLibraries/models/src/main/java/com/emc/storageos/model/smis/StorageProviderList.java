/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.smis;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "storage_providers")
public class StorageProviderList {

    private List<NamedRelatedResourceRep> storageProviders;

    public StorageProviderList() {
    }

    public StorageProviderList(List<NamedRelatedResourceRep> storageProviders) {
        this.storageProviders = storageProviders;
    }

    /**
     * List of Storage Providers.
     * 
     */
    @XmlElement(name = "storage_provider")
    public List<NamedRelatedResourceRep> getStorageProviders() {
        if (storageProviders == null) {
            storageProviders = new ArrayList<NamedRelatedResourceRep>();
        }
        return storageProviders;
    }

    public void setStorageProviders(List<NamedRelatedResourceRep> storageProviders) {
        this.storageProviders = storageProviders;
    }

}
