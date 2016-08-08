/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "scheduled_event_update")
public class ScheduledEventUpdateParam {

    private ScheduleInfo scheduleInfo;

    private Integer maxNumOfRetainedCopies;
    
    @XmlElement(name = "scheduleInfo")
    public ScheduleInfo getScheduleInfo() {
        return scheduleInfo;
    }

    public void setScheduleInfo(ScheduleInfo scheduleInfo) {
        this.scheduleInfo = scheduleInfo;
    }

    @XmlElement(name = "maxNumOfRetainedCopies")
	public Integer getMaxNumOfRetainedCopies() {
		return maxNumOfRetainedCopies;
	}

	public void setMaxNumOfRetainedCopies(Integer maxNumOfRetainedCopies) {
		this.maxNumOfRetainedCopies = maxNumOfRetainedCopies;
	}
    
}
