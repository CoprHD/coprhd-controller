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

@XmlRootElement(name = "bulk_config_types")
public class CustomConfigTypeBulkRep {
    private List<CustomConfigTypeRep> configTypes;
    
    public CustomConfigTypeBulkRep() {}
    
    public CustomConfigTypeBulkRep(List<CustomConfigTypeRep> configs) {
        this.configTypes = configs;
    }

    @XmlElement(name = "config_type")
    public List<CustomConfigTypeRep> getConfigTypes() {
        if (configTypes == null) {
            configTypes = new ArrayList<CustomConfigTypeRep>();
        }
        return configTypes;
    }

    public void setCustomConfigs(List<CustomConfigTypeRep> configs) {
        this.configTypes = configs;
    }
    

}
