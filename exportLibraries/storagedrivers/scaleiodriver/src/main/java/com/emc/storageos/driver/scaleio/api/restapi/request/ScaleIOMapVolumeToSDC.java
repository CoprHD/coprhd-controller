/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Parameters to map a volume to SDC
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ScaleIOMapVolumeToSDC {
    private String sdcId;
    private String allowMultipleMappings;

    public String getSdcId() {
        return sdcId;
    }

    public void setSdcId(String sdcId) {
        this.sdcId = sdcId;
    }

    public String getAllowMultipleMappings() {
        return allowMultipleMappings;
    }

    public void setAllowMultipleMappings(String allowMultipleMappings) {
        this.allowMultipleMappings = allowMultipleMappings;
    }

}
