/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter for custom config update.
 */
@XmlRootElement(name = "config_update")
public class CustomConfigUpdateParam {
    private String value;

    public CustomConfigUpdateParam() {
    }

    public CustomConfigUpdateParam(String value) {
        this.value = value;
    }

    /**
     * The config value to be changed
     * 
     */
    @XmlElement
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
