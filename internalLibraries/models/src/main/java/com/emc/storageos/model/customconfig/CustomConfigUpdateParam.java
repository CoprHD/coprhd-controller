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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Parameter for custom config update.  
 */
@XmlRootElement(name = "config_update")
public class CustomConfigUpdateParam {
    private String value;
    
    public CustomConfigUpdateParam() {}
    
    public CustomConfigUpdateParam(String value) {
        this.value = value;
    }
    
    /**
     * The config value to be changed
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
    
    

}
