/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "order_create")
public class OrderCreateParam extends OrderCommonParam {

    private URI tenantId;

    private URI scheduledEventId;

    private String scheduledTime;
    
    private Integer maxNumOfRetainedCopies;
    
    @XmlElement(name = "tenantId")
    public URI getTenantId() {
        return tenantId;
    }

    public void setTenantId(URI tenantId) {
        this.tenantId = tenantId;
    }

    @XmlElement(name = "scheduledEventId")
    public URI getScheduledEventId() {
        return scheduledEventId;
    }

    public void setScheduledEventId(URI scheduledEventId) {
        this.scheduledEventId = scheduledEventId;
    }


    @XmlElement(name = "scheduledTime")
    public String getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    @XmlElement(name = "maxNumOfRetainedCopies")
	public Integer getMaxNumOfRetainedCopies() {
		return maxNumOfRetainedCopies;
	}

	public void setMaxNumOfRetainedCopies(Integer maxNumOfRetainedCopies) {
		this.maxNumOfRetainedCopies = maxNumOfRetainedCopies;
	}
    
}
