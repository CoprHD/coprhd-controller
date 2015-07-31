/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "variables")
public class CustomConfigVariableList {
    private List<VariableParam> variables;

    public CustomConfigVariableList() {
    }

    public CustomConfigVariableList(List<VariableParam> variables) {
        this.variables = variables;
    }

    @XmlElement(name = "variable")
    public List<VariableParam> getVariables() {
        return variables;
    }

    public void setVariables(List<VariableParam> variables) {
        this.variables = variables;
    }

}
