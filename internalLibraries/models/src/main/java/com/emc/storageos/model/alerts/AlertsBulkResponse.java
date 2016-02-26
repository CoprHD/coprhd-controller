package com.emc.storageos.model.alerts;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_storage_container")
public class AlertsBulkResponse extends BulkRestRep{

    private List<AlertsCreateResponse> storageContainers;

    @XmlElement(name="storage_container")
    public List<AlertsCreateResponse> getStorageContainers() {
        if(null == storageContainers){
            storageContainers = new ArrayList<AlertsCreateResponse>();
        }
        return storageContainers;
    }

    public void setStorageContainers(List<AlertsCreateResponse> storageContainers) {
        this.storageContainers = storageContainers;
    } 
}
