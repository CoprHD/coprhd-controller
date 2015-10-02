/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "variable")
public class VariableParam {
    private String name;
    private String sampleValue;
    private boolean isRecommended;

    /**
     * The name of the variable
     * 
     */
    @XmlElement(name = "display_name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The sample value of the variable
     * 
     */
    @XmlElement(name = "sample_value")
    public String getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(String sample) {
        this.sampleValue = sample;
    }

    /**
     * Whether or not this variable is recommended to be used when constructing the
     * config value
     * 
     */
    @XmlElement(name = "recommended")
    public boolean getIsRecommended() {
        return isRecommended;
    }

    public void setIsRecommended(boolean isRecommended) {
        this.isRecommended = isRecommended;
    }

}
