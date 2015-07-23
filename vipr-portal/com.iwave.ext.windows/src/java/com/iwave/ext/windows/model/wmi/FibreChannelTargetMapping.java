/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class FibreChannelTargetMapping implements Serializable {
    private static final long serialVersionUID = -8965673010752960715L;

    private String nodeWWN;
    private String portWWN;
    private Integer fcpLun;
    private Integer scsiBus;
    private Integer scsiTarget;
    private Integer scsiLun;

    public String getNodeWWN() {
        return nodeWWN;
    }

    public void setNodeWWN(String nodeWWN) {
        this.nodeWWN = nodeWWN;
    }

    public String getPortWWN() {
        return portWWN;
    }

    public void setPortWWN(String portWWN) {
        this.portWWN = portWWN;
    }

    public Integer getFcpLun() {
        return fcpLun;
    }

    public void setFcpLun(Integer fcpLun) {
        this.fcpLun = fcpLun;
    }

    public Integer getScsiBus() {
        return scsiBus;
    }

    public void setScsiBus(Integer scsiBus) {
        this.scsiBus = scsiBus;
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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FibreChannelTargetMapping) {
            return equalsFibreChannelTargetMapping((FibreChannelTargetMapping) obj);
        }
        return false;
    }

    public boolean equalsFibreChannelTargetMapping(FibreChannelTargetMapping mapping) {
        if (mapping == this) {
            return true;
        }
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(nodeWWN, mapping.nodeWWN);
        builder.append(portWWN, mapping.portWWN);
        builder.append(fcpLun, mapping.fcpLun);
        builder.append(scsiBus, mapping.scsiBus);
        builder.append(scsiTarget, mapping.scsiTarget);
        builder.append(scsiLun, mapping.scsiLun);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(nodeWWN);
        builder.append(portWWN);
        builder.append(fcpLun);
        builder.append(scsiBus);
        builder.append(scsiTarget);
        builder.append(scsiLun);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("nodeWWN", nodeWWN);
        builder.append("portWWN", portWWN);
        builder.append("fcpLun", fcpLun);
        builder.append("scsiBus", scsiBus);
        builder.append("scsiTarget", scsiTarget);
        builder.append("scsiLun", scsiLun);
        return builder.toString();
    }
}
