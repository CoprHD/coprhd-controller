/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeLun extends VNXeBase {
    private Integer lunNumber;
    private List<Integer> operationalStatus;
    private Health health;
    private String name;
    private String description;
    private Integer type;
    private Long sizeTotal;
    private Long sizeUsed;
    private Long sizeAllocated;
    private List<Long> perTierSizeUsed;
    private Boolean isThinEnabled;
    private VNXeBase storageResource;
    private VNXeBase pool;
    private String wwn;
    private TieringPolicyEnum tieringPolicy;
    private Integer defaultNode;
    private Boolean isReplicationDestination;
    private Integer currentNode;
    private VNXeBase snapSchedule;
    private Boolean isSnapSchedulePaused;
    private Integer healthValue;
    private String healthDescription;
    private Boolean isFASTCacheEnabled;
    private Boolean vfcCachedOnHost;
    private Integer vfcCacheCandidateRating;
    private Long metadataSize;
    private Long metadataSizeAllocated;
    private String snapWwn;
    private Long snapsSize;
    private Long snapsSizeAllocated;
    private List<BlockHostAccess> hostAccess;
    private Integer snapCount;

    public Integer getLunNumber() {
        return lunNumber;
    }

    public void setLunNumber(Integer lunNumber) {
        this.lunNumber = lunNumber;
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

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getSizeTotal() {
        return sizeTotal;
    }

    public void setSizeTotal(Long sizeTotal) {
        this.sizeTotal = sizeTotal;
    }

    public Long getSizeUsed() {
        return sizeUsed;
    }

    public void setSizeUsed(Long sizeUsed) {
        this.sizeUsed = sizeUsed;
    }

    public Long getSizeAllocated() {
        return sizeAllocated;
    }

    public void setSizeAllocated(Long sizeAllocated) {
        this.sizeAllocated = sizeAllocated;
    }

    public List<Long> getPerTierSizeUsed() {
        return perTierSizeUsed;
    }

    public void setPerTierSizeUsed(List<Long> perTierSizeUsed) {
        this.perTierSizeUsed = perTierSizeUsed;
    }

    public Boolean getIsThinEnabled() {
        return isThinEnabled;
    }

    public void setIsThinEnabled(Boolean isThinEnabled) {
        this.isThinEnabled = isThinEnabled;
    }

    public VNXeBase getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(VNXeBase storageResource) {
        this.storageResource = storageResource;
    }

    public VNXeBase getPool() {
        return pool;
    }

    public void setPool(VNXeBase pool) {
        this.pool = pool;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public TieringPolicyEnum getTieringPolicy() {
        return tieringPolicy;
    }

    public void setTieringPolicy(TieringPolicyEnum tieringPolicy) {
        this.tieringPolicy = tieringPolicy;
    }

    public Integer getDefaultNode() {
        return defaultNode;
    }

    public void setDefaultNode(Integer defaultNode) {
        this.defaultNode = defaultNode;
    }

    public Boolean getIsReplicationDestination() {
        return isReplicationDestination;
    }

    public void setIsReplicationDestination(Boolean isReplicationDestination) {
        this.isReplicationDestination = isReplicationDestination;
    }

    public Integer getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(Integer currentNode) {
        this.currentNode = currentNode;
    }

    public VNXeBase getSnapSchedule() {
        return snapSchedule;
    }

    public void setSnapSchedule(VNXeBase snapSchedule) {
        this.snapSchedule = snapSchedule;
    }

    public Boolean getIsSnapSchedulePaused() {
        return isSnapSchedulePaused;
    }

    public void setIsSnapSchedulePaused(Boolean isSnapSchedulePaused) {
        this.isSnapSchedulePaused = isSnapSchedulePaused;
    }

    public Integer getHealthValue() {
        return healthValue;
    }

    public void setHealthValue(Integer healthValue) {
        this.healthValue = healthValue;
    }

    public String getHealthDescription() {
        return healthDescription;
    }

    public void setHealthDescription(String healthDescription) {
        this.healthDescription = healthDescription;
    }

    public Boolean getIsFASTCacheEnabled() {
        return isFASTCacheEnabled;
    }

    public void setIsFASTCacheEnabled(Boolean isFASTCacheEnabled) {
        this.isFASTCacheEnabled = isFASTCacheEnabled;
    }

    public Boolean getVfcCachedOnHost() {
        return vfcCachedOnHost;
    }

    public void setVfcCachedOnHost(Boolean vfcCachedOnHost) {
        this.vfcCachedOnHost = vfcCachedOnHost;
    }

    public Integer getVfcCacheCandidateRating() {
        return vfcCacheCandidateRating;
    }

    public void setVfcCacheCandidateRating(Integer vfcCacheCandidateRating) {
        this.vfcCacheCandidateRating = vfcCacheCandidateRating;
    }

    public Long getMetadataSize() {
        return metadataSize;
    }

    public void setMetadataSize(Long metadataSize) {
        this.metadataSize = metadataSize;
    }

    public Long getMetadataSizeAllocated() {
        return metadataSizeAllocated;
    }

    public void setMetadataSizeAllocated(Long metadataSizeAllocated) {
        this.metadataSizeAllocated = metadataSizeAllocated;
    }

    public String getSnapWwn() {
        return snapWwn;
    }

    public void setSnapWwn(String snapWwn) {
        this.snapWwn = snapWwn;
    }

    public Long getSnapsSize() {
        return snapsSize;
    }

    public void setSnapsSize(Long snapsSize) {
        this.snapsSize = snapsSize;
    }

    public Long getSnapsSizeAllocated() {
        return snapsSizeAllocated;
    }

    public void setSnapsSizeAllocated(Long snapsSizeAllocated) {
        this.snapsSizeAllocated = snapsSizeAllocated;
    }

    public List<BlockHostAccess> getHostAccess() {
        return hostAccess;
    }

    public void setHostAccess(List<BlockHostAccess> hostAccess) {
        this.hostAccess = hostAccess;
    }

    public Integer getSnapCount() {
        return snapCount;
    }

    public void setSnapCount(Integer snapCount) {
        this.snapCount = snapCount;
    }

    public static enum TieringPolicyEnum {
        AUTOTIER_HIGH,
        AUTOTIER,
        HIGHEST,
        LOWEST,
        NO_DATA_MOVEMENT;
    }

    public static enum LUNTypeEnum {
        GenericStorage(1),
        Standalone(2),
        VmWareISCSI(3);

        private int value;

        private LUNTypeEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }
}
