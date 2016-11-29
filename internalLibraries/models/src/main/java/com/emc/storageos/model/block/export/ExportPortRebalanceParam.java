/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="block_export_port_rebalance")
public class ExportPortRebalanceParam {
    private URI storageSystem;
    private ExportPathParameters exportPathParameters;
    private List<InitiatorPathParam> addedPaths;
    private List<InitiatorPathParam> removedPaths;
    private Boolean waitBeforeRemovePaths;
    
    /**
     * Specifies the storage system whose ports will be reallocated.
     */
    @XmlElement(name = "storage_system", required=true)
    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
    }
    
    /**
     * Export path parameter to be used. if not specified, the existing path parameters would be used.
     */
    @XmlElement(name="path_parameters", required=false)
    public ExportPathParameters getExportPathParameters() {
        return exportPathParameters;
    }
    
    public void setExportPathParameters(ExportPathParameters exportPathParameters) {
        this.exportPathParameters = exportPathParameters;
    }
    
    /**
     * Paths are going to be added
     */
    @XmlElementWrapper(name = "added_paths", required = false)
    @XmlElement(name = "initiator_path")
    public List<InitiatorPathParam> getAddedPaths() {
        if (addedPaths == null) {
            addedPaths = new ArrayList<InitiatorPathParam>();
        }
        return addedPaths;
    }
    
    public void setAddedPaths(List<InitiatorPathParam> paths) {
        addedPaths = paths;
    }
    
    /**
     * Paths are going to be removed
     */
    @XmlElementWrapper(name = "removed_paths", required = false)
    @XmlElement(name = "initiator_path")
    public List<InitiatorPathParam> getRemovedPaths() {
        if (removedPaths == null) {
            removedPaths = new ArrayList<InitiatorPathParam>();
        }
        return removedPaths;
    }
    
    public void setRemovedPaths(List<InitiatorPathParam> paths) {
        removedPaths = paths;
    }
    
    /**
     * If true, remove paths would be pending until users resume the workflow,
     * if false, remove paths would not wait for users input.
     */
    @XmlElementWrapper(name = "wait_before_remove_paths", required = false)
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
