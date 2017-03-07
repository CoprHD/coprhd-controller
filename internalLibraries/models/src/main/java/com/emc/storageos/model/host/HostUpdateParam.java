/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Request PUT parameter for host update operation.
 */
@XmlRootElement(name = "host_update")
public class HostUpdateParam extends HostParam {
    private boolean updateSanBootTargets = false;
    
    @XmlElement(name = "update_san_boot_targets", required = false)
    @JsonProperty("update_san_boot_targets")
    public boolean getUpdateSanBootTargets() {
        return updateSanBootTargets;
    }

    public void setUpdateSanBootTargets(boolean updateSanBootTargets) {
        this.updateSanBootTargets = updateSanBootTargets;
    }
}
