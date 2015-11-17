/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Parameters to unmap volume
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ScaleIOUnmapVolumeToSDC {
    private String sdcId;
    private String ignoreScsiInitiators;

    public String getSdcId() {
        return sdcId;
    }

    public void setSdcId(String sdcId) {
        this.sdcId = sdcId;
    }

    public String getIgnoreScsiInitiators() {
        return ignoreScsiInitiators;
    }

    public void setIgnoreScsiInitiators(String ignoreScsiInitiators) {
        this.ignoreScsiInitiators = ignoreScsiInitiators;
    }

}
