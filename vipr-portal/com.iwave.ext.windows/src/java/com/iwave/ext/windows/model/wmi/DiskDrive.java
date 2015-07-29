/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class DiskDrive {
    private int number;
    private String name;
    private String caption;
    private String deviceId;
    private String pnpDeviceId;
    private String interfaceType;
    private Integer scsiBus;
    private Integer scsiPort;
    private Integer scsiTarget;
    private Integer scsiLun;
    private String serialNumber;
    private long size;
    private String status;
    private String signature;

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPnpDeviceId() {
        return pnpDeviceId;
    }

    public void setPnpDeviceId(String pnpDeviceId) {
        this.pnpDeviceId = pnpDeviceId;
    }

    public String getInterfaceType() {
        return interfaceType;
    }

    public void setInterfaceType(String interfaceType) {
        this.interfaceType = interfaceType;
    }

    public Integer getScsiBus() {
        return scsiBus;
    }

    public void setScsiBus(Integer scsiBus) {
        this.scsiBus = scsiBus;
    }

    public Integer getScsiPort() {
        return scsiPort;
    }

    public void setScsiPort(Integer scsiPort) {
        this.scsiPort = scsiPort;
    }

    public Integer getScsiTarget() {
        return scsiTarget;
    }

    public void setScsiTarget(Integer scsiTarget) {
        this.scsiTarget = scsiTarget;
    }

    public Integer getScsiLun() {
        return scsiLun;
    }

    public void setScsiLun(Integer scsiLun) {
        this.scsiLun = scsiLun;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("number", number);
        builder.append("name", name);
        builder.append("caption", caption);
        builder.append("deviceId", deviceId);
        builder.append("pnpDeviceId", pnpDeviceId);
        builder.append("interfaceType", interfaceType);
        builder.append("scsiBus", scsiBus);
        builder.append("scsiPort", scsiPort);
        builder.append("scsiTarget", scsiTarget);
        builder.append("scsiLun", scsiLun);
        builder.append("serialNumber", serialNumber);
        builder.append("size", size);
        builder.append("status", status);
        builder.append("signature", signature);
        return builder.toString();
    }
}
