/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.portgroup;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ports.StoragePortList;

@XmlRootElement(name = "storage_port_group")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StoragePortGroupRestRep extends DiscoveredDataObjectRestRep {
    private String registrationStatus;
    private RelatedResourceRep storageDevice;
    private StoragePortList storagePorts;
    private Double portMetric;
    private Long volumeCount;

    public StoragePortGroupRestRep() {
    }

    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageDevice() {
        return storageDevice;
    }

    public void setStorageDevice(RelatedResourceRep storageDevice) {
        this.storageDevice = storageDevice;
    }

    @XmlElement(name = "storage_ports")
    public StoragePortList getStoragePorts() {
        if (storagePorts == null) {
            storagePorts = new StoragePortList();
        }
        return storagePorts;
    }

    public void setStoragePorts(StoragePortList storagePorts) {
        this.storagePorts = storagePorts;
    }

    @XmlElement(name = "port_metric")
    public Double getPortMetric() {
        return portMetric;
    }

    public void setPortMetric(Double portMetric) {
        this.portMetric = portMetric;
    }

    @XmlElement(name = "volume_count")
    public Long getVolumeCount() {
        return volumeCount;
    }

    public void setVolumeCount(Long volumeCount) {
        this.volumeCount = volumeCount;
    }

}
