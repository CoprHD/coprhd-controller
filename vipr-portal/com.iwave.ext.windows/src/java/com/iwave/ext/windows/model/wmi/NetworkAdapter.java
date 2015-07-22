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

public class NetworkAdapter implements Serializable {
    private static final long serialVersionUID = -302462661618514059L;

    private Integer name;
    private String ipAddress;
    private String ip6Address;
    private String subnetMask;
    private String macAddress;

    public Integer getName() {
        return name;
    }

    public void setName(Integer name) {
        this.name = name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getIp6Address() {
        return ip6Address;
    }

    public void setIp6Address(String ip6Address) {
        this.ip6Address = ip6Address;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NetworkAdapter) {
            return equalsNetworkAdapter((NetworkAdapter) obj);
        }
        return false;
    }

    public boolean equalsNetworkAdapter(NetworkAdapter adapter) {
        if (adapter == this) {
            return true;
        }
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(name, adapter.name);
        builder.append(ipAddress, adapter.ipAddress);
        builder.append(ip6Address, adapter.ip6Address);
        builder.append(subnetMask, adapter.subnetMask);
        builder.append(macAddress, adapter.macAddress);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(name);
        builder.append(ipAddress);
        builder.append(ip6Address);
        builder.append(subnetMask);
        builder.append(macAddress);
        return builder.toHashCode();
    }

    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("name", name);
        builder.append("ipAddress", ipAddress);
        builder.append("ip6Address", ip6Address);
        builder.append("subnetMask", subnetMask);
        builder.append("macAddress", macAddress);
        return builder.toString();
    }
}
