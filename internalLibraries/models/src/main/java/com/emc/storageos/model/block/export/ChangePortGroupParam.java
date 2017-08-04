/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "block_export_change_port_group")
public class ChangePortGroupParam {
    private URI newPortGroup;
    private Boolean waitBeforeRemovePaths;

    /**
     * The new port group to be used
     */
    @XmlElement(name = "new_port_group", required=true)
    public URI getNewPortGroup() {
        return newPortGroup;
    }

    public void setNewPortGroup(URI newPortGroup) {
        this.newPortGroup = newPortGroup;
    }
    
    /**
     * If true, remove paths would be pending until users resume the workflow,
     * if false, remove paths would not wait for users input.
     */
    @XmlElement(name = "wait_before_remove_paths", required=false)
    public Boolean getWaitBeforeRemovePaths() {
        if (waitBeforeRemovePaths == null) {
            waitBeforeRemovePaths = false;
        }
        return waitBeforeRemovePaths;
    }

    public void setWaitBeforeRemovePaths(Boolean wait) {
        waitBeforeRemovePaths = wait;
    }
}
