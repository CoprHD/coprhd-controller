/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
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
