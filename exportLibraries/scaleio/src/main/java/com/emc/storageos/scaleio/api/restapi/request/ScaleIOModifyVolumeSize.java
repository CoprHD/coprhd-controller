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
