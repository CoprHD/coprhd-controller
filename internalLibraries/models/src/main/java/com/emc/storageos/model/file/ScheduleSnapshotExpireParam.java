/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a schedule snapshot expire duration, specified
 * during schedule policy creation.
 * 
 * @author prasaa9
 * 
 */
@XmlRootElement
public class ScheduleSnapshotExpireParam {

    // Schedule Snapshot expire type e.g hours, days, weeks, months or never
    private String expireType;

    // Schedule Snapshot expire after
    private int expireValue;

    @XmlElement(required = true, name = "expire_type")
    public String getExpireType() {
        return expireType;
    }

    public void setExpireType(String expireType) {
        this.expireType = expireType;
    }

    @XmlElement(name = "expire_value")
    public int getExpireValue() {
        return expireValue;
    }

    public void setExpireValue(int expireValue) {
        this.expireValue = expireValue;
    }

}
