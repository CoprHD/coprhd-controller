package com.emc.storageos.model.storagedriver;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_driver_list")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StorageDriverListParam {
    private List<String> driverList;

    public StorageDriverListParam() {
        driverList = new ArrayList<>();
    }
    @XmlElement(name = "driver")
    public List<String> getDrivers() {
        return driverList;
    }

    public void setIds(List<String> driverList) {
        this.driverList = driverList;
    }
}