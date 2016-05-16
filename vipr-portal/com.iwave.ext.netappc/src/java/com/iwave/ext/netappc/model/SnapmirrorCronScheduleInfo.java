/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

import java.util.List;

public class SnapmirrorCronScheduleInfo {

    public List<Integer> getJobScheduleCronDay() {
        return jobScheduleCronDay;
    }

    public void setJobScheduleCronDay(List<Integer> jobScheduleCronDay) {
        this.jobScheduleCronDay = jobScheduleCronDay;
    }

    public List<Integer> getJobScheduleCronDayofWeek() {
        return jobScheduleCronDayofWeek;
    }

    public void setJobScheduleCronDayofWeek(List<Integer> jobScheduleCronDayofWeek) {
        this.jobScheduleCronDayofWeek = jobScheduleCronDayofWeek;
    }

    public List<Integer> getJobScheduleCronHour() {
        return jobScheduleCronHour;
    }

    public void setJobScheduleCronHour(List<Integer> jobScheduleCronHour) {
        this.jobScheduleCronHour = jobScheduleCronHour;
    }

    public List<Integer> getJobScheduleCronMinute() {
        return jobScheduleCronMinute;
    }

    public void setJobScheduleCronMinute(List<Integer> jobScheduleCronMinute) {
        this.jobScheduleCronMinute = jobScheduleCronMinute;
    }

    public List<Integer> getJobScheduleCronMonth() {
        return jobScheduleCronMonth;
    }

    public void setJobScheduleCronMonth(List<Integer> jobScheduleCronMonth) {
        this.jobScheduleCronMonth = jobScheduleCronMonth;
    }

    public String getJobScheduleName() {
        return jobScheduleName;
    }

    public void setJobScheduleName(String jobScheduleName) {
        this.jobScheduleName = jobScheduleName;
    }

    private List<Integer> jobScheduleCronDay;

    private List<Integer> jobScheduleCronDayofWeek;

    private List<Integer> jobScheduleCronHour;

    private List<Integer> jobScheduleCronMinute;

    private List<Integer> jobScheduleCronMonth;

    private String jobScheduleName;

}
