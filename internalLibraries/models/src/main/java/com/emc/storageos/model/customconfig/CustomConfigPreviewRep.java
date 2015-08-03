/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "preview")
public class CustomConfigPreviewRep {
    private String resolvedValue;

    public CustomConfigPreviewRep() {
    }

    public CustomConfigPreviewRep(String resolvedValue) {
        this.resolvedValue = resolvedValue;
    }

    @XmlElement(name = "resolved_value")
    public String getResolvedValue() {
        return resolvedValue;
    }

    public void setResolvedValue(String resolvedValue) {
        this.resolvedValue = resolvedValue;
    }

}
