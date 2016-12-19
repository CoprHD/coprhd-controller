/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author jainm15
 */
@XmlRootElement(name = "unassign_file_policy")
public class FilePolicyUnAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private boolean unassignFromAll;
    private boolean forceUnassign = false;
    private Set<URI> unassignfromVpools;
    private Set<URI> unassignfromProjects;
    private Set<URI> unassignfromFileSystems;

    public FilePolicyUnAssignParam() {
        super();
    }

    @XmlElement(name = "unassign_from_all")
    public boolean getUnassignFromAll() {
        return unassignFromAll;
    }

    public void setUnassignFromAll(boolean unassignFromAll) {
        this.unassignFromAll = unassignFromAll;
    }

    @XmlElement(name = "force_unassign")
    public boolean getForceUnassign() {
        return forceUnassign;
    }

    public void setForceUnassign(boolean forceUnassign) {
        this.forceUnassign = forceUnassign;
    }

    @XmlElementWrapper(name = "unassign_from_vpools")
    @XmlElement(name = "vpool")
    public Set<URI> getUnassignfromVpools() {
        return unassignfromVpools;
    }

    public void setUnassignfromVpools(Set<URI> unassignfromVpools) {
        this.unassignfromVpools = unassignfromVpools;
    }

    @XmlElementWrapper(name = "unassign_from_projects")
    @XmlElement(name = "project")
    public Set<URI> getUnassignfromProjects() {
        return unassignfromProjects;
    }

    public void setUnassignfromProjects(Set<URI> unassignfromProjects) {
        this.unassignfromProjects = unassignfromProjects;
    }

    @XmlElementWrapper(name = "unassign_from_file_systems")
    @XmlElement(name = "file_system")
    public Set<URI> getUnassignfromFileSystems() {
        return unassignfromFileSystems;
    }

    public void setUnassignfromFileSystems(Set<URI> unassignfromFileSystems) {
        this.unassignfromFileSystems = unassignfromFileSystems;
    }

}
