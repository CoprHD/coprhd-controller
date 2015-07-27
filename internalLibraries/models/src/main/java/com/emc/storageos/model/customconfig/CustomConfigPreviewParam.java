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

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "config_preview")
public class CustomConfigPreviewParam {
    private String configType;
    private String value;
    private ScopeParam scope;
    private List<PreviewVariableParam> previewVariables;
    
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
     * The scope 
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
     * The variable values used to generate config value
     *
     * @valid none
     */
    @XmlElement(name="preview_variable")
    public List<PreviewVariableParam> getPreviewVariables() {
        return previewVariables;
    }
    public void setPreviewVariables(List<PreviewVariableParam> previewVariables) {
        this.previewVariables = previewVariables;
    }

    
}
