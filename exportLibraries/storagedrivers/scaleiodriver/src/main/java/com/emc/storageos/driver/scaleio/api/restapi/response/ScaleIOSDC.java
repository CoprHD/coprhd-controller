/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.response;

public class ScaleIOSDC {
    private String id;
    private String sdcIp;
    private String sdcGuid;
    private String mdmConnectionState;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

}
