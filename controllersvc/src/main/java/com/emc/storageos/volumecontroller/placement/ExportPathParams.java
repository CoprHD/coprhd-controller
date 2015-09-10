/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;

/**
 * Parameters related to creating Export Paths.
 * 
 * @author Watson
 * 
 */
public class ExportPathParams {
    // Return the default params if asked.
    // MaxPaths will be set to 4.
    // Minpaths will be set to 0 which is ignored.
    // PathsPerInitiator is set to 0 which means determine by array type.
    static public final ExportPathParams defaultParams = new ExportPathParams(4, 0, 0);

    static public ExportPathParams getDefaultParams() {
        return defaultParams;
    }

    /**
     * The maximum number of paths (Initiator-Target pairings) that will be allocated.
     */
    Integer maxPaths;
    /**
     * The minimum number of paths (Initiator-Target pairings) that will be allocated.
     */
    Integer minPaths;
    /**
     * The desired number of paths for each Initiator.
     */
    Integer pathsPerInitiator;
    /**
     * The ExportGroupType. Defaults to Host.
     */
    ExportGroupType exportGroupType;
    /*
     * If allowFewerPorts is true, may allocate fewer than the calculated port requirement
     * for a Network. This is used for RP situations where we're zoning all Initiators to all Ports.
     */
    Boolean allowFewerPorts = false;

    public ExportPathParams(int maxPaths, int minPaths, int pathsPerInitiator) {
        this(maxPaths, minPaths, pathsPerInitiator, ExportGroupType.Host);
    }

    public ExportPathParams(int maxPaths, int minPaths, int pathsPerInitiator, ExportGroupType type) {
        this.maxPaths = maxPaths;
        this.minPaths = minPaths;
        this.pathsPerInitiator = pathsPerInitiator;
        this.exportGroupType = type;
    }

    public Integer getMaxPaths() {
        return maxPaths;
    }

    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
    }

    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }

    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
    }

    public Integer getMinPaths() {
        return minPaths;
    }

    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
    }

    /**
     * @return Returns the ExportGroupType. If not set defaults to Host.
     */
    public ExportGroupType getExportGroupType() {
        return (exportGroupType != null) ? exportGroupType : ExportGroupType.Host;
    }

    public void setExportGroupType(ExportGroupType exportGroupType) {
        this.exportGroupType = exportGroupType;
    }

    public String toString() {
        return String.format("type %s maxPaths %d minPaths %d pathsPerInitiator %d",
                getExportGroupType().name(), getMaxPaths(), getMinPaths(), getPathsPerInitiator());
    }

    public Boolean getAllowFewerPorts() {
        return allowFewerPorts;
    }

    public void setAllowFewerPorts(Boolean allowFewerPorts) {
        this.allowFewerPorts = allowFewerPorts;
    }
}
