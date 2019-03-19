/*
 * Copyright (c) 2018 DellEMC
 * All Rights Reserved
 */

package com.emc.storageos.model.portgroup;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

/**
 * Extends the name related resource to add new fields
 * specifying if the a change to the port group is allowed and
 * the reason if not allowed.
 */
public class StoragePortGroupChangeRep extends StoragePortGroupRestRep {

    private Boolean allowed;
    private String notAllowedReason;

    public StoragePortGroupChangeRep() {
    }

    public StoragePortGroupChangeRep(URI id, String name, String notAllowedReason, Boolean allowed) {
        this.notAllowedReason = notAllowedReason;
        this.allowed = allowed;
        setId(id);
        setName(name);
    }

    public StoragePortGroupChangeRep(String notAllowedReason, Boolean allowed) {
        this.notAllowedReason = notAllowedReason;
        this.allowed = allowed;

    }

    /**
     * Specifies whether or not a port group change is allowed.
     * 
     */
    @XmlElement(name = "allowed")
    public Boolean getAllowed() {
        return allowed;
    }

    public void setAllowed(Boolean allowed) {
        this.allowed = allowed;
    }

    /**
     * When not allowed, the reason the port group change is not allowed.
     * 
     */
    @XmlElement(name = "not_allowed_reason")
    public String getNotAllowedReason() {
        return notAllowedReason;
    }

    public void setNotAllowedReason(String notAllowedReason) {
        this.notAllowedReason = notAllowedReason;
    }

    public void setChangePGOtherParams(StoragePortGroupRestRep param) {
        this.setNativeGuid(param.getNativeGuid());
        this.setPortMetric(param.getPortMetric());
        this.setVolumeCount(param.getVolumeCount());
        this.setId(param.getId());
        this.setName(param.getName());
    }
}
