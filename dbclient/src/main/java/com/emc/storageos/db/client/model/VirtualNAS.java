/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;

/**
 * VirtualNAS Server will contain the details of NAS server depending on StorageArray type
 * e.g. VDM, vFiler, vServer or AccessZone or NasServer.
 * It will hold information about the IP interfaces, CIFS Server & NFS servers mapped to NasServer
 * 
 * @author ganeso
 * 
 */

@Cf("VirtualNAS")
public class VirtualNAS extends NASServer {

    // Project name associated with VNAS
    private URI project;

    // Project URI set name associated with VNAS
    /*
     * Note: Cannot remove or modify the data type of the attribute: 'project'.
     * Because, that's not legal for a schema change.
     */
    private StringSet associatedProjects;

    // Base directory Path for the VNAS applicable in AccessZones & vFiler device types
    private String baseDirPath;

    // place holder for the Parent NAS server the Data Mover
    private URI parentNasUri;

    @Name("project")
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
        setChanged("project");
    }

    @Name("associatedProjects")
    public StringSet getAssociatedProjects() {
        if (associatedProjects == null) {
            associatedProjects = new StringSet();
        }
        return associatedProjects;
    }

    public void setAssociatedProjects(StringSet projects) {
        this.associatedProjects = projects;
        setChanged("associatedProjects");
    }

    public void associateProject(String projectURI) {
        StringSet existingProjects = getAssociatedProjects();
        existingProjects.add(projectURI);
        setAssociatedProjects(existingProjects);
    }

    public void dissociateProject(String projectURI) {
        StringSet existingProjects = getAssociatedProjects();
        existingProjects.remove(projectURI);
        setAssociatedProjects(existingProjects);
    }

    @Name("baseDirPath")
    public String getBaseDirPath() {
        return baseDirPath;
    }

    public void setBaseDirPath(String baseDirPath) {
        this.baseDirPath = baseDirPath;
        setChanged("baseDirPath");
    }

    @Name("vNasState")
    @EnumType(VirtualNasState.class)
    public String getVNasState() {
        return this.getNasState();
    }

    public void setVNasState(String nasState) {
        this.setNasState(nasState);
        setChanged("vNasState");
    }

    @RelationIndex(cf = "RelationIndex", type = PhysicalNAS.class)
    @Name("parentNasUri")
    public URI getParentNasUri() {
        return parentNasUri;
    }

    public void setParentNasUri(URI parentNasUri) {
        this.parentNasUri = parentNasUri;
        setChanged("parentNasUri");
    }

    // Defines different States of the Virtual NAS server.
    public static enum VirtualNasState {
        LOADED("loaded"),
        MOUNTED("mounted"),
        TEMP_LOADED("tempunloaded"),
        PERM_UNLOADED("permunloaded"),
        UNKNOWN("N/A");

        private final String vNasState;

        private VirtualNasState(String state) {
            vNasState = state;
        }

        public String getNasState() {
            return vNasState;
        }

        private static VirtualNasState[] copyValues = values();

        public static String getNasState(String name) {
            for (VirtualNasState type : copyValues) {
                if (type.getNasState().equalsIgnoreCase(name)) {
                    return type.name();
                }
            }
            return UNKNOWN.toString();
        }
    };

    /**
     * Check whether VNAS is assigned to a project or not
     * 
     * @return true if VNAS is not assigned to project(s), false otherwise
     */
    public boolean isNotAssignedToProject() {
        return (associatedProjects == null || associatedProjects.isEmpty());
    }
}
