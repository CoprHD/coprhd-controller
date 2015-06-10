/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.valid.Length;


public class ExecutionWindowCommonParam  {

    private String name;
    private Integer hourOfDayInUTC;                     
    private Integer minuteOfHourInUTC;                  
    private Integer executionWindowLength;              
    private String executionWindowLengthType;           
    private String executionWindowType;                 
    private Integer dayOfWeek;                          
    private Integer dayOfMonth;                         
    private Boolean lastDayOfMonth;     
    
    @XmlElement(required = true, name = "hour_of_day_in_utc")
    public Integer getHourOfDayInUTC() {
        return hourOfDayInUTC;
    }
    public void setHourOfDayInUTC(Integer hourOfDayInUTC) {
        this.hourOfDayInUTC = hourOfDayInUTC;
    }
    
    @XmlElement(name = "minute_of_hour_in_utc")
    public Integer getMinuteOfHourInUTC() {
        return minuteOfHourInUTC;
    }
    public void setMinuteOfHourInUTC(Integer minuteOfHourInUTC) {
        this.minuteOfHourInUTC = minuteOfHourInUTC;
    }
    
    @XmlElement(required = true, name = "execution_window_length")
    @Length(min = 1)
    public Integer getExecutionWindowLength() {
        return executionWindowLength;
    }
    public void setExecutionWindowLength(Integer executionWindowLength) {
        this.executionWindowLength = executionWindowLength;
    }
    
    @XmlElement(required = true, name = "execution_window_length_type")
    public String getExecutionWindowLengthType() {
        return executionWindowLengthType;
    }
    public void setExecutionWindowLengthType(String executionWindowLengthType) {
        this.executionWindowLengthType = executionWindowLengthType;
    }
    
    @XmlElement(required = true, name = "execution_window_type")
    public String getExecutionWindowType() {
        return executionWindowType;
    }
    public void setExecutionWindowType(String executionWindowType) {
        this.executionWindowType = executionWindowType;
    }
    
    @XmlElement(name = "day_of_week")
    @Length(min = 1, max = 7)
    public Integer getDayOfWeek() {
        return dayOfWeek;
    }
    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }
    
    @XmlElement(name = "day_of_month")
    public Integer getDayOfMonth() {
        return dayOfMonth;
    }
    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }
    
    @XmlElement(name = "last_day_of_month")
    public Boolean getLastDayOfMonth() {
        return lastDayOfMonth;
    }
    public void setLastDayOfMonth(Boolean lastDayOfMonth) {
        this.lastDayOfMonth = lastDayOfMonth;
    }
    
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    
    
    
}
