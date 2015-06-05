/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public ClusterRestRep() {}
    
    public ClusterRestRep(RelatedResourceRep project,
            RelatedResourceRep vcenterDataCenter) {
        this.project = project;
        VcenterDataCenter = vcenterDataCenter;
    }

    /**
     * The project to which the cluster is assigned.
     * @valid none
     * @return the project to which the cluster is assigned.
     */
    @XmlElement(name="project")
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * The name of the data center in vCenter where this cluster resides
     * @valid none
     * @return the name of the data center in vCenter where this cluster resides
     */
    @XmlElement(name="vcenter_data_center")
    public RelatedResourceRep getVcenterDataCenter() {
        return VcenterDataCenter;
    }

    public void setVcenterDataCenter(RelatedResourceRep vcenterDataCenter) {
        VcenterDataCenter = vcenterDataCenter;
    }
}

