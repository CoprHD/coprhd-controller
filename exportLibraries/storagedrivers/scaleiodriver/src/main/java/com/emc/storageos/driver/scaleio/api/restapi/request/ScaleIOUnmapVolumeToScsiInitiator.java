/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

/**
 * Parameters to unmap volume
 * 
 */
public class ScaleIOUnmapVolumeToScsiInitiator {
    private String scsiInitiatorId;

    public String getScsiInitiatorId() {
        return scsiInitiatorId;
    }

    public void setScsiInitiatorId(String scsiInitiatorId) {
        this.scsiInitiatorId = scsiInitiatorId;
    }

}
