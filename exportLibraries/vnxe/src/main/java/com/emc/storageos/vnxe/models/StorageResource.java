/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageResource extends VNXeBase {
    private List<Integer> operationalStatus;
    private Health health;
    private String name;
    private String description;
    private StorageResourceTypeEnum type;
    private boolean isReplicationDestination;
    private long sizeTotal;
    private long sizeUsed;
    private long sizeAllocated;
    private Integer thinStatus;
    private VNXeBase snapSchedule;
    private boolean isSnapSchedulePaused;
    private TieringPolicyEnum relocationPolicy;
    private List<Long> perTierSizeused;
    // private List<BlockHostAccess> blockHostAccess;
    private long metadataSize;
    private long metadataSizeAllocated;
    private long snapSizeTotal;
    private long snapSizeAllocated;
    private int snapCount;
    private List<VNXeBase> luns;

    public List<VNXeBase> getLuns() {
                return luns;
    }

    public void setLuns(List<VNXeBase> luns) {
                this.luns = luns;
    }

    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StorageResourceTypeEnum getType() {
        return type;
    }

    public void setType(StorageResourceTypeEnum type) {
        this.type = type;
    }

    public boolean getIsReplicationDestination() {
        return isReplicationDestination;
    }

    public void setIsReplicationDestination(boolean isReplicationDestination) {
        this.isReplicationDestination = isReplicationDestination;
    }

    public long getSizeTotal() {
        return sizeTotal;
    }

    public void setSizeTotal(long sizeTotal) {
        this.sizeTotal = sizeTotal;
    }

    public long getSizeUsed() {
        return sizeUsed;
    }

    public void setSizeUsed(long sizeUsed) {
        this.sizeUsed = sizeUsed;
    }

    public long getSizeAllocated() {
        return sizeAllocated;
    }

    public void setSizeAllocated(long sizeAllocated) {
        this.sizeAllocated = sizeAllocated;
    }

    public Integer getThinStatus() {
        return thinStatus;
    }

    public void setThinStatus(Integer thinStatus) {
        this.thinStatus = thinStatus;
    }

    public VNXeBase getSnapSchedule() {
        return snapSchedule;
    }

    public void setSnapSchedule(VNXeBase snapSchedule) {
        this.snapSchedule = snapSchedule;
    }

    public boolean getIsSnapSchedulePaused() {
        return isSnapSchedulePaused;
    }

    public void setIsSnapSchedulePaused(boolean isSnapSchedulePaused) {
        this.isSnapSchedulePaused = isSnapSchedulePaused;
    }

    public TieringPolicyEnum getRelocationPolicy() {
        return relocationPolicy;
    }

    public void setRelocationPolicy(TieringPolicyEnum relocationPolicy) {
        this.relocationPolicy = relocationPolicy;
    }

    public List<Long> getPerTierSizeused() {
        return perTierSizeused;
    }

    public void setPerTierSizeused(List<Long> perTierSizeused) {
        this.perTierSizeused = perTierSizeused;
    }

    public long getMetadataSize() {
        return metadataSize;
    }

    public void setMetadataSize(long metadataSize) {
        this.metadataSize = metadataSize;
    }

    public long getMetadataSizeAllocated() {
        return metadataSizeAllocated;
    }

    public void setMetadataSizeAllocated(long metadataSizeAllocated) {
        this.metadataSizeAllocated = metadataSizeAllocated;
    }

    public long getSnapSizeTotal() {
        return snapSizeTotal;
    }

    public void setSnapSizeTotal(long snapSizeTotal) {
        this.snapSizeTotal = snapSizeTotal;
    }

    public long getSnapSizeAllocated() {
        return snapSizeAllocated;
    }

    public void setSnapSizeAllocated(long snapSizeAllocated) {
        this.snapSizeAllocated = snapSizeAllocated;
    }

    public int getSnapCount() {
        return snapCount;
    }

    public void setSnapCount(int snapCount) {
        this.snapCount = snapCount;
    }

    public static enum StorageResourceTypeEnum {
        UNKNOWN,
        SHAREDFOLDER,
        GENERICSTORAGE,
        VMWAREFS,
        VMWAREISCSI,
        MICROSOFT_HYPERV,
        MICROSFOT_EXCHANGE2007,
        MICROSOFT_EXCHANGE2010,
        STANDALONE;
    }

    public static enum TieringPolicyEnum {
        AUTOTIER_HIGH(0),
        AUTOTIER(1),
        HIGHEST(2),
        LOWEST(3),
        NO_DATA_MOVEMENT(4),
        MIXED(0xffff);

        private int value;

        private TieringPolicyEnum(int value) {
            this.value = value;
        }

        @org.codehaus.jackson.annotate.JsonValue
        public int getValue() {
            return this.value;
        }

        public static String[] getTieringPolicyNames() {
            String[] policies = { AUTOTIER_HIGH.name(),
                    AUTOTIER.name(),
                    HIGHEST.name(),
                    LOWEST.name() };
            return policies;

        }
    }
}
