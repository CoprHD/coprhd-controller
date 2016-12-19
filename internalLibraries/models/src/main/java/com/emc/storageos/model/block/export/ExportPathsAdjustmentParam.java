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

@XmlRootElement(name="block_export_paths_adjustment")
public class ExportPathsAdjustmentParam {
    private URI storageSystem;
    private ExportPathParameters exportPathParameters;
    private List<InitiatorPathParam> adjustedPaths;
    private List<InitiatorPathParam> removedPaths;
    private Boolean waitBeforeRemovePaths;
    private URI virtualArray;
    
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
     * Export path parameter to be used.
     */
    @XmlElement(name="path_parameters", required=true)
    public ExportPathParameters getExportPathParameters() {
        return exportPathParameters;
    }
    
    public void setExportPathParameters(ExportPathParameters exportPathParameters) {
        this.exportPathParameters = exportPathParameters;
    }
    
    /**
     * Paths are going to be adjusted, including new and retained paths
     */
    @XmlElementWrapper(name = "adjusted_paths", required = false)
    @XmlElement(name = "initiator_path")
    public List<InitiatorPathParam> getAdjustedPaths() {
        if (adjustedPaths == null) {
            adjustedPaths = new ArrayList<InitiatorPathParam>();
        }
        return adjustedPaths;
    }
    
    public void setAdjustedPaths(List<InitiatorPathParam> paths) {
        adjustedPaths = paths;
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
}
