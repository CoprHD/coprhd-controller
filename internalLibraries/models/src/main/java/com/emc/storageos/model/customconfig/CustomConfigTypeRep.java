/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "config_type")
public class CustomConfigTypeRep {
    private String configName;
    private ScopeParamList scopes;
    private CustomConfigVariableList variables;
    private String type;
    private CustomConfigRuleList rules;
    private String configType;

    /**
     * The name of the config type
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
     * The valid scopes of this config type
     * 
     */
    @XmlElement(name = "scopes")
    public ScopeParamList getScopes() {
        return scopes;
    }

    public void setScopes(ScopeParamList scopes) {
        this.scopes = scopes;
    }

    /**
     * The variables of this config type could be used when creating config value
     * 
     */
    @XmlElement(name = "variables")
    public CustomConfigVariableList getVariables() {
        return variables;
    }

    public void setVariables(CustomConfigVariableList variables) {
        this.variables = variables;
    }

    /**
     * The data type of the config value
     * 
     */
    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The rules applied to the config value
     * 
     */
    @XmlElement(name = "rules")
    public CustomConfigRuleList getRules() {
        return rules;
    }

    public void setRules(CustomConfigRuleList rules) {
        this.rules = rules;
    }

    /**
     * The configType of the config value.
     * 
     * Current values can be {@code CustomName} or {@code SimpleValue}.
     * 
     */
    @XmlElement(name = "config_type")
    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }
}
