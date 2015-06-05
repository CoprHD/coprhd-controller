/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class FibreChannelHBA implements Serializable {
    private static final long serialVersionUID = 283054093552549435L;

    private String nodeWWN;
    private String portWWN;
    private String instanceName;

    public FibreChannelHBA() {
    }

    public FibreChannelHBA(String nodeWWN, String portWWN) {
        this.nodeWWN = nodeWWN;
        this.portWWN = portWWN;
    }

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

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FibreChannelHBA) {
            return equals((FibreChannelHBA) obj);
        }
        return false;
    }

    public boolean equals(FibreChannelHBA hba) {
        if (hba == this) {
            return true;
        }
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(nodeWWN, hba.nodeWWN);
        builder.append(portWWN, hba.portWWN);
        builder.append(instanceName, hba.instanceName);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(nodeWWN);
        builder.append(portWWN);
        builder.append(instanceName);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("nodeWWN", nodeWWN);
        builder.append("portWWN", portWWN);
        builder.append("instanceName", instanceName);
        return builder.toString();
    }
}
