/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "config_types")
public class CustomConfigTypeList {
    private List<RelatedConfigTypeRep> configTypes;

    public CustomConfigTypeList() {
    }

    public CustomConfigTypeList(List<RelatedConfigTypeRep> configTypes) {
        this.configTypes = configTypes;
    }

    @XmlElement(name = "config_type")
    public List<RelatedConfigTypeRep> getConfigTypes() {
        if (configTypes == null) {
            configTypes = new ArrayList<RelatedConfigTypeRep>();
        }
        return configTypes;
    }

    public void setConfigTypes(List<RelatedConfigTypeRep> configTypes) {
        this.configTypes = configTypes;
    }
}
