/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.cluster;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.host.TenantResourceRestRep;

/**
 * REST Response representing an host cluster.
 */
@XmlRootElement(name = "cluster")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ClusterRestRep extends TenantResourceRestRep {
    private RelatedResourceRep project;
    private RelatedResourceRep VcenterDataCenter;
    private Boolean autoExportEnabled;

    public ClusterRestRep() {
    }

    public ClusterRestRep(RelatedResourceRep project,
            RelatedResourceRep vcenterDataCenter) {
        this.project = project;
        VcenterDataCenter = vcenterDataCenter;
    }

    /**
     * The project to which the cluster is assigned.
     * 
     * @return the project to which the cluster is assigned.
     */
    @XmlElement(name = "project")
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * The name of the data center in vCenter where this cluster resides
     * 
     * @return the name of the data center in vCenter where this cluster resides
     */
    @XmlElement(name = "vcenter_data_center")
    public RelatedResourceRep getVcenterDataCenter() {
        return VcenterDataCenter;
    }

    public void setVcenterDataCenter(RelatedResourceRep vcenterDataCenter) {
        VcenterDataCenter = vcenterDataCenter;
    }

    /**
     * If discovery will auto export to this cluster.
     * 
     * @return
     */
    @XmlElement(name = "auto_export_enabled")
    public Boolean getAutoExportEnabled() {
        return autoExportEnabled;
    }

    public void setAutoExportEnabled(Boolean autoExportEnabled) {
        this.autoExportEnabled = autoExportEnabled;
    }
}
