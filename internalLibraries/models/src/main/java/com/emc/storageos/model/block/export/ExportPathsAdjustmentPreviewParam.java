/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="block_export_paths_adjustment_preview")
public class ExportPathsAdjustmentPreviewParam {
    private URI virtualArray;
    private URI storageSystem;
    private Boolean useExistingPaths;
    private ExportPathParameters exportPathParameters;
    
    @XmlElement(name="path_parameters", required=true)
    public ExportPathParameters getExportPathParameters() {
        return exportPathParameters;
    }

    public void setExportPathParameters(ExportPathParameters exportPathParameters) {
        this.exportPathParameters = exportPathParameters;
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
     * If true, the existing paths will be retained, and any additional
     * paths made on top of the existing paths. If false, all new export
     * paths will be computed.
     */
    @XmlElement(name = "use_existing_paths",required=false)
    public Boolean getUseExistingPaths() {
        if (useExistingPaths == null) {
            useExistingPaths = false;
        }
        return useExistingPaths;
    }

    public void setUseExistingPaths(Boolean useExistingPaths) {
        this.useExistingPaths = useExistingPaths;
    }

}
