/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import com.emc.storageos.coordinator.client.model.CoordinatorClassInfo;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

public class DbRepairJobState implements CoordinatorSerializable {
    private static final Logger log = LoggerFactory.getLogger(DbRepairJobState.class);

    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    private String lastSuccessDigest;
    private Boolean lastCrossVdc;
    private Long lastSuccessStartTime;
    private Long lastSuccessEndTime;
    private String currentDigest;
    private String currentWorker; // For debugging so we know which node is driving repair
    private Long currentStartTime;
    private Long currentUpdateTime;
    private String currentToken;
    private Integer currentProgress;
    private Integer currentRetry;
    private Boolean currentCrossVdc;

    public DbRepairJobState() {
    }

    public DbRepairJobState(String clusterDigest) {
        this.currentRetry = 0;
        this.currentProgress = 0;
        this.currentDigest = clusterDigest;
        this.currentStartTime = System.currentTimeMillis();
        this.currentUpdateTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            log.error("Failed to serialize this object", e);
            return null;
        }
    }

    public String getLastSuccessDigest() {
        return lastSuccessDigest;
    }

    public void setLastSuccessDigest(String lastSuccessDigest) {
        this.lastSuccessDigest = lastSuccessDigest;
    }

    public Boolean getLastCrossVdc() {
        return lastCrossVdc;
    }

    public void setLastCrossVdc(Boolean lastCrossVdc) {
        this.lastCrossVdc = lastCrossVdc;
    }

    public Long getLastSuccessStartTime() {
        return lastSuccessStartTime;
    }

    public void setLastSuccessStartTime(Long lastSuccessStartTime) {
        this.lastSuccessStartTime = lastSuccessStartTime;
    }

    public Long getLastSuccessEndTime() {
        return lastSuccessEndTime;
    }

    public void setLastSuccessEndTime(Long lastSuccessEndTime) {
        this.lastSuccessEndTime = lastSuccessEndTime;
    }

    public String getCurrentDigest() {
        return currentDigest;
    }

    public void setCurrentDigest(String currentDigest) {
        this.currentDigest = currentDigest;
    }

    public String getCurrentWorker() {
        return currentWorker;
    }

    public void setCurrentWorker(String currentWorker) {
        this.currentWorker = currentWorker;
    }

    public Long getCurrentStartTime() {
        return currentStartTime;
    }

    public void setCurrentStartTime(Long currentStartTime) {
        this.currentStartTime = currentStartTime;
    }

    public Long getCurrentUpdateTime() {
        return currentUpdateTime;
    }

    public void setCurrentUpdateTime(Long currentUpdateTime) {
        this.currentUpdateTime = currentUpdateTime;
    }

    public String getCurrentToken() {
        return currentToken;
    }

    public void setCurrentToken(String currentToken) {
        this.currentToken = currentToken;
    }

    public Integer getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(Integer currentProgress) {
        this.currentProgress = currentProgress;
    }

    public Integer getCurrentRetry() {
        return currentRetry;
    }

    public void setCurrentRetry(Integer currentRetry) {
        this.currentRetry = currentRetry;
    }

    @Deprecated
    public Boolean getCurrentCrossVdc() {
        return currentCrossVdc;
    }

    @Deprecated
    public void setCurrentCrossVdc(Boolean currentCrossVdc) {
        this.currentCrossVdc = currentCrossVdc;
    }

    @Override
    public String encodeAsString() {
        return toString();
    }

    @Override
    public DbRepairJobState decodeFromString(String infoStr) throws FatalCoordinatorException {
        try {
            mapper.readerForUpdating(this).readValue(infoStr);
            return this;
        } catch (IOException e) {
            log.error("Failed to decode data string", e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }

    @Override
    @JsonIgnore
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return null;
    }

    // See if we can resume: there's is something to resume, and the retry time is not reaching limit, and is in compatible config
    @JsonIgnore
    public boolean canResume(String clusterDigest, int maxRetryTimes) {
        return this.currentToken != null && this.currentRetry < maxRetryTimes
                && this.currentDigest.equals(clusterDigest);
    }

    @JsonIgnore
    private boolean isIntervalElapse() {
        long now = System.currentTimeMillis();
        return (this.lastSuccessEndTime + DbRepairRunnable.INTERVAL_TIME_IN_MINUTES * 60 * 1000 < now);
    }

    @JsonIgnore
    public boolean notSuitableForNewRepair(String clusterDigest) {
        return this.lastSuccessEndTime != null
                && this.lastSuccessDigest.equals(clusterDigest)
                && !isIntervalElapse();
    }

    @JsonIgnore
    public void increaseRetry() {
        this.currentRetry++;
    }

    @JsonIgnore
    public void updateProgress(int progress, String currentToken) {
        this.currentProgress = progress;
        this.currentToken = currentToken;
    }

    @JsonIgnore
    public boolean success(String clusterDigest) {
        if (!clusterDigest.equals(this.currentDigest)) {
            return false;
        }

        this.lastSuccessDigest = this.getCurrentDigest();
        this.lastSuccessStartTime = this.getCurrentStartTime();
        this.lastSuccessEndTime = System.currentTimeMillis();
        cleanCurrentFields();
        return true;
    }

    @JsonIgnore
    public void cleanCurrentFields() {
        this.currentToken = null;
        this.currentProgress = null;
        this.currentRetry = null;
        this.currentDigest = null;
        this.currentWorker = null;
        this.currentStartTime = null;
        this.currentCrossVdc = null;
    }

    @JsonIgnore
    public boolean retry(int maxRetryTimes) {
        this.increaseRetry();
        if (this.getCurrentRetry() > maxRetryTimes) {
            log.error("Current db repair retry number({}) exceed the limit({})",
                    this.getCurrentRetry(), maxRetryTimes);
            return false;
        }
        return true;
    }

    @JsonIgnore
    public void fail(int maxRetryTimes) {
        if (this.getCurrentRetry() <= maxRetryTimes) {
            this.increaseRetry();
        }
    }

    @JsonIgnore
    public void inProgress(String clusterDigest) {
        this.currentRetry = 0;
        this.currentProgress = 0;
        this.currentDigest = clusterDigest;
        this.currentStartTime = System.currentTimeMillis();
        this.currentUpdateTime = System.currentTimeMillis();
    }
}
