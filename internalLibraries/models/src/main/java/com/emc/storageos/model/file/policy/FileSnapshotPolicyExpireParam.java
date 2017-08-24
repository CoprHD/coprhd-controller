/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with snapshot expire duration, specified
 * during snapshot policy creation.
 * 
 * @author jainm15
 * 
 */
@XmlRootElement
public class FileSnapshotPolicyExpireParam implements Serializable {

    private static final long serialVersionUID = 1L;

    // Snapshot expire type e.g hours, days, weeks, months or never
    private String expireType;

    // Snapshot expire after this value
    private int expireValue;

    /**
     * Snapshot expire type e.g hours, days, weeks, months or never
     * 
     * @return
     */
    @XmlElement(required = true, name = "expire_type")
    public String getExpireType() {
        return this.expireType;
    }

    public void setExpireType(String expireType) {
        this.expireType = expireType;
    }

    /**
     * Snapshot expire after this value
     * 
     * @return
     */
    @XmlElement(required = true, name = "expire_value")
    public int getExpireValue() {
        return this.expireValue;
    }

    public void setExpireValue(int expireValue) {
        this.expireValue = expireValue;
    }

}
