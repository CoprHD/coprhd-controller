/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "scope")
public class ScopeParam {
    private String type;
    private String value;

    public ScopeParam() {
    }

    public ScopeParam(String type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * The scope type
     * 
     * @valid none
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
    @XmlElement
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
