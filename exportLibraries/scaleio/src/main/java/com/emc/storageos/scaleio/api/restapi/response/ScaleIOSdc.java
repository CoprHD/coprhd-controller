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
package com.emc.storageos.scaleio.api.restapi.response;

public class ScaleIOSdc {
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
