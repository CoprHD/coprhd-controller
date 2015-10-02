/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
     */
    @XmlElement(name = "preview_variable")
    public List<PreviewVariableParam> getPreviewVariables() {
        return previewVariables;
    }

    public void setPreviewVariables(List<PreviewVariableParam> previewVariables) {
        this.previewVariables = previewVariables;
    }

}
