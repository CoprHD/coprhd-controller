package com.emc.storageos.model.storagesystem.types;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.storagesystem.types.StorageSystemTypeRestRep;;

public class StorageSystemTypeList {

    private List<StorageSystemTypeRestRep> storageTypes;

    public StorageSystemTypeList() {
    }

    public StorageSystemTypeList(List<StorageSystemTypeRestRep> storageTypes) {
        this.storageTypes = storageTypes;
    }

    @XmlElement(name = "site")
    public List<StorageSystemTypeRestRep> getStorageSystemTypes() {
        if (storageTypes == null) {
        	storageTypes = new ArrayList<StorageSystemTypeRestRep>();
        }
        return storageTypes;
    }

    public void setSites(List<StorageSystemTypeRestRep> storageTypes) {
        this.storageTypes = storageTypes;
    }
	
}
