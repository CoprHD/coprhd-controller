/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class ScaleIOSDCDataRestRep {

    private String id;
    private String name;
    private String sdcIp;
    private String sdcGuid;
    private String mdmConnectionState;
    private List<ScaleIOVolumeDataRestRep> volumes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSdcIp() {
        return sdcIp;
    }

    public void setSdcIp(String sdcIp) {
        this.sdcIp = sdcIp;
    }

    public String getSdcGuid() {
        return sdcGuid;
    }

    public void setSdcGuid(String sdcGuid) {
        this.sdcGuid = sdcGuid;
    }

    public String getMdmConnectionState() {
        return mdmConnectionState;
    }

    public void setMdmConnectionState(String mdmConnectionState) {
        this.mdmConnectionState = mdmConnectionState;
    }

    @XmlElementWrapper(name = "volumeList")
    @XmlElement(name = "volume")
    public List<ScaleIOVolumeDataRestRep> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<ScaleIOVolumeDataRestRep> volumes) {
        this.volumes = volumes;
    }

}
