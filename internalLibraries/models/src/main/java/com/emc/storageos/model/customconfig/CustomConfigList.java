/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Response for getting a list of custom configs
 */
@XmlRootElement(name = "configs")
public class CustomConfigList {
    private List<NamedRelatedResourceRep> customConfigs;

    public CustomConfigList() {
    }

    public CustomConfigList(List<NamedRelatedResourceRep> configs) {
        this.customConfigs = configs;
    }

    /**
     * List of custom config objects that exist in ViPR. Each
     * custom config contains an id, name, and link.
     * 
     * @valid none
     */
    @XmlElement(name = "config")
    public List<NamedRelatedResourceRep> getCustomConfigs() {
        if (customConfigs == null) {
            customConfigs = new ArrayList<NamedRelatedResourceRep>();
        }
        return customConfigs;
    }

    public void setCustomConfigs(List<NamedRelatedResourceRep> configs) {
        this.customConfigs = configs;
    }

}
