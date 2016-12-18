/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.vipr.model.catalog.OrderJobInfo;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class OrderJobStatus implements CoordinatorSerializable {
    private static final Logger log = LoggerFactory.getLogger(OrderJobStatus.class);

    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    private long startTime = -1;
    private long endTime = -1;
    private List<URI> tids;

    // Total number orders to be deleted in this job
    private long total = -1;

    // key=timestamp when this round of deleting happens
    // value = number of orders deleted in this round
    private TreeMap<Long, Long> completedMap = new TreeMap<>();

    private long nFailed = 0;  // Number of Orders failed to be deleted or downloaded so far
    private long timeUsedPerOrder = -1;  //The time used to delete or download an order

    // used to deserialize from ZK
    public OrderJobStatus() {
    }

    public OrderJobStatus(long startTime, long endTime, List<URI> tids) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.tids = tids;
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

    @JsonIgnore
    private long getCompletedNumber() {
        long nCompleted = 0;
        for (long n : completedMap.values()) {
            nCompleted += n;
        }

        return nCompleted;
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
            log.info("lbyt1: str={}", infoStr);
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
            log.info("lbyt: str={}", str);
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
