/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model;

import java.util.List;

import com.emc.storageos.driver.univmax.smc.basetype.DefaultParameter;

/**
 * @author fengs5
 *
 */
public class RemoveVolumeParamType extends DefaultParameter {

    List<String> volumeId;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "RemoveVolumeParamType [volumeId=" + volumeId + "]";
    }

    /**
     * @param volumeId
     */
    public RemoveVolumeParamType(List<String> volumeId) {
        super();
        this.volumeId = volumeId;
    }

    /**
     * @return the volumeId
     */
    public List<String> getVolumeId() {
        return volumeId;
    }

    /**
     * @param volumeId the volumeId to set
     */
    public void setVolumeId(List<String> volumeId) {
        this.volumeId = volumeId;
    }

}
