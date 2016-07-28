/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeFCPort extends VNXeBase {
    private List<Integer> operationalStatus;
    private Health health;
    private VNXeBase storageProcessorId;
    // VNX Unity API returns storageProcessor
    private VNXeBase storageProcessor;
    private String wwn;
    private Integer slotNumber;
    private Integer currentSpeed;
    private Integer requestedSpeed;
    private Boolean needsReplacement;
    private Integer nPortId;
    private String name;
    private String portWwn;

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

    public VNXeBase getStorageProcessorId() {
        return storageProcessorId;
    }

    public void setStorageProcessorId(VNXeBase storageProcessorId) {
        this.storageProcessorId = storageProcessorId;
    }
    
    public VNXeBase getStorageProcessor() {
        return storageProcessor;
    }

    public void setStorageProcessor(VNXeBase storageProcessor) {
        this.storageProcessor = storageProcessor;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public Integer getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Integer slotNumber) {
        this.slotNumber = slotNumber;
    }

    public Integer getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(Integer currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    public Integer getRequestedSpeed() {
        return requestedSpeed;
    }

    public void setRequestedSpeed(Integer requestedSpeed) {
        this.requestedSpeed = requestedSpeed;
    }

    public Boolean getNeedsReplacement() {
        return needsReplacement;
    }

    public void setNeedsReplacement(Boolean needsReplacement) {
        this.needsReplacement = needsReplacement;
    }

    public Integer getnPortId() {
        return nPortId;
    }

    public void setnPortId(Integer nPortId) {
        this.nPortId = nPortId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPortWwn() {
        if (wwn != null && !wwn.isEmpty() &&
                (portWwn == null || portWwn.isEmpty())) {
            // wwn format is node:port, for example 50:06:01:60:88:E0:03:5D:50:06:01:6C:08:E0:03:5D
            portWwn = wwn.substring(24);

        }
        return portWwn;
    }

    public void setPortWwn(String portWwn) {
        this.portWwn = portWwn;
    }

    public static enum FcSpeedEnum {
        Auto(0),
        OneGbps(1),
        TwoGbps(2),
        FourGbps(4),
        EightGbps(8),
        SixteenGbps(16),
        ThirtyTwoGbps(32);

        private int value;

        private FcSpeedEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public static String getFcSpeedString(int value) {
            if (value == FcSpeedEnum.Auto.getValue()) {
                return Auto.name();
            } else if (value == FcSpeedEnum.OneGbps.getValue()) {
                return OneGbps.name();
            } else if (value == FcSpeedEnum.TwoGbps.getValue()) {
                return TwoGbps.name();
            } else if (value == FcSpeedEnum.FourGbps.getValue()) {
                return FourGbps.name();
            } else if (value == FcSpeedEnum.EightGbps.getValue()) {
                return EightGbps.name();
            } else if (value == FcSpeedEnum.SixteenGbps.getValue()) {
                return SixteenGbps.name();
            } else if (value == FcSpeedEnum.ThirtyTwoGbps.getValue()) {
                return ThirtyTwoGbps.name();
            } else {
                return null;
            }
        }

    }

}
