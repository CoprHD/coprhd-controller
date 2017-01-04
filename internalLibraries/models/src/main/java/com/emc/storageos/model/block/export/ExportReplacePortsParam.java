/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;

@XmlRootElement(name="block_export_replace_ports")
public class ExportReplacePortsParam {
    private URI storageSystem;
    private URI virtualArray;
    private Boolean waitBeforeRemovePaths;
    private List<PortReplacementParam> replacePorts;
    
    /**
     * Specifies the storage system whose ports will be replaced.
     */
    @XmlElement(name = "storage_system", required=true)
    public URI getStorageSystem() {
        return storageSystem;
    }
    
    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
    }
    
    /**
     *  Optional virtual_array parameter. Must match the Export Group virtual array or the
     *  alternate VPLEX high availability virtual array for the storage system.
     */
    @XmlElement(name = "virtual_array", required=false)
    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
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

    
    /**
     * Ports are going to be replaced
     */
    @XmlElementWrapper(name = "replace_ports", required = true)
    @XmlElement(name = "replace_port")
    public List<PortReplacementParam> getReplacePorts() {
        return replacePorts;
    }

    public void setReplacePorts(List<PortReplacementParam> replacePorts) {
        this.replacePorts = replacePorts;
    }
    
    public void logParameters(Logger log) {

        log.info("ExportReplacePorts input parameters storage-system:" + getStorageSystem().toString());
        
        if (getVirtualArray() != null) {
            log.info("Virtual array: " + getVirtualArray().toString());
        }
        
        if (getReplacePorts() != null) {
            log.info("Replace ports:");
            for (PortReplacementParam portReplacement : getReplacePorts()) {
                portReplacement.log(log);
            }
        } else {
            log.info("Replace ports not supplied");
        }
    }
    
}
