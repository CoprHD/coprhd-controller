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

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
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
