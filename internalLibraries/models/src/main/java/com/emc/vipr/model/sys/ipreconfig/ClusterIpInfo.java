/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.ipreconfig;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.io.*;
import java.util.Map;

/**
 * Cluster IP Information
 */
@XmlRootElement(name = "cluster_ipinfo")
public class ClusterIpInfo implements Serializable {

    private ClusterIpv4Setting ipv4_setting;
    private ClusterIpv6Setting ipv6_setting;

    public ClusterIpInfo() {
    }

    public ClusterIpInfo(ClusterIpv4Setting ipv4_setting, ClusterIpv6Setting ipv6_setting) {
        this.ipv4_setting = ipv4_setting;
        this.ipv6_setting = ipv6_setting;
    }

    @XmlElement(name = "ipv4_setting")
    public ClusterIpv4Setting getIpv4Setting() {
        return ipv4_setting;
    }

    public void setIpv4Setting(ClusterIpv4Setting ipv4_setting) {
        this.ipv4_setting = ipv4_setting;
    }

    @XmlElement(name = "ipv6_setting")
    public ClusterIpv6Setting getIpv6Setting() {
        return ipv6_setting;
    }

    public void setIpv6Setting(ClusterIpv6Setting ipv6_setting) {
        this.ipv6_setting = ipv6_setting;
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        try {
            out.writeObject(this);
        } finally {
            out.close();
        }
        return bos.toByteArray();
    }

    public static ClusterIpInfo deserialize(byte[] data) throws IOException,
            ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        try {
            obj = in.readObject();
        } finally {
            in.close();
        }
        return (ClusterIpInfo) obj;
    }
    /* (non-Javadoc)
   	 * @see java.lang.Object#hashCode()
   	 */
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((ipv4_setting == null) ? 0 : ipv4_setting.hashCode());
		result = prime * result
				+ ((ipv6_setting == null) ? 0 : ipv6_setting.hashCode());
		return result;
	}

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }

        if (!ipv4_setting.equals(((ClusterIpInfo) obj).getIpv4Setting())) {
            return false;
        }
        if (!ipv6_setting.equals(((ClusterIpInfo) obj).getIpv6Setting())) {
            return false;
        }

        return true;
    }


	@Override
    public String toString() {
        StringBuffer propStrBuf = new StringBuffer();
        propStrBuf.append(ipv4_setting.toString());
        propStrBuf.append(ipv6_setting.toString());
        return propStrBuf.toString();
    }

    /*
     * Load key/value property map
     */
    public void loadFromPropertyMap(Map<String, String> propMap)
    {
        ipv4_setting = new ClusterIpv4Setting();
        ipv4_setting.loadFromPropertyMap(propMap);

        ipv6_setting = new ClusterIpv6Setting();
        ipv6_setting.loadFromPropertyMap(propMap);
    }

    public boolean isDefault() {
        if (ipv4_setting.isDefault() && ipv6_setting.isDefault()) {
            return true;
        }
        return false;
    }

    public String validate(int nodecount) {
        String errmsg = "";
        if (ipv4_setting.isDefault() && ipv6_setting.isDefault()) {
            errmsg = "Both IPv4 and IPv6 networks are not configured.";
            return errmsg;
        }

        if (!ipv4_setting.isValid()) {
            errmsg = "IPv4 adresses are not valid.";
            return errmsg;
        }

        if (!ipv6_setting.isValid()) {
            errmsg = "IPv6 adresses are not valid.";
            return errmsg;
        }

        if (ipv4_setting.isDuplicated()) {
            errmsg = "IPv4 adresses are duplicated.";
            return errmsg;
        }

        if (ipv6_setting.isDuplicated()) {
            errmsg = "IPv6 adresses are duplicated.";
            return errmsg;
        }

        if (!ipv4_setting.isOnSameNetworkIPv4()) {
            errmsg = "IPv4 adresses are not in same network.";
            return errmsg;
        }

        if (!ipv6_setting.isOnSameNetworkIPv6()) {
            errmsg = "IPv6 adresses are not in same network.";
            return errmsg;
        }

        if (ipv4_setting.getNetworkAddrs().size() != ipv6_setting.getNetworkAddrs().size()) {
            errmsg = "Nodes number does not match between IPv4 and IPv6.";
            return errmsg;
        }

        if (ipv4_setting.getNetworkAddrs().size() != nodecount) {
            errmsg = "Nodes number does not match with the cluster.";
            return errmsg;
        }

        return errmsg;
    }
}
