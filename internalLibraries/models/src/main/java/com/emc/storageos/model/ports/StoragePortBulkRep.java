/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.ports;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_storage_ports")
public class StoragePortBulkRep extends BulkRestRep {
    private List<StoragePortRestRep> storagePorts;

    /**
     * List of Storage ports.  A Storage port represents a 
     * port of a storage device.
     * @valid none
     */
    @XmlElement(name = "storage_port")
    public List<StoragePortRestRep> getStoragePorts() {
        if (storagePorts == null) {
            storagePorts = new ArrayList<StoragePortRestRep>();
        }
        return storagePorts;
    }

    public void setStoragePorts(List<StoragePortRestRep> storagePorts) {
        this.storagePorts = storagePorts;
    }

    public StoragePortBulkRep() {
    }

    public StoragePortBulkRep(List<StoragePortRestRep> storagePorts) {
        super();
        this.storagePorts = storagePorts;
    }

}
