/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

/**
 * Parameters to remove a volume
 * 
 */
public class ScaleIORemoveVolume {
    private String removeMode = "ONLY_ME";

    public String getRemoveMode() {
        return removeMode;
    }

    public void setRemoveMode(String removeMode) {
        this.removeMode = removeMode;
    }

}
