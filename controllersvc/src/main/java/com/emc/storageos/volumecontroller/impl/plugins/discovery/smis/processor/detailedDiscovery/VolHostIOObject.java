/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import com.emc.storageos.plugins.common.Constants;


public class VolHostIOObject {
    private String volNativeGuid;
    private String hostIoBw;
    private String hostIops;
    private String fastSetting;

    public String getVolNativeGuid() {
        return volNativeGuid;
    }

    public void setVolNativeGuid(String volNativeGuid) {
        this.volNativeGuid = volNativeGuid;
    }

    public String getHostIoBw() {
        return hostIoBw;
    }

    public void setHostIoBw(String hostIoBw) {
        this.hostIoBw = hostIoBw;
    }

    public String getHostIops() {
        return hostIops;
    }

    public void setHostIops(String hostIops) {
        this.hostIops = hostIops;
    }

	public String getFastSetting() {
		return fastSetting;
	}

	public void setFastSetting(String fastSetting) {
		this.fastSetting = fastSetting;
	}
	
	public String toString() {
		StringBuilder hostIoObj = new StringBuilder(volNativeGuid);
		hostIoObj.append(Constants._minusDelimiter).append(hostIoBw)
				.append(Constants._minusDelimiter).append(hostIops)
				.append(Constants._minusDelimiter).append(fastSetting);
		return hostIoObj.toString();
	}

}
