/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

import java.util.List;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class IScsiSession {
    private String targetAddress;
    private int targetPort;
    private String initiatorName;
    private String sessionId;
    private String targetName;
    private List<IScsiDevice> devices;

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public List<IScsiDevice> getDevices() {
        return devices;
    }

    public void setDevices(List<IScsiDevice> devices) {
        this.devices = devices;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("targetAddress", targetAddress);
        builder.append("targetPort", targetPort);
        builder.append("initiatorName", initiatorName);
        builder.append("sessionId", sessionId);
        builder.append("targetName", targetName);
        builder.append("devices", devices);
        return builder.toString();
    }
}
