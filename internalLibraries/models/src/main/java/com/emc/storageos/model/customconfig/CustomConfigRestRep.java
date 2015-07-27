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

import java.net.URI;
import java.util.Calendar;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.adapters.CalendarAdapter;

/**
 * Information relevant to a custom config, returned as a 
 * response to a REST request.
 *
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "config")
public class CustomConfigRestRep extends DataObjectRestRep{
    private ScopeParam scope;
    private String value;
    private Boolean registered;
    private Boolean systemDefault;
    private RelatedConfigTypeRep configType;
    
    /**
     * The scope of this config applies to
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
     * The config value
     *
     * @valid none
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
    
    /**
     * Whether or not the config is system generated. 
     *
     * @valid true
     * @valid false
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
     * @valid none
     */
    @XmlElement(name = "config_type")
    public RelatedConfigTypeRep getConfigType() {
        return configType;
    }
    public void setConfigType(RelatedConfigTypeRep configType) {
        this.configType = configType;
    }
    
}
