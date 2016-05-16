/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Disk {
    private String id;
    private String name;
    private String emcPartNumber;
    private int tierType;
    private int diskTechnology;
    private long size;
    private long rawSize;
    private int rpm;
    private VNXePool pool;

    public static enum DiskTechnologyEnum {
        SAS(1),
        NL_SAS(2),
        SAS_FLASH(5),
        SAS_FLASH_VP(6);

        private static final Map<Integer, DiskTechnologyEnum> diskTechnologyMap = new HashMap<Integer, DiskTechnologyEnum>();
        static {
            for (DiskTechnologyEnum type : DiskTechnologyEnum.values()) {
                diskTechnologyMap.put(type.value, type);
            }
        }

        private int value;

        private DiskTechnologyEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public static DiskTechnologyEnum getEnumValue(Integer inValue) {
            return diskTechnologyMap.get(inValue);
        }

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmcPartNumber() {
        return emcPartNumber;
    }

    public void setEmcPartNumber(String emcPartNumber) {
        this.emcPartNumber = emcPartNumber;
    }

    public int getTierType() {
        return tierType;
    }

    public void setTierType(int tierType) {
        this.tierType = tierType;
    }

    public int getDiskTechnology() {
        return diskTechnology;
    }

    public void setDiskTechnology(int diskTechnology) {
        this.diskTechnology = diskTechnology;
    }

    public DiskTechnologyEnum getDiskTechnologyEnum() {
        return DiskTechnologyEnum.getEnumValue(diskTechnology);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getRawSize() {
        return rawSize;
    }

    public void setRawSize(long rawSize) {
        this.rawSize = rawSize;
    }

    public int getRpm() {
        return rpm;
    }

    public void setRpm(int rpm) {
        this.rpm = rpm;
    }

	 public VNXePool getPool() {
        return pool;
    }

    public void setPool(VNXePool pool) {
        this.pool = pool;
    }


}
