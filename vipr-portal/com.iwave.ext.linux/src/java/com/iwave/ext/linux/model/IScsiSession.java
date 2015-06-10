/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class IScsiSession implements Serializable {
    private static final long serialVersionUID = 18313363105456067L;
    private IScsiTarget target;
    private String persistentPortal;
    private String ifaceTransport;
    private String ifaceInitiatorName;
    private String ifaceIPAddress;
    private String ifaceHWAddress;
    private String ifaceNetdev;
    private String sessionID;
    private String connectionState;
    private String sessionState;

    public IScsiTarget getTarget() {
        return target;
    }

    public void setTarget(IScsiTarget target) {
        this.target = target;
    }

    public String getPersistentPortal() {
        return persistentPortal;
    }

    public void setPersistentPortal(String persistentPortal) {
        this.persistentPortal = persistentPortal;
    }

    public String getIfaceTransport() {
        return ifaceTransport;
    }

    public void setIfaceTransport(String ifaceTransport) {
        this.ifaceTransport = ifaceTransport;
    }

    public String getIfaceInitiatorName() {
        return ifaceInitiatorName;
    }

    public void setIfaceInitiatorName(String ifaceInitiatorName) {
        this.ifaceInitiatorName = ifaceInitiatorName;
    }

    public String getIfaceIPAddress() {
        return ifaceIPAddress;
    }

    public void setIfaceIPAddress(String ifaceIPAddress) {
        this.ifaceIPAddress = ifaceIPAddress;
    }

    public String getIfaceHWAddress() {
        return ifaceHWAddress;
    }

    public void setIfaceHWAddress(String ifaceHWAddress) {
        this.ifaceHWAddress = ifaceHWAddress;
    }

    public String getIfaceNetdev() {
        return ifaceNetdev;
    }

    public void setIfaceNetdev(String ifaceNetdev) {
        this.ifaceNetdev = ifaceNetdev;
    }

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

    public String getConnectionState() {
        return connectionState;
    }

    public void setConnectionState(String connectionState) {
        this.connectionState = connectionState;
    }

    public String getSessionState() {
        return sessionState;
    }

    public void setSessionState(String sessionState) {
        this.sessionState = sessionState;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return false;
        }
        if (!(obj instanceof IScsiSession)) {
            return false;
        }
        IScsiSession session = (IScsiSession) obj;
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(target, session.target);
        builder.append(persistentPortal, session.persistentPortal);
        builder.append(ifaceTransport, session.ifaceTransport);
        builder.append(ifaceInitiatorName, session.ifaceInitiatorName);
        builder.append(ifaceIPAddress, session.ifaceIPAddress);
        builder.append(ifaceHWAddress, session.ifaceHWAddress);
        builder.append(ifaceNetdev, session.ifaceNetdev);
        builder.append(sessionID, session.sessionID);
        builder.append(connectionState, session.connectionState);
        builder.append(sessionState, session.sessionState);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(target);
        builder.append(persistentPortal);
        builder.append(ifaceTransport);
        builder.append(ifaceInitiatorName);
        builder.append(ifaceIPAddress);
        builder.append(ifaceHWAddress);
        builder.append(ifaceNetdev);
        builder.append(sessionID);
        builder.append(connectionState);
        builder.append(sessionState);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return toString(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public String toString(ToStringStyle style) {
        ToStringBuilder builder = new ToStringBuilder(this, style);
        builder.append("target", target);
        builder.append("persistentPortal", persistentPortal);
        builder.append("ifaceTransport", ifaceTransport);
        builder.append("ifaceInitiatorName", ifaceInitiatorName);
        builder.append("ifaceIPAddress", ifaceIPAddress);
        builder.append("ifaceHWAddress", ifaceHWAddress);
        builder.append("ifaceNetdev", ifaceNetdev);
        builder.append("sessionID", sessionID);
        builder.append("connectionState", connectionState);
        builder.append("sessionState", sessionState);
        return builder.toString();
    }
}
