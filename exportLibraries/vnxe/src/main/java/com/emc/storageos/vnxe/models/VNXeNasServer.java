/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeNasServer extends VNXeBase {
    private String name;
    private Health health;
    private VNXeBase homeSP;
    private VNXeBase currentSP;
    private VNXePool pool;
    private boolean isSystem = false;
    private List<Integer> operationalStatus;
    private long sizeUsed;
    private NasServerModeEnum mode;
    private boolean isReplicationDestination = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public VNXeBase getHomeSP() {
        return homeSP;
    }

    public void setHomeSP(VNXeBase homeSP) {
        this.homeSP = homeSP;
    }

    public VNXeBase getCurrentSP() {
        return currentSP;
    }

    public void setCurrentSP(VNXeBase currentSP) {
        this.currentSP = currentSP;
    }

    public VNXePool getPool() {
        return pool;
    }

    public void setPool(VNXePool pool) {
        this.pool = pool;
    }

    public boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(boolean isSystem) {
        this.isSystem = isSystem;
    }

    public boolean getIsReplicationDestination() {
        return isReplicationDestination;
    }

    public void setIsReplicationDestination(boolean isReplicationDestination) {
        this.isReplicationDestination = isReplicationDestination;
    }


    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public long getSizeUsed() {
        return sizeUsed;
    }

    public void setSizeUsed(long sizeUsed) {
        this.sizeUsed = sizeUsed;
    }

    public NasServerModeEnum getMode() {
        return mode;
    }

    public void setMode(NasServerModeEnum mode) {
        this.mode = mode;
    }

    public static enum NasServerModeEnum {
        NORMAL,
        DESTINATION;
    }

}
