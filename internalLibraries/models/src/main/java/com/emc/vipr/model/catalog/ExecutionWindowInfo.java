/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ExecutionWindowInfo extends ModelInfo {
    
    /**
     * Hour of the day for this execution window
     */
    private Integer hourOfDayInUTC;                    
    
    /**
     * Minute of the day for this execution window
     */
    private Integer minuteOfHourInUTC;            
    
    /**
     * Length of this execution window
     */
    private Integer executionWindowLength;
    
    /**
     * Length type of the execution window: MINUTES, HOURS, DAYS
     */
    private String executionWindowLengthType;          
    
    /**
     * Type of the execution window: DAILY, WEEKLY, MONTHLY
     */
    private String executionWindowType;                
    
    /**
     * Day of the week for this execution window
     */
    private Integer dayOfWeek;                        
    
    /**
     * Day of the month for this execution window
     */
    private Integer dayOfMonth;                  
    
    /**
     * Indicates the day of the week is the last day of the month
     */
    private Boolean lastDayOfMonth = Boolean.FALSE;     
    
    /**
     * Tenant that this execution window applies to
     */
    private String tenant;                             
    
    /**
     * Label for this execution window
     */
    private String label;                                

    public Integer getHourOfDayInUTC() {
        return hourOfDayInUTC;
    }

    public void setHourOfDayInUTC(Integer hourOfDayInUTC) {
        this.hourOfDayInUTC = hourOfDayInUTC;
    }

    public Integer getMinuteOfHourInUTC() {
        return minuteOfHourInUTC;
    }

    public void setMinuteOfHourInUTC(Integer minuteOfHourInUTC) {
        this.minuteOfHourInUTC = minuteOfHourInUTC;
    }

    public Integer getExecutionWindowLength() {
        return executionWindowLength;
    }

    public void setExecutionWindowLength(Integer executionWindowLength) {
        this.executionWindowLength = executionWindowLength;
    }

    public String getExecutionWindowLengthType() {
        return executionWindowLengthType;
    }

    public void setExecutionWindowLengthType(String executionWindowLengthType) {
        this.executionWindowLengthType = executionWindowLengthType;
    }

    public String getExecutionWindowType() {
        return executionWindowType;
    }

    public void setExecutionWindowType(String executionWindowType) {
        this.executionWindowType = executionWindowType;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public Boolean getLastDayOfMonth() {
        return lastDayOfMonth;
    }

    public void setLastDayOfMonth(Boolean lastDayOfMonth) {
        this.lastDayOfMonth = lastDayOfMonth;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
