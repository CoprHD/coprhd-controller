/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class IScsiDevice {
    private String deviceInterfaceGuid;
    private String deviceInterfaceName;
    private int deviceNumber;
    private String initiatorName;
    private int partitionNumber;
    private int scsiBus;
    private int scsiTarget;
    private int scsiPort;
    private int scsiLun;
    private String targetName;

    public String getDeviceInterfaceGuid() {
        return deviceInterfaceGuid;
    }

    public void setDeviceInterfaceGuid(String deviceInterfaceGuid) {
        this.deviceInterfaceGuid = deviceInterfaceGuid;
    }

    public String getDeviceInterfaceName() {
        return deviceInterfaceName;
    }

    public void setDeviceInterfaceName(String deviceInterfaceName) {
        this.deviceInterfaceName = deviceInterfaceName;
    }

    public int getDeviceNumber() {
        return deviceNumber;
    }

    public void setDeviceNumber(int deviceNumber) {
        this.deviceNumber = deviceNumber;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public int getPartitionNumber() {
        return partitionNumber;
    }

    public void setPartitionNumber(int partitionNumber) {
        this.partitionNumber = partitionNumber;
    }

    public int getScsiBus() {
        return scsiBus;
    }

    public void setScsiBus(int scsiBus) {
        this.scsiBus = scsiBus;
    }

    public int getScsiTarget() {
        return scsiTarget;
    }

    public void setScsiTarget(int scsiTarget) {
        this.scsiTarget = scsiTarget;
    }

    public int getScsiPort() {
        return scsiPort;
    }

    public void setScsiPort(int scsiPort) {
        this.scsiPort = scsiPort;
    }

    public int getScsiLun() {
        return scsiLun;
    }

    public void setScsiLun(int scsiLun) {
        this.scsiLun = scsiLun;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("deviceInterfaceGuid", deviceInterfaceGuid);
        builder.append("deviceInterfaceName", deviceInterfaceName);
        builder.append("deviceNumber", deviceNumber);
        builder.append("initiatorName", initiatorName);
        builder.append("partitionNumber", partitionNumber);
        builder.append("scsiBus", scsiBus);
        builder.append("scsiTarget", scsiTarget);
        builder.append("scsiPort", scsiPort);
        builder.append("scsiLun", scsiLun);
        builder.append("targetName", targetName);
        return builder.toString();
    }
}
