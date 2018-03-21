/*
 * Copyright (c) 2018 Dell-EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;

@XmlRootElement(name = "block_export_change_port_group")
public class ChangePortGroupParam {
    private URI newPortGroup;
    private URI currentPortGroup;
    private URI exportMask;
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
     * The current port group being used
     */
    @XmlElement(name = "current_port_group", required = false)
    public URI getCurrentPortGroup() {
        return currentPortGroup;
    }

    public void setCurrentPortGroup(URI currentPortGroup) {
        this.currentPortGroup = currentPortGroup;
    }

    /**
     * The ExportMask URI to change port group
     */
    @XmlElement(name = "export_mask", required = false)
    public URI getExportMask() {
        return exportMask;
    }

    public void setExportMask(URI exportMask) {
        this.exportMask = exportMask;
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

    public void logParameters(Logger log) {
        log.info(String.format("ChangePortGroup input parameters new port group: %s ", getNewPortGroup().toString()));
        if (getCurrentPortGroup() != null) {
            log.info(String.format("current port group: %s ", getCurrentPortGroup().toString()));
        }
        if (getExportMask() != null) {
            log.info(String.format("export mask: %s ", getExportMask().toString()));
        }
        if (getWaitBeforeRemovePaths() != null) {
            log.info(String.format("wait: %s ", getWaitBeforeRemovePaths().toString()));
        }
    }
}