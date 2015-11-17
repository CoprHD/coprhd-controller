/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.request;

/**
 * Parameters to expand a volume
 * 
 */
public class ScaleIOModifyVolumeSize {
    private String sizeInGB;

    public String getSizeInGB() {
        return sizeInGB;
    }

    public void setSizeInGB(String sizeInGB) {
        this.sizeInGB = sizeInGB;
    }

}
