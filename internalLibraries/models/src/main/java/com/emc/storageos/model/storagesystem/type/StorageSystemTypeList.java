package com.emc.storageos.model.storagesystem.type;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "storagesystem_types")
public class StorageSystemTypeList {

	private List<StorageSystemTypeRestRep> storagesystemTypes;

    public StorageSystemTypeList() {
    }

    public StorageSystemTypeList(List<StorageSystemTypeRestRep> storageTypes) {
        this.storagesystemTypes = storageTypes;
    }
    
    @XmlElement(name = "storagesystem_type")
    public List<StorageSystemTypeRestRep> getStorageSystemTypes() {
        if (storagesystemTypes == null) {
        	storagesystemTypes = new ArrayList<StorageSystemTypeRestRep>();
        }
        return storagesystemTypes;
    }

    public void setStorageSystemTypes(List<StorageSystemTypeRestRep> storageTypes) {
        this.storagesystemTypes = storageTypes;
    }
	
}
