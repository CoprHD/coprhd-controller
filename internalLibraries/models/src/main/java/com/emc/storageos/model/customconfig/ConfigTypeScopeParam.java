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


@XmlRootElement(name = "config_type_scope")
public class ConfigTypeScopeParam {

    private String type;
    private List<String> value;
    
    public ConfigTypeScopeParam() {}
    
    public ConfigTypeScopeParam(String type, List<String> value) {
        this.type = type;
        this.value = value;
    }
    
    /**
     * The scope type
     *
     * @valid systemType
     * @valid global
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
     * @valid none
     */
    @XmlElement(name = "value")
    public List<String> getValue() {
        return value;
    }
    public void setValue(List<String> value) {
        this.value = value;
    }
    
    
    

}
