/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import java.util.List;

import com.emc.storageos.hds.xmlgen.XMLConstants;

public class Pool {

    private String objectID;
    private String displayName;
    private String poolID;
    private String raidType;
    private String type;
    private String diskType;
    private Long usedCapacity;
    private Long allocatedCapacity;
    private Long freeCapacity;
    private Long diskSizeInKB;
    private Long largestFreeSpace;
    private String controllerID;
    private Integer poolFunction;
    private Long subscribedCapacityInKB;
    private Integer tierControl;
    private String name;

    private List<StoragePoolTier> tiers;

    private List<LogicalUnit> virtualLuList;

    public Pool() {
    }

    public Pool(String objectID) {
        this.objectID = objectID;
    }

    /**
     * @return the objectID
     */
    public String getObjectID() {
        return objectID;
    }

    /**
     * @param objectID the objectID to set
     */
    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the number
     */
    public String getPoolID() {
        return poolID;
    }

    /**
     * @param number the number to set
     */
    public void setPoolID(String poolID) {
        this.poolID = poolID;
    }

    /**
     * @return the raidType
     */
    public String getRaidType() {
        return raidType;
    }

    /**
     * @param raidType the raidType to set
     */
    public void setRaidType(String raidType) {
        this.raidType = raidType;
    }

    /**
     * @return the diskType
     */
    public String getDiskType() {
        return diskType;
    }

    /**
     * @param diskType the diskType to set
     */
    public void setDiskType(String diskType) {
        this.diskType = diskType;
    }

    /**
     * @return the totalCapacity
     */
    public Long getUsedCapacity() {
        return usedCapacity;
    }

    /**
     * @param totalCapacity the totalCapacity to set
     */
    public void setUsedCapacity(Long usedCapacity) {
        this.usedCapacity = usedCapacity;
    }

    /**
     * @return the allocatedCapacity
     */
    public Long getAllocatedCapacity() {
        return allocatedCapacity;
    }

    /**
     * @param allocatedCapacity the allocatedCapacity to set
     */
    public void setAllocatedCapacity(Long allocatedCapacity) {
        this.allocatedCapacity = allocatedCapacity;
    }

    /**
     * @return the freeCapacity
     */
    public Long getFreeCapacity() {
        return freeCapacity;
    }

    /**
     * @param freeCapacity the freeCapacity to set
     */
    public void setFreeCapacity(Long freeCapacity) {
        this.freeCapacity = freeCapacity;
    }

    /**
     * @return the diskSizeInKB
     */
    public Long getDiskSizeInKB() {
        return diskSizeInKB;
    }

    /**
     * @param diskSizeInKB the diskSizeInKB to set
     */
    public void setDiskSizeInKB(Long diskSizeInKB) {
        this.diskSizeInKB = diskSizeInKB;
    }

    /**
     * @return the largestFreeSpace
     */
    public Long getLargestFreeSpace() {
        return largestFreeSpace;
    }

    /**
     * @param largestFreeSpace the largestFreeSpace to set
     */
    public void setLargestFreeSpace(Long largestFreeSpace) {
        this.largestFreeSpace = largestFreeSpace;
    }

    /**
     * @return the controllerID
     */
    public String getControllerID() {
        return controllerID;
    }

    /**
     * @param controllerID the controllerID to set
     */
    public void setControllerID(String controllerID) {
        this.controllerID = controllerID;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 
     * @return poolFunction of the StoragePool
     */
    public Integer getPoolFunction() {
        return poolFunction;
    }

    /**
     * Sets poolFunction of the StoragePool
     * 
     * @param poolFunction
     */
    public void setPoolFunction(Integer poolFunction) {
        this.poolFunction = poolFunction;
    }

    /**
     * 
     * @return subscribedCapacityInKB of the thin pool
     */
    public Long getSubscribedCapacityInKB() {
        return subscribedCapacityInKB;
    }

    /**
     * Sets thin pool's subscribed capacity in KB
     * 
     * @param subscribedCapacityInKB
     */
    public void setSubscribedCapacityInKB(Long subscribedCapacityInKB) {
        this.subscribedCapacityInKB = subscribedCapacityInKB;
    }

    public List<StoragePoolTier> getTiers() {
        return tiers;
    }

    public void setTiers(List<StoragePoolTier> tiers) {
        this.tiers = tiers;
    }

    public List<LogicalUnit> getVirtualLuList() {
        return virtualLuList;
    }

    public void setVirtualLuList(List<LogicalUnit> virtualLuList) {
        this.virtualLuList = virtualLuList;
    }

    /**
     * get tierControl
     * 
     * @return
     */
    public Integer getTierControl() {
        return tierControl;
    }

    /**
     * Set tierControl
     * 
     * @param tierControl
     */
    public void setTierControl(Integer tierControl) {
        this.tierControl = tierControl;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.objectID) {
            xmlString.append("objectID=").append(XMLConstants.QUOTATION)
                    .append(this.objectID).append(XMLConstants.QUOTATION);
        }
        return xmlString.toString();
    }

}
