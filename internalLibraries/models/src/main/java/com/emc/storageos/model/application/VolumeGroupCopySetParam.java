/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "volume_group_copy_set")
public class VolumeGroupCopySetParam {
    private String copySetName;

    public VolumeGroupCopySetParam() {
    }

    public VolumeGroupCopySetParam(String copySetName) {
        this.copySetName = copySetName;
    }

    @XmlElement(name = "copy_set_name", required = true)
    public String getCopySetName() {
        return copySetName;
    }

    public void setCopySetName(String copySetName) {
        this.copySetName = copySetName;
    }
}
