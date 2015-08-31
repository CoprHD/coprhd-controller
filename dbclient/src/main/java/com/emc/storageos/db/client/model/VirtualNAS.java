/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;

/**
 * VirtualNAS Server will contain the details of NAS server depending on StorageArray type
 * eg. VDM, vFiler, vServer or AccessZone or NasServer.
 * It will hold information about the Ip interfaces, cifs Server & NFS servers mapped to NasServer
 * 
 * @author ganeso
 * 
 */

@Cf("VirtualNAS")
public class VirtualNAS extends NASServer {

    // Project name which this VNAS belongs to
    private URI project;

    // Base directory Path for the VNAS applicable in AccessZones & vFiler device types
    private String baseDirPath;

    // place holder for the Parent NAS server the Data Mover
    private URI parentNASURI;

    @Name("project")
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
        setChanged("project");
    }

    @Name("baseDirPath")
    public String getBaseDirPath() {
        return baseDirPath;
    }

    public void setBaseDirPath(String baseDirPath) {
        this.baseDirPath = baseDirPath;
        setChanged("baseDirPath");
    }

    @Name("parentNASURI")
    public URI getParentPhysicalNAS() {
        return parentNASURI;
    }

    public void setParentNAS(URI parentPhysicalNAS) {
        this.parentNASURI = parentPhysicalNAS;
        setChanged("parentNASURI");
    }

    @Name("vNasState")
    @EnumType(vNasState.class)
    public String getVNasState() {
        return this.getNasState();
    }

    public void setVNasState(String nasState) {
        this.setNasState(nasState);
        setChanged("vNasState");
    }

    // Defines different States of the NAS server.
    public static enum vNasState {
        LOADED("loaded"),
        MOUNTED("mounted"),
        TEMP_LOADED("tempunloaded"),
        PERM_UNLOADED("permunloaded"),
        UNKNOWN("N/A");

        private final String vNasState;

        private vNasState(String state) {
            vNasState = state;
        }

        public String getNasState() {
            return vNasState;
        }

        private static vNasState[] copyValues = values();

        public static String getNasState(String name) {
            for (vNasState type : copyValues) {
                if (type.getNasState().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }
    };
}
