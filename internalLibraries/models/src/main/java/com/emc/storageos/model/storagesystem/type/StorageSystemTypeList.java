package com.emc.storageos.model.storagesystem.type;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "storagesystem_types")
public class StorageSystemTypeList {

	private List<StorageSystemTypeRestRep> storagesystem_types;

	public StorageSystemTypeList() {
	}

	public StorageSystemTypeList(List<StorageSystemTypeRestRep> storageTypes) {
		this.storagesystem_types = storageTypes;
	}

	@XmlElement(name = "storagesystem_type")
	public List<StorageSystemTypeRestRep> getStorageSystemTypes() {
		if (storagesystem_types == null) {
			storagesystem_types = new ArrayList<StorageSystemTypeRestRep>();
		}
		return storagesystem_types;
	}

	public void setStorageSystemTypes(List<StorageSystemTypeRestRep> storageTypes) {
		this.storagesystem_types = storageTypes;
	}

}
