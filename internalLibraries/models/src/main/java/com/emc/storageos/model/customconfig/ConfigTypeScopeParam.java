/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "config_type_scope")
public class ConfigTypeScopeParam {

    private String type;
    private List<String> value;

    public ConfigTypeScopeParam() {
    }

    public ConfigTypeScopeParam(String type, List<String> value) {
        this.type = type;
        this.value = value;
    }

    /**
     * The scope type
     * Valid values:
     *  systemType
     *  global
     */
    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The scope value
     * 
     */
    @XmlElement(name = "value")
    public List<String> getValue() {
        return value;
    }

    public void setValue(List<String> value) {
        this.value = value;
    }

}
