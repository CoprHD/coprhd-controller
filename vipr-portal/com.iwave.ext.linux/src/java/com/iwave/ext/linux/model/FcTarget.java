/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * FibreChannel target. This is the target information used to determine which LUNs are from which
 * storage arrays.
 * 
 * @author jonnymiller
 */
public class FcTarget implements Serializable {
    private static final long serialVersionUID = -4125774620268681437L;
    private int scsiHost;
    private int scsiChannel;
    private int scsiId;
    private String nodeName;
    private String portName;

    public int getScsiHost() {
        return scsiHost;
    }

    public void setScsiHost(int host) {
        this.scsiHost = host;
    }

    public int getScsiChannel() {
        return scsiChannel;
    }

    public void setScsiChannel(int channel) {
        this.scsiChannel = channel;
    }

    public int getScsiId() {
        return scsiId;
    }

    public void setScsiId(int id) {
        this.scsiId = id;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof FcTarget)) {
            return false;
        }
        FcTarget target = (FcTarget) obj;
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(scsiHost, target.scsiHost);
        builder.append(scsiChannel, target.scsiChannel);
        builder.append(scsiId, target.scsiId);
        builder.append(nodeName, target.nodeName);
        builder.append(portName, target.portName);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(scsiHost);
        builder.append(scsiChannel);
        builder.append(scsiId);
        builder.append(nodeName);
        builder.append(portName);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return toString(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public String toString(ToStringStyle style) {
        ToStringBuilder builder = new ToStringBuilder(this, style);
        builder.append("scsiHost", scsiHost);
        builder.append("scsiChannel", scsiChannel);
        builder.append("scsiId", scsiId);
        builder.append("nodeName", nodeName);
        builder.append("portName", portName);
        return builder.toString();
    }
}
