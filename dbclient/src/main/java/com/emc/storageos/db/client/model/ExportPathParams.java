/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.beans.Transient;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;

@Cf("ExportPathParams")
public class ExportPathParams extends DataObject {
    Integer maxPaths;
    Integer minPaths;
    Integer pathsPerInitiator;
    // storage ports to be used for port allocation if supplied
    StringSet storagePorts;
    // Default exportGroupType is Host. Expressed in ExportGroup.ExportGroupType
    String exportGroupType;
    // If explicitly created is true, a user specifically create an ExportPathParam record.
    // If explicitly created is false, the entry was created as a side effect of an export operation.
    Boolean explicitlyCreated;
    
    /*
     * If allowFewerPorts is true, may allocate fewer than the calculated port requirement
     * for a Network. This is used for RP situations where we're zoning all Initiators to all Ports.
     */
    Boolean allowFewerPorts = false;
    
    // Return the default params if asked.
    // MaxPaths will be set to 4.
    // Minpaths will be set to 0 which is ignored.
    // PathsPerInitiator is set to 0 which means determine by array type.
    static public final ExportPathParams defaultParams = new ExportPathParams(4, 0, 0);

    static public ExportPathParams getDefaultParams() {
        return defaultParams;
    }
    
    public ExportPathParams() {
        // default constructor needed for persistence framework
    }

    public ExportPathParams(int maxPaths, int minPaths, int pathsPerInitiator) {
        this(maxPaths, minPaths, pathsPerInitiator, ExportGroupType.Host);
    }

    public ExportPathParams(int maxPaths, int minPaths, int pathsPerInitiator, ExportGroupType type) {
        this.maxPaths = maxPaths;
        this.minPaths = minPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.exportGroupType = type.toString();
    }
    
    public String toString() {
        return String.format("type %s maxPaths %d minPaths %d pathsPerInitiator %d",
                returnExportGroupType().name(), getMaxPaths(), getMinPaths(), getPathsPerInitiator());
    }
    
    @Name("maxPaths")
    public Integer getMaxPaths() {
        return maxPaths;
    }
    
    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
        setChanged("maxPaths");
    }
    
    @Name("minPaths")
    public Integer getMinPaths() {
        return minPaths;
    }
    
    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
        setChanged("minPaths");
    }
    
    @Name("pathsPerInitiator")
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }
    
    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
        setChanged("pathsPerInitiator");
    }
    
    @Name("storagePorts")
    public StringSet getStoragePorts() {
        if (storagePorts == null) {
            return new StringSet();
        }
        return storagePorts;
    }
    
    public void setStoragePorts(StringSet storagePorts) {
        this.storagePorts = storagePorts;
        setChanged("storagePorts");
    }

    @Name("explicitlyCreated")
    public Boolean getExplicitlyCreated() {
        return explicitlyCreated;
    }
    
    public boolean wasExplicitlyCreated() {
        return (explicitlyCreated != null && explicitlyCreated);
    }

    public void setExplicitlyCreated(Boolean explicitlyCreated) {
        this.explicitlyCreated = explicitlyCreated;
    }

    @Name("exportGroupType")
    public String getExportGroupType() {
                return exportGroupType;
    }
    
    public ExportGroupType returnExportGroupType() {
        if (getExportGroupType() == null) {
            return ExportGroupType.Host;
        }
        return ExportGroupType.valueOf(getExportGroupType());
    }

    public void setExportGroupType(String exportGroupType) {
                this.exportGroupType = exportGroupType;
    }

    @Transient
    public Boolean getAllowFewerPorts() {
                return allowFewerPorts;
    }

    public void setAllowFewerPorts(Boolean allowFewerPorts) {
                this.allowFewerPorts = allowFewerPorts;
    }

}
