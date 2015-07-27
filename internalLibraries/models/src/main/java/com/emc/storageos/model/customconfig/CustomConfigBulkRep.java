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

package com.emc.storageos.model.customconfig;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_configs")
public class CustomConfigBulkRep extends BulkRestRep{
    private List<CustomConfigRestRep> configs;
    
    public CustomConfigBulkRep() {}
    
    public CustomConfigBulkRep(List<CustomConfigRestRep> configs) {
        this.configs = configs;
    }

    @XmlElement(name = "config")
    public List<CustomConfigRestRep> getCustomConfigs() {
        if (configs == null) {
            configs = new ArrayList<CustomConfigRestRep>();
        }
        return configs;
    }

    public void setCustomConfigs(List<CustomConfigRestRep> configs) {
        this.configs = configs;
    }
    
    

}
