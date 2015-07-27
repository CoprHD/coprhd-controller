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

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Response for getting a list of custom configs
 */
@XmlRootElement(name = "configs")
public class CustomConfigList {
    private List<NamedRelatedResourceRep> customConfigs;

    public CustomConfigList() {}
    
    public CustomConfigList(List<NamedRelatedResourceRep> configs) {
        this.customConfigs = configs;
    }

    /**
     * List of custom config objects that exist in ViPR. Each   
     * custom config contains an id, name, and link.
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
