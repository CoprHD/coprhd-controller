/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.schedulepolicy;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a schedule policy, specified
 * during schedule policy creation.
 * 
 * @author prasaa9
 * 
 */
@XmlRootElement
public class SchedulePolicyParam {

    // File Policy schedule type - daily, weekly, monthly.
    private String scheduleFrequency;

    // Policy execution repeats on
    private int scheduleRepeat;

    // Time when policy run
    private String scheduleTime;

    // week day when policy run
    private String scheduleDayOfWeek;

    // Day of the month
    private int scheduleDayOfMonth;

    @XmlElement(required = true, name = "schedule_frequency")
    public String getScheduleFrequency() {
        return scheduleFrequency;
    }

    public void setScheduleFrequency(String scheduleFrequency) {
        this.scheduleFrequency = scheduleFrequency;
    }

    @XmlElement(required = true, name = "schedule_repeat")
    public int getScheduleRepeat() {
        return scheduleRepeat;
    }

    public void setScheduleRepeat(int scheduleRepeat) {
        this.scheduleRepeat = scheduleRepeat;
    }

    @XmlElement(required = true, name = "schedule_time")
    public String getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    @XmlElement(name = "schedule_day_of_week")
    public String getScheduleDayOfWeek() {
        return scheduleDayOfWeek;
    }

    public void setScheduleDayOfWeek(String scheduleDayOfWeek) {
        this.scheduleDayOfWeek = scheduleDayOfWeek;
    }

    @XmlElement(name = "schedule_day_of_month")
    public int getScheduleDayOfMonth() {
        return scheduleDayOfMonth;
    }

    public void setScheduleDayOfMonth(int scheduleDayOfMonth) {
        this.scheduleDayOfMonth = scheduleDayOfMonth;
    }

}
