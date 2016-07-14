/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.storagesystem.type;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "storagesystem_types")
public class StorageSystemTypeList {

    private List<StorageSystemTypeRestRep> storageSystemTypes;

    public StorageSystemTypeList() {
    }

    public StorageSystemTypeList(List<StorageSystemTypeRestRep> storageTypes) {
        this.storageSystemTypes = storageTypes;
    }

    @XmlElement(name = "storagesystem_type")
    public List<StorageSystemTypeRestRep> getStorageSystemTypes() {
        if (storageSystemTypes == null) {
            storageSystemTypes = new ArrayList<StorageSystemTypeRestRep>();
        }
        return storageSystemTypes;
    }

    public void setStorageSystemTypes(List<StorageSystemTypeRestRep> storageTypes) {
        this.storageSystemTypes = storageTypes;
    }

}
