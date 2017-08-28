/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.storagedriver;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "drivers")
public class StorageDriverList {

    private List<StorageDriverRestRep> drivers;

    @XmlElement(name = "driver")
    public List<StorageDriverRestRep> getDrivers() {
        if (drivers == null) {
            drivers = new ArrayList<StorageDriverRestRep>();
        }
        return drivers;
    }

    public void setDrivers(List<StorageDriverRestRep> drivers) {
        this.drivers = drivers;
    }
}
