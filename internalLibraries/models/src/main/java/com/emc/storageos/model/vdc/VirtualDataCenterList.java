/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vdc;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "virtual_data_centers")
public class VirtualDataCenterList {
    private List<NamedRelatedResourceRep> virtualDataCenters;

    public VirtualDataCenterList() {}
    
    public VirtualDataCenterList(List<NamedRelatedResourceRep> virtualDataCenters) {
        this.virtualDataCenters = virtualDataCenters;
    }

    /**
     * List of storage system URLs with name
     * 
     * @valid none
     */ 
    @XmlElement(name = "virtual_data_center")
    public List<NamedRelatedResourceRep> getVirtualDataCenters() {
        if (virtualDataCenters == null) {
            virtualDataCenters = new ArrayList<NamedRelatedResourceRep>();
        }
        return virtualDataCenters;
    }

    public void setVirtualDataCenters(List<NamedRelatedResourceRep> virtualDataCenters) {
        this.virtualDataCenters = virtualDataCenters;
    }
}
