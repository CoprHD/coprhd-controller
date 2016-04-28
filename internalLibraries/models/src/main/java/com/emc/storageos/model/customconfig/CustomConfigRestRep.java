/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.model.DataObjectRestRep;

/**
 * Information relevant to a custom config, returned as a
 * response to a REST request.
 * 
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "config")
public class CustomConfigRestRep extends DataObjectRestRep {
    private ScopeParam scope;
    private String value;
    private Boolean registered;
    private Boolean systemDefault;
    private RelatedConfigTypeRep configType;

    /**
     * The scope of this config applies to
     * 
     */
    @XmlElement
    public ScopeParam getScope() {
        return scope;
    }

    public void setScope(ScopeParam scope) {
        this.scope = scope;
    }

    /**
     * The config value
     * 
     */
    @XmlElement
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Whether or not the config is registered. When a config is not
     * registered, the config is not usable.
     * 
     */
    @XmlElement
    public Boolean getRegistered() {
        return registered;
    }

    public void setRegistered(Boolean registered) {
        this.registered = registered;
    }

    /**
     * Whether or not the config is system generated.
     * 
     */
    @XmlElement(name = "system_default")
    public Boolean getSystemDefault() {
        return systemDefault;
    }

    public void setSystemDefault(Boolean systemDefault) {
        this.systemDefault = systemDefault;
    }

    /**
     * The related config type
     * 
     */
    @XmlElement(name = "config_type")
    public RelatedConfigTypeRep getConfigType() {
        return configType;
    }

    public void setConfigType(RelatedConfigTypeRep configType) {
        this.configType = configType;
    }

}
