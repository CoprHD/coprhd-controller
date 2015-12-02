package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "bulk_storage_container")
public class StorageContainerBulkResponse {

    private List<StorageContainerCreateResponse> storageContainers;

    @XmlElement(name="storage_container")
    public List<StorageContainerCreateResponse> getStorageContainers() {
        if(null == storageContainers){
            storageContainers = new ArrayList<StorageContainerCreateResponse>();
        }
        return storageContainers;
    }

    public void setStorageContainers(List<StorageContainerCreateResponse> storageContainers) {
        this.storageContainers = storageContainers;
    } 
}
