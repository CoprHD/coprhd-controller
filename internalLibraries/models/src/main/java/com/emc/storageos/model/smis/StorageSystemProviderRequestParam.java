/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.smis;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_provider_request")
public class StorageSystemProviderRequestParam {

    private String systemType;
    private String serialNumber;

    public StorageSystemProviderRequestParam() {
    }

    /**
     * Type of the storage system. 
     * Valid values:
     *  isilon
     *  vnxblock
     *  vnxfile
     *  vmax
     *  netapp
     *  vplex
     *  mds
     *  brocade
     *  rp
     *  srdf
     *  host
     *  vcenter
     *  hds
     *  rpvplex
     *  openstack
     *  scaleio
     * 
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
     */
    @XmlElement(name = "serial_number", required = true)
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

}
