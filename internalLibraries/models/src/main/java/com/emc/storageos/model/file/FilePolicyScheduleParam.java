/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file policy schedule, specified
 * during file policy creation.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement
public class FilePolicyScheduleParam {

    // File Policy schedule type - daily, weekly, monthly, yearly
    private String scheduleType;

    // Number of times policy run
    private int scheduleNumber;

    // Time when policy run
    private String scheduleTime;

    // Month when policy run
    private int scheduleMonth;

    // Day when policy run
    private String scheduleDay;

    // File Policy expire type - hours, days, weeks, months, years
    private String expireType;

    // Policy expire after
    private int expireValue;

    @XmlElement(required = true, name = "scheduleType")
    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    @XmlElement(name = "scheduleNumber")
    public int getScheduleNumber() {
        return scheduleNumber;
    }

    public void setScheduleNumber(int scheduleNumber) {
        this.scheduleNumber = scheduleNumber;
    }

    @XmlElement(required = true, name = "scheduleTime")
    public String getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    @XmlElement(name = "scheduleMonth")
    public int getScheduleMonth() {
        return scheduleMonth;
    }

    public void setScheduleMonth(int scheduleMonth) {
        this.scheduleMonth = scheduleMonth;
    }

    @XmlElement(name = "scheduleDay")
    public String getScheduleDay() {
        return scheduleDay;
    }

    public void setScheduleDay(String scheduleDay) {
        this.scheduleDay = scheduleDay;
    }

    @XmlElement(name = "expireType")
    public String getExpireType() {
        return expireType;
    }

    public void setExpireType(String expireType) {
        this.expireType = expireType;
    }

    @XmlElement(name = "expireValue")
    public int getExpireValue() {
        return expireValue;
    }

    public void setExpireValue(int expireValue) {
        this.expireValue = expireValue;
    }

}
