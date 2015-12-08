/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file policy expire duration, specified
 * during file policy creation.
 * 
 * @author prasaa9
 * 
 */
@XmlRootElement
public class FilePolicyExpireParam {

    // File Policy expire type - hours, days, weeks, months, years
    private String expireType;

    // Policy expire after
    private int expireValue;

    @XmlElement(required = true, name = "expireType")
    public String getExpireType() {
        return expireType;
    }

    public void setExpireType(String expireType) {
        this.expireType = expireType;
    }

    @XmlElement(required = true, name = "expireValue")
    public int getExpireValue() {
        return expireValue;
    }

    public void setExpireValue(int expireValue) {
        this.expireValue = expireValue;
    }

}
