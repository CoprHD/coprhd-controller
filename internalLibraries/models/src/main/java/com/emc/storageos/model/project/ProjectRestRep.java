/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.project;

import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "project")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ProjectRestRep extends DataObjectRestRep {
    private RelatedResourceRep tenant;
    private String owner;
    private Set<String> assignedVNasServers;

    public ProjectRestRep() {
    }

    public ProjectRestRep(RelatedResourceRep tenant, String owner) {
        this.tenant = tenant;
        this.owner = owner;
    }

    /**
     * Owner of the project is the user who created it or
     * explicitly assigned as owner to the project, is allowed
     * full access to the project and all its resources"
     * 
     * 
     */
    @XmlElement(name = "owner")
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * 
     * The tenant that this project is associated with.
     * 
     */
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    /**
     * Keywords and labels that can be added by a user to a resource
     * to make it easy to find when doing a search.
     * 
     */
    @XmlElementWrapper(name = "assigned_vnas_servers")
    /**
     * 
     * List of VNAS Servers associated with this project.
     * 
     */
    @XmlElement(name = "assigned_vnas_server")
    public Set<String> getAssignedVNasServers() {
        return assignedVNasServers;
    }

    /**
     * @param assignedVNasServers the assignedVNasServers to set
     */
    public void setAssignedVNasServers(Set<String> assignedVNasServers) {
        this.assignedVNasServers = assignedVNasServers;
    }

}
