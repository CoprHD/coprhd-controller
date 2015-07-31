/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeStorageTier extends VNXeBase {
    private int tierType;
    private int disksTotal;
    private int disksUnused;
    private double sizeTotal;
    private double sizeFree;

    public int getTierType() {
        return tierType;
    }

    public void setTierType(int tierType) {
        this.tierType = tierType;
    }

    public int getDisksTotal() {
        return disksTotal;
    }

    public void setDisksTotal(int disksTotal) {
        this.disksTotal = disksTotal;
    }

    public int getDisksUnused() {
        return disksUnused;
    }

    public void setDisksUnused(int disksUnused) {
        this.disksUnused = disksUnused;
    }

    public double getSizeTotal() {
        return sizeTotal;
    }

    public void setSizeTotal(double sizeTotal) {
        this.sizeTotal = sizeTotal;
    }

    public double getSizeFree() {
        return sizeFree;
    }

    public void setSizeFree(double sizeFree) {
        this.sizeFree = sizeFree;
    }

    public String getName() {
        if (tierType == TierTypeEnum.Extreme_Performance.getValue()) {
            return TierTypeEnum.Extreme_Performance.name();
        } else if (tierType == TierTypeEnum.Performance.getValue()) {
            return TierTypeEnum.Performance.name();
        } else if (tierType == TierTypeEnum.Capacity.getValue()) {
            return TierTypeEnum.Capacity.name();
        } else {
            return null;
        }
    }

    public static enum TierTypeEnum {
        Extreme_Performance(10),
        Performance(20),
        Capacity(30);

        private final int value;

        private TierTypeEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

}
