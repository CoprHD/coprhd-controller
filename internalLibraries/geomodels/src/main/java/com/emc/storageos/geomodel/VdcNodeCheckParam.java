/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geomodel;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author cgarber
 *
 */
@XmlRootElement(name = "vdc-node-check")
public class VdcNodeCheckParam {
    
    private List<VdcConfig> virtualDataCenters;

    @XmlElement(name = "vdc")
    public List<VdcConfig> getVirtualDataCenters() {
        if (virtualDataCenters == null) {
            virtualDataCenters = new ArrayList<VdcConfig>();
        }
        return virtualDataCenters;
    }

    public void setVirtualDataCenters(List<VdcConfig> virtualDataCenters) {
        this.virtualDataCenters = virtualDataCenters;
    }
    
    public void addVirtualDataCenter(VdcConfig vdc) {
        if (this.virtualDataCenters == null) {
            this.virtualDataCenters = new ArrayList<VdcConfig>();
        }
        this.virtualDataCenters.add(vdc);
    }
}
