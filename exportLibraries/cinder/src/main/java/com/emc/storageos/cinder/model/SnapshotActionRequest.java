/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;

import com.google.gson.annotations.SerializedName;

public class SnapshotActionRequest {

    /**
     * Json model for snapshot update status request
     * {"os-reset_status":
     * {"status": "available"}
     * }
     */
    @XmlElement(name = "os-reset_status")
    @SerializedName("os-reset_status")
    public UpdateStatus updateStatus = new UpdateStatus();

    public class UpdateStatus {
        public String status;
    }

}
