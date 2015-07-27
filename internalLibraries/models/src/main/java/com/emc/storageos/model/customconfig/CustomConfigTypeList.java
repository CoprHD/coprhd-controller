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

@XmlRootElement(name = "config_types")
public class CustomConfigTypeList {
    private List<RelatedConfigTypeRep> configTypes;
    
    public CustomConfigTypeList() {}
    
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
