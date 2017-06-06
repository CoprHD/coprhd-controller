/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.portgroup;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;


@XmlRootElement(name = "storage_port_group_list")
public class StoragePortGroupRestRepList extends BulkRestRep{
    private List<StoragePortGroupRestRep> storagePortGroups;
    
    /**
     * List of Storage port groups. 
     * 
     */
    @XmlElement(name = "storage_port_group")
    public List<StoragePortGroupRestRep> getStoragePortGroups() {
        if (storagePortGroups == null) {
            storagePortGroups = new ArrayList<StoragePortGroupRestRep>();
        }
        return storagePortGroups;
    }

    public void setStoragePorts(List<StoragePortGroupRestRep> storagePorts) {
        this.storagePortGroups = storagePorts;
    }

    public StoragePortGroupRestRepList() {
    }

    public StoragePortGroupRestRepList(List<StoragePortGroupRestRep> storagePortGroups) {
        super();
        this.storagePortGroups = storagePortGroups;
    }
}
