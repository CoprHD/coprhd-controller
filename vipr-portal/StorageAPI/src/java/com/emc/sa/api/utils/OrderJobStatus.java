/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;

import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

import com.emc.storageos.db.client.model.uimodels.OrderStatus;

import com.emc.vipr.model.catalog.OrderJobInfo;

public class OrderJobStatus implements CoordinatorSerializable {
    private static final Logger log = LoggerFactory.getLogger(OrderJobStatus.class);

    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    private OrderServiceJob.JobType type;
    private OrderStatus status = null;
    private long startTime = -1;
    private long endTime = -1;
    private List<URI> tids;
    private List<URI> orderIDs = new ArrayList(); //orders to be downloaded given by REST API

    //for audit log
    private URI tenantId;
    private URI userId;

    // Total number orders to be deleted in this job
    private long total = -1;

    // key=timestamp when this round of deleting happens
    // value = number of orders deleted in this round
    private TreeMap<Long, Long> completedMap = new TreeMap<>();
    private long nCompleted = 0; // number of Order objects physically deleted from the DB

    private long nFailed = 0;  // Number of Orders failed to be deleted or downloaded so far
    private long timeUsedPerOrder = -1;  //The time used to delete or download an order

    // used to deserialize from ZK
    public OrderJobStatus() {
    }

    public OrderJobStatus(OrderServiceJob.JobType type, long startTime, long endTime, List<URI> tids,
                          URI tid, URI uid, OrderStatus status) {
        this.type = type;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.tids = tids;

        this.tenantId = tid;
        this.userId = uid;
    }

    public List<URI> getOrderIDs() {
        return orderIDs;
    }

    public void setOrderIDs(List<URI> orderIDs) {
        this.orderIDs = orderIDs;
        total = orderIDs.size();
    }

    public OrderServiceJob.JobType getType() {
        return type;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public TreeMap<Long, Long> getCompleted() {
        return completedMap;
    }

    public void addCompleted(long n) {
        long now = System.currentTimeMillis();
        completedMap.put(now, n);
    }

    public URI getTenantId() {
        return tenantId;
    }

    public URI getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getNCompleted() {
        return nCompleted;
    }

    public void setNCompleted(long n) {
        nCompleted = n;
    }

    @JsonIgnore
    private long getCompletedNumber() {
        long completed = 0;
        for (long n : completedMap.values()) {
            completed += n;
        }

        completed += nCompleted;
        return completed;
    }

    @JsonIgnore
    public void addToDeletedNumber(long n) {
        nCompleted += n;
    }

    @JsonIgnore
    public boolean isFinished() {
        return (getCompletedNumber() + nFailed) == total;
    }

    public long getFailed() {
        return nFailed;
    }

    public void setFailed(long n) {
        nFailed = n;
    }

    @JsonIgnore
    public void addFailed(long n) {
        nFailed +=n;
    }

    public long getTimeUsedPerOrder() {
        return timeUsedPerOrder;
    }

    public void setTimeUsedPerOrder(long timeUsedPerOrder) {
        this.timeUsedPerOrder = timeUsedPerOrder;
    }

    public List<URI> getTids() {
        return tids;
    }

    @Override
    public String encodeAsString() {
        return toString();
    }

    @Override
    public OrderJobStatus decodeFromString(String infoStr) throws FatalCoordinatorException {
        try {
            mapper.readerForUpdating(this).readValue(infoStr);
            return this;
        } catch (IOException e) {
            log.error("Failed to decode data string", e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }

    @Override
    public String toString() {
        try {
            String str = mapper.writeValueAsString(this);
            return str;
        } catch (IOException e) {
            log.error("Failed to serialize this object", e);
        }
        return null;
    }

    @Override
    @JsonIgnore
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return null;
    }

    @JsonIgnore
    public OrderJobInfo toOrderJobInfo() {
        OrderJobInfo info = new OrderJobInfo();

        info.setTotal(total);
        info.setStartTime(startTime);
        info.setEndTime(endTime);
        info.setCompleted(getCompletedNumber());
        info.setFailed(getFailed());
        info.setTimeUsedPerOrder(timeUsedPerOrder);
        info.setTids(Collections.unmodifiableList(tids));

        return info;
    }
}
