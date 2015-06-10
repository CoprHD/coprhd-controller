/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.smis;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "storage_provider_request")
public class StorageSystemProviderRequestParam {

    private String systemType;
    private String serialNumber;

    public StorageSystemProviderRequestParam() {}

    /**
     * Type of the storage system
     * 
     * @valid isilon
     * @valid vnxblock
     * @valid vnxfile
     * @valid vmax
     * @valid netapp
     * @valid vplex
     * @valid mds
     * @valid brocade
     * @valid rp
     * @valid srdf,
     * @valid host,
     * @valid vcenter,
     * @valid hds,
     * @valid rpvplex,
     * @valid openstack,
     * @valid scaleio;
     */




    @XmlElement(name = "system_type", required = true)
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * Serial ID of the storage system
     * 
     * @valid none
     */
    @XmlElement(name = "serial_number", required = true)
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
    
}
