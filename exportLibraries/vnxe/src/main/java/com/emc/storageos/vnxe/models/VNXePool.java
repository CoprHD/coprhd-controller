/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

/*
 * represents storage pool
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXePool extends VNXeBase {
    private String name;
    private String instanceId;
    private Health health;
    private List<Integer> operationalStatus;
    private String description;
    private PoolSystemDefinedEnum type;
    private Integer raidType;
    private double sizeFree = -1;
    private double sizeTotal = -1;
    private double sizeUsed = -1;
    private double sizeSubscribed = -1;
    private int alertThreshold;
    private float harvestHightThreshold;
    private float harvestLowThreshold;
    private float snapHarvestHighThreshold;
    private boolean isFASTCacheEnabled;
    private List<PoolTier> tiers;
    private String creationTime;
    private boolean isEmpty;
    private PoolFASTVP poolFastVP;
    private boolean isHarvestEnabled;
    private HarvestStateEnum harvestState;
    private boolean isSnapHarvestEnabled;
    private long metadataSizeSubscribed;
    private long snapSizeSubscribed;
    private long metadataSizeUsed;
    private long snapSizeUsed;
    private long rebalanceProgress;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PoolSystemDefinedEnum getType() {
        return type;
    }

    public void setType(PoolSystemDefinedEnum type) {
        this.type = type;
    }

    public Integer getRaidType() {
        return raidType;
    }

    public RaidTypeEnum getRaidTypeEnum() {
        return RaidTypeEnum.getEnumValue(raidType);
    }

    public void setRaidType(Integer raidGroupLevel) {
        this.raidType = raidGroupLevel;
    }

    public double getSizeFree() {
        return sizeFree;
    }

    public void setSizeFree(double sizeFree) {
        this.sizeFree = sizeFree;
    }

    public double getSizeTotal() {
        return sizeTotal;
    }

    public void setSizeTotal(double sizeTotal) {
        this.sizeTotal = sizeTotal;
    }

    public double getSizeUsed() {
        return sizeUsed;
    }

    public void setSizeUsed(double sizeUsed) {
        this.sizeUsed = sizeUsed;
    }

    public double getSizeSubscribed() {
        return sizeSubscribed;
    }

    public void setSizeSbuscribed(double sizeSubscribed) {
        this.sizeSubscribed = sizeSubscribed;
    }

    public int getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(int alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    public float getHarvestHightThreshold() {
        return harvestHightThreshold;
    }

    public void setHarvestHightThreshold(float harvestHightThreshold) {
        this.harvestHightThreshold = harvestHightThreshold;
    }

    public float getHarvestLowThreshold() {
        return harvestLowThreshold;
    }

    public void setHarvestLowThreshold(float harvestLowThreshold) {
        this.harvestLowThreshold = harvestLowThreshold;
    }

    public float getSnapHarvestHighThreshold() {
        return snapHarvestHighThreshold;
    }

    public void setSnapHarvestHighThreshold(float snapHarvestHighThreshold) {
        this.snapHarvestHighThreshold = snapHarvestHighThreshold;
    }

    public boolean getIsFASTCacheEnabled() {
        return isFASTCacheEnabled;
    }

    public void setIsFASTCacheEnabled(boolean isFASTCacheEnabled) {
        this.isFASTCacheEnabled = isFASTCacheEnabled;
    }

    public List<PoolTier> getTiers() {
        return tiers;
    }

    public void setTiers(List<PoolTier> tiers) {
        this.tiers = tiers;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(String creationTime) {
        this.creationTime = creationTime;
    }

    public boolean getIsEmpty() {
        return isEmpty;
    }

    public void setIsEmpty(boolean isEmpty) {
        this.isEmpty = isEmpty;
    }

    public PoolFASTVP getPoolFastVP() {
        return poolFastVP;
    }

    public void setPoolFastVP(PoolFASTVP poolFastVP) {
        this.poolFastVP = poolFastVP;
    }

    public boolean getIsHarvestEnabled() {
        return isHarvestEnabled;
    }

    public void setIsHarvestEnabled(boolean isHarvestEnabled) {
        this.isHarvestEnabled = isHarvestEnabled;
    }

    public HarvestStateEnum getHarvestState() {
        return harvestState;
    }

    public void setHarvestState(HarvestStateEnum harvestState) {
        this.harvestState = harvestState;
    }

    public boolean getIsSnapHarvestEnabled() {
        return isSnapHarvestEnabled;
    }

    public void setIsSnapHarvestEnabled(boolean isSnapHarvestEnabled) {
        this.isSnapHarvestEnabled = isSnapHarvestEnabled;
    }

    public long getMetadataSizeSubscribed() {
        return metadataSizeSubscribed;
    }

    public void setMetadataSizeSubscribed(long metadataSizeSubscribed) {
        this.metadataSizeSubscribed = metadataSizeSubscribed;
    }

    public long getSnapSizeSubscribed() {
        return snapSizeSubscribed;
    }

    public void setSnapSizeSubscribed(long snapSizeSubscribed) {
        this.snapSizeSubscribed = snapSizeSubscribed;
    }

    public long getMetadataSizeUsed() {
        return metadataSizeUsed;
    }

    public void setMetadataSizeUsed(long metadataSizeUsed) {
        this.metadataSizeUsed = metadataSizeUsed;
    }

    public long getSnapSizeUsed() {
        return snapSizeUsed;
    }

    public void setSnapSizeUsed(long snapSizeUsed) {
        this.snapSizeUsed = snapSizeUsed;
    }

    public long getRebalanceProgress() {
        return rebalanceProgress;
    }

    public void setRebalanceProgress(long rebalanceProgress) {
        this.rebalanceProgress = rebalanceProgress;
    }

    public String getStatus() {
        String statusString = null;
        if (operationalStatus !=null){
        for (Integer opStatus : operationalStatus) {
            if (opStatus == 2) {
                statusString = "READY";
                break;
            }

        }}
        if (statusString == null) {
            statusString = "NOTREADY";
        }
        return statusString;
    }

    public static enum HarvestStateEnum {
        IDLE,
        RUNNING,
        COULD_NOT_REACH_LWM,
        PAUSED_COULD_NOT_REACH_HWM,
        FAILED;
    }

    // @JsonSerialize(using = PoolSystemDefinedEnumSerializer.class)
    @JsonDeserialize(using = PoolSystemDefinedEnumDeserializer.class)
    public static enum PoolSystemDefinedEnum {
        PERFORMANCE(1),
        CAPACITY(3),
        BESTPERFORMANCE(9),
        CUSTOM(27);

        private int value;

        private PoolSystemDefinedEnum(int value) {
            this.value = value;
        }

        @org.codehaus.jackson.annotate.JsonValue
        public int getValue() {
            return this.value;
        }

    }

}
