/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_vcenter_data_centers")
public class VcenterDataCenterBulkRep extends BulkRestRep {
    private List<VcenterDataCenterRestRep> VcenterDataCenters;

    public VcenterDataCenterBulkRep() {
    }

    public VcenterDataCenterBulkRep(
            List<VcenterDataCenterRestRep> VcenterDataCenters) {
        this.VcenterDataCenters = VcenterDataCenters;
    }

    /**
     * List of vCenter data center instances that exists in ViPR.
     * 
     * @valid none
     */
    @XmlElement(name = "vcenter_data_center")
    public List<VcenterDataCenterRestRep> getVcenterDataCenters() {
        if (VcenterDataCenters == null) {
            VcenterDataCenters = new ArrayList<VcenterDataCenterRestRep>();
        }
        return VcenterDataCenters;
    }

    public void setVcenterDataCenters(
            List<VcenterDataCenterRestRep> vcenterDataCenters) {
        VcenterDataCenters = vcenterDataCenters;
    }
}
