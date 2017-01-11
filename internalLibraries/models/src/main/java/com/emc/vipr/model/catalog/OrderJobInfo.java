/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.List;

@XmlRootElement(name = "order_job_info")
public class OrderJobInfo {
    private long startTime = -1;
    private long endTime = -1;
    private List<URI> tids;

    // Total number orders to be deleted in this job
    private long total = -1;

    private long nCompleted = -1;
    private long nFailed = -1;
    private long timeUsedPerOrder = -1; //The time used to delete or download an order

    public OrderJobInfo() {

    }

    @XmlElement(name = "start_time")
    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    @XmlElement(name = "end_time")
    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @XmlElement(name = "tids")
    public List<URI> getTids() {
        return tids;
    }

    public void setTids(List<URI> tids) {
        this.tids = tids;
    }

    @XmlElement(name = "total")
    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    @XmlElement(name = "completed_number")
    public long getCompleted() {
        return nCompleted;
    }

    public void setCompleted(long nCompleted) {
        this.nCompleted = nCompleted;
    }

    @XmlElement(name = "failed_number")
    public long getFailed() {
        return nFailed;
    }

    public void setFailed(long nFailed) {
        this.nFailed = nFailed;
    }

    @XmlElement(name = "time_used_per_order")
    public long getTimeUsedPerOrder() {
        return timeUsedPerOrder;
    }

    public void setTimeUsedPerOrder(long timeUsedPerOrder) {
        this.timeUsedPerOrder = timeUsedPerOrder;
    }
    
    public boolean isNoJobOrJobDone() {
        return this.total == -1 || this.total == this.nCompleted + this.nFailed;
    }
}
