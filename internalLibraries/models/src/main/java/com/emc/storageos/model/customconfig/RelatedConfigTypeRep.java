/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name = "config_type")
public class RelatedConfigTypeRep {
    private String configName;
    private RestLinkRep selfLink;

    /**
     * The config type name
     * 
     */
    @XmlElement(name = "name")
    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    /**
     * The link to the config type object
     * 
     */
    @XmlElement(name = "link")
    public RestLinkRep getSelfLink() {
        return selfLink;
    }

    public void setSelfLink(RestLinkRep selfLink) {
        this.selfLink = selfLink;
    };

}
