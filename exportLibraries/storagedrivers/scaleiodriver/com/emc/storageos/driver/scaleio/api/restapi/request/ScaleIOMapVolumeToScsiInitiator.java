/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Parameters to map a volume to a scsi initiator
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ScaleIOMapVolumeToScsiInitiator {
    private String scsiInitiatorId;
    private String lun;
    private String allowMultipleMapp;

    public String getScsiInitiatorId() {
        return scsiInitiatorId;
    }

    public void setScsiInitiatorId(String scsiInitiatorId) {
        this.scsiInitiatorId = scsiInitiatorId;
    }

    public String getLun() {
        return lun;
    }

    public void setLun(String lun) {
        this.lun = lun;
    }

    public String getAllowMultipleMapp() {
        return allowMultipleMapp;
    }

    public void setAllowMultipleMapp(String allowMultipleMapp) {
        this.allowMultipleMapp = allowMultipleMapp;
    }

}
