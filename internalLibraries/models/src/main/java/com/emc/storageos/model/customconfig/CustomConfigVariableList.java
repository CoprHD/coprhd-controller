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

@XmlRootElement(name = "variables")
public class CustomConfigVariableList {
    private List<VariableParam> variables;

    public CustomConfigVariableList() {}
    
    public CustomConfigVariableList(List<VariableParam> variables) {
        this.variables = variables;
    }
    
    @XmlElement(name="variable")
    public List<VariableParam> getVariables() {
        return variables;
    }

    public void setVariables(List<VariableParam> variables) {
        this.variables = variables;
    }
    
    
}
