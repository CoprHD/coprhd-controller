/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "config_create")
public class CustomConfigCreateParam {
    private String configType;
    private String value;
    private ScopeParam scope;
    private Boolean registered = true;

    public CustomConfigCreateParam() {
    }

    public CustomConfigCreateParam(String configType, String value) {
        this.configType = configType;
        this.value = value;
    }

    public CustomConfigCreateParam(String configType, String value, ScopeParam scope, Boolean registered) {
        this.configType = configType;
        this.value = value;
        this.scope = scope;
        this.registered = registered;
    }

    /**
     * The config type name
     * 
     * @valid none
     */
    @XmlElement(required = true, name = "config_type")
    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    /**
     * The config value
     * 
     * @valid none
     */
    @XmlElement(required = true)
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * The scope that the config applies to
     * 
     * @valid none
     */
    @XmlElement
    public ScopeParam getScope() {
        return scope;
    }

    public void setScope(ScopeParam scope) {
        this.scope = scope;
    }

    /**
     * Whether or not the config is registered when the config is created. the default
     * is true
     * 
     * @valid true
     * @valid false
     */
    @XmlElement
    public Boolean getRegistered() {
        return registered;
    }

    public void setRegistered(Boolean registered) {
        this.registered = registered;
    }

}
