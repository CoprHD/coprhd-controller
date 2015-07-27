/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * An object to represent a cluster of hosts. A cluster can be a part of a
 * vcenter or a physical cluster. A cluster is a {@link TenantOrg} resource
 * and can be optionally assigned to a project.
 * @author elalih
 *
 */
@Cf("Cluster")
public class Cluster extends AbstractTenantResource {
    private URI _vcenterDataCenter;
    private URI _project;
    private String _externalId;

    /**
     * Returns the data center in vcenter where this cluster resides
     * 
     * @return the data center in vcenter where this cluster resides
     */
    @RelationIndex(cf = "RelationIndex", type = VcenterDataCenter.class)
    @Name("vcenterDataCenter")
    public URI getVcenterDataCenter() {
        return _vcenterDataCenter;
    }

    /**
     * Sets the data center in vcenter where this cluster resides
     * 
     * @param dataCenter the data center of this cluster
     */
    public void setVcenterDataCenter(URI dataCenter) {
        this._vcenterDataCenter = dataCenter;
        setChanged("vcenterDataCenter");
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] {  getLabel(),
                getVcenterDataCenter(), getId()};
    }

    /**
     * This field is currently not used. Any values passed into it will be ignored.
     * @return null
     */
    @RelationIndex(cf = "RelationIndex", type = Project.class)
    @Name("project")
    public URI getProject() {
        return _project;
    }

    /**
     * This field is currently not used. Any values passed into it will be ignored.
     * @param project 
     */
    public void setProject(URI project) {
//        _project = project;
//        setChanged("project");
    }

    /**
     * ID of this cluster on an external system such as vCenter.
     * 
     * @return
     */
    @AlternateId("AltIdIndex")
    @Name("externalId")
    public String getExternalId() {
        return _externalId;
    }

    public void setExternalId(String externalId) {
        this._externalId = externalId;
        setChanged("externalId");
    }
}
