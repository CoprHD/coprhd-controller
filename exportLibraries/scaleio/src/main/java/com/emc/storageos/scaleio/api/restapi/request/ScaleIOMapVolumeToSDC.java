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
