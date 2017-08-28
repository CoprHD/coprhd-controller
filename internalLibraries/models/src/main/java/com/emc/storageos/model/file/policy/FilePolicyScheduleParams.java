/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

/**
 * @author jainm15
 */
public class FilePolicyScheduleParams implements Serializable {

    private static final long serialVersionUID = 1L;

    // Type of schedule policy e.g days, weeks or months, hours and minutes
    private String scheduleFrequency;

    // Policy run on every
    private Long scheduleRepeat;

    // Time when policy run
    private String scheduleTime;

    // Day of week when policy run
    private String scheduleDayOfWeek;

    // Day of month when policy run
    private Long scheduleDayOfMonth;

    public FilePolicyScheduleParams() {

    }

    /**
     * Type of schedule policy e.g days, weeks , months hours and minutes
     * 
     * @return
     */
    @XmlElement(required = true, name = "schedule_frequency")
    public String getScheduleFrequency() {
        return this.scheduleFrequency;
    }

    public void setScheduleFrequency(String scheduleFrequency) {
        this.scheduleFrequency = scheduleFrequency;
    }

    /**
     * Policy run on every
     * 
     * @return
     */
    @XmlElement(required = true, name = "schedule_repeat")
    public Long getScheduleRepeat() {
        return this.scheduleRepeat;
    }

    public void setScheduleRepeat(Long scheduleRepeat) {
        this.scheduleRepeat = scheduleRepeat;
    }

    /**
     * Time when policy run
     * 
     * @return
     */
    @XmlElement(required = true, name = "schedule_time")
    public String getScheduleTime() {
        return this.scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    /**
     * Day of week when policy run
     * 
     * @return
     */
    @XmlElement(name = "schedule_day_of_week")
    public String getScheduleDayOfWeek() {
        return this.scheduleDayOfWeek;
    }

    public void setScheduleDayOfWeek(String scheduleDayOfWeek) {
        this.scheduleDayOfWeek = scheduleDayOfWeek;
    }

    /**
     * Day of month when policy run
     * 
     * @return
     */
    @XmlElement(name = "schedule_day_of_month")
    public Long getScheduleDayOfMonth() {
        return this.scheduleDayOfMonth;
    }

    public void setScheduleDayOfMonth(Long scheduleDayOfMonth) {
        this.scheduleDayOfMonth = scheduleDayOfMonth;
    }
}
