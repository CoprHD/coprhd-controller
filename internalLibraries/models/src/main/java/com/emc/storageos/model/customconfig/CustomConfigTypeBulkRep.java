/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "bulk_config_types")
public class CustomConfigTypeBulkRep {
    private List<CustomConfigTypeRep> configTypes;

    public CustomConfigTypeBulkRep() {
    }

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
