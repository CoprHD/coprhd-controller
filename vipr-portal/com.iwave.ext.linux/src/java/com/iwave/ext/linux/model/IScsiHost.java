/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class IScsiHost implements Serializable {
    private static final long serialVersionUID = -5493553042101233980L;
    private int hostId;
    private String state;
    private String transport;
    private String initiatorName;
    private String ipAddress;
    private String hwAddress;
    private String netdev;
    private List<IScsiSession> sessions;

    public int getHostId() {
        return hostId;
    }

    public void setHostId(int hostId) {
        this.hostId = hostId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getHwAddress() {
        return hwAddress;
    }

    public void setHwAddress(String hwAddress) {
        this.hwAddress = hwAddress;
    }

    public String getNetdev() {
        return netdev;
    }

    public void setNetdev(String netdev) {
        this.netdev = netdev;
    }

    public List<IScsiSession> getSessions() {
        return sessions;
    }

    public void setSessions(List<IScsiSession> sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof IScsiHost)) {
            return false;
        }
        IScsiHost host = (IScsiHost) obj;
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(hostId, host.hostId);
        builder.append(state, host.state);
        builder.append(transport, host.transport);
        builder.append(initiatorName, host.initiatorName);
        builder.append(ipAddress, host.ipAddress);
        builder.append(hwAddress, host.hwAddress);
        builder.append(netdev, host.netdev);
        builder.append(sessions, host.sessions);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(hostId);
        builder.append(state);
        builder.append(transport);
        builder.append(initiatorName);
        builder.append(ipAddress);
        builder.append(hwAddress);
        builder.append(netdev);
        builder.append(sessions);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return toString(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public String toString(ToStringStyle style) {
        ToStringBuilder builder = new ToStringBuilder(this, style);
        builder.append("hostId", hostId);
        builder.append("state", state);
        builder.append("transport", transport);
        builder.append("initiatorName", initiatorName);
        builder.append("ipAddress", ipAddress);
        builder.append("hwAddress", hwAddress);
        builder.append("netdev", netdev);
        builder.append("sessions", sessions);
        return builder.toString();
    }
}
