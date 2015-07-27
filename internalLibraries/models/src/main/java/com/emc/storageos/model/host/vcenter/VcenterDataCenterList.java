/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for getting a list of tenant vCenter data centers
 */
@XmlRootElement(name = "vcenter_data_centers")
public class VcenterDataCenterList {
    private List<NamedRelatedResourceRep> dataCenters;

    public VcenterDataCenterList() {}
    
    public VcenterDataCenterList(List<NamedRelatedResourceRep> dataCenters) {
        this.dataCenters = dataCenters;
    }

    /**
     * List of vCenter data center instances that exists in ViPR.
     * @valid none
     */
    @XmlElement(name = "vcenter_data_center")
    public List<NamedRelatedResourceRep> getDataCenters() {
        if (dataCenters == null) {
            dataCenters = new ArrayList<NamedRelatedResourceRep>();
        }
        return dataCenters;
    }

    public void setDataCenters(List<NamedRelatedResourceRep> dataCenters) {
        this.dataCenters = dataCenters;
    }
}
