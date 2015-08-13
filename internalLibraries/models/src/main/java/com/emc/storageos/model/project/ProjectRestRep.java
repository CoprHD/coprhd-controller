/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.project;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "project")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ProjectRestRep extends DataObjectRestRep {
    private RelatedResourceRep tenant;
    private String owner;
    private AssignVNASParam assignedVNAS;

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
     * @valid none
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
     * @valid none
     */
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    /**
     * 
     * List of VNAS Servers associated with this project.
     * 
     * @valid none
     */
    @XmlElement(name = "assignedVNAS")
    public AssignVNASParam getAssignedVNAS() {
        return assignedVNAS;
    }

    /**
     * @param assignedVNAS the assignedVNAS to set
     */
    public void setAssignedVNAS(AssignVNASParam assignedVNAS) {
        this.assignedVNAS = assignedVNAS;
    }
}
