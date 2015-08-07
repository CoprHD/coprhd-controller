/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.sys;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "target_version_info")
public class TargetVersionResponse {

    private String targetVersion;

    public TargetVersionResponse() {
    }

    public TargetVersionResponse(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    @XmlElement(name = "target_version")
    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

}
