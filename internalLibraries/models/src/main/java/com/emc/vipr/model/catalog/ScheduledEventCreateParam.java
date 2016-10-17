/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "scheduled_event_create")
public class ScheduledEventCreateParam {

    private ScheduleInfo scheduleInfo;
    private OrderCreateParam orderCreateParam;

    @XmlElement(name = "scheduleInfo")
    public ScheduleInfo getScheduleInfo() {
        return scheduleInfo;
    }

    public void setScheduleInfo(ScheduleInfo scheduleInfo) {
        this.scheduleInfo = scheduleInfo;
    }

    @XmlElement(name = "orderCreateParam")
    public OrderCreateParam getOrderCreateParam() {
        return orderCreateParam;
    }

    public void setOrderCreateParam(OrderCreateParam orderCreateParam) {
        this.orderCreateParam = orderCreateParam;
    }
}
