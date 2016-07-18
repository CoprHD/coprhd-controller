/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeEthernetPort extends VNXeBase {
    private List<Integer> operationalStatus;
    private Health health;
    private VNXeBase storageProcessorId;
    private VNXeBase storageProcessor;
    private boolean needsReplacement;
    private String name;
    private int portNumber;
    // speed is in Mbps
    private int speed;
    private int mtu;
    private boolean bond;
    private String macAddresss;
    private boolean isRSSCapable;
    private boolean isRDMAcapable;
    private VNXeBase ioModuleId;
    private int requestedSpeed;
    private List<Integer> supportedSpeeds;
    private int requestedMtu;
    private List<Integer> supportedMtus;

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


    public boolean getIsNeedsReplacement() {
        return needsReplacement;
    }

    public void setIsNeedsReplacement(boolean needsReplacement) {
        this.needsReplacement = needsReplacement;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public boolean getIsBond() {
        return bond;
    }

    public void setIsBond(boolean bond) {
        this.bond = bond;
    }

    public String getMacAddresss() {
        return macAddresss;
    }

    public void setMacAddresss(String macAddresss) {
        this.macAddresss = macAddresss;
    }

    public boolean getIsRSSCapable() {
        return isRSSCapable;
    }

    public void setIsRSSCapable(boolean isRSSCapable) {
        this.isRSSCapable = isRSSCapable;
    }

    public boolean getIsRDMAcapable() {
        return isRDMAcapable;
    }

    public void setIsRDMAcapable(boolean isRDMAcapable) {
        this.isRDMAcapable = isRDMAcapable;
    }

    public VNXeBase getIoModuleId() {
        return ioModuleId;
    }

    public void setIoModuleId(VNXeBase ioModuleId) {
        this.ioModuleId = ioModuleId;
    }

    public int getRequestedSpeed() {
        return requestedSpeed;
    }

    public void setRequestedSpeed(int requestedSpeed) {
        this.requestedSpeed = requestedSpeed;
    }

    public List<Integer> getSupportedSpeeds() {
        return supportedSpeeds;
    }

    public void setSupportedSpeeds(List<Integer> supportedSpeeds) {
        this.supportedSpeeds = supportedSpeeds;
    }

    public int getRequestedMtu() {
        return requestedMtu;
    }

    public void setRequestedMtu(int requestedMtu) {
        this.requestedMtu = requestedMtu;
    }

    public List<Integer> getSupportedMtus() {
        return supportedMtus;
    }

    public void setSupportedMtus(List<Integer> supportedMtus) {
        this.supportedMtus = supportedMtus;
    }

    public static enum EthernetPortStatusEnum {
        UNKNOWN(0),
        OK(2),
        DEGRADED(3),
        UNINITIALIZED(0x8000),
        EMPTY(0x8001),
        MISSING(0x8002),
        FAULTED(0x8003),
        UNAVAILABLE(0x8004),
        DISABLED(0x8005),
        LINK_UP(0x8010),
        LINK_DOWN(0x8011);

        private int value;

        private EthernetPortStatusEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public static EthernetPortStatusEnum getEnum(int state) {
            for (EthernetPortStatusEnum enumValue : EthernetPortStatusEnum.values()) {
                if (enumValue.getValue() == state) {
                    return enumValue;
                }
            }

            return UNKNOWN;
        }
    }
}
