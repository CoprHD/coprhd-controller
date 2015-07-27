/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
