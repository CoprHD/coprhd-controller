/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api.restapi.request;

/**
 * Parameters to unmap volume
 * 
 */
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
