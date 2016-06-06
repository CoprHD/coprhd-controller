/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.ipreconfig;

import com.emc.storageos.model.property.PropertyConstants;
import org.codehaus.jackson.annotate.JsonIgnore;

import java.io.*;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Physical Site IP Information
 */
public class SiteIpInfo implements Serializable {

    private SiteIpv4Setting ipv4_setting;
    private SiteIpv6Setting ipv6_setting;

    public SiteIpInfo() {
    }

    public SiteIpInfo(SiteIpv4Setting ipv4_setting, SiteIpv6Setting ipv6_setting) {
        this.ipv4_setting = ipv4_setting;
        this.ipv6_setting = ipv6_setting;
    }

    @XmlElement(name = "ipv4_setting")
    public SiteIpv4Setting getIpv4Setting() {
        return ipv4_setting;
    }

    public void setIpv4Setting(SiteIpv4Setting ipv4_setting) {
        this.ipv4_setting = ipv4_setting;
    }

    @XmlElement(name = "ipv6_setting")
    public SiteIpv6Setting getIpv6Setting() {
        return ipv6_setting;
    }

    public void setIpv6Setting(SiteIpv6Setting ipv6_setting) {
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

    public static SiteIpInfo deserialize(byte[] data) throws IOException,
            ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        try {
            obj = in.readObject();
        } finally {
            in.close();
        }
        return (SiteIpInfo) obj;
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

        if (!ipv4_setting.equals(((SiteIpInfo) obj).getIpv4Setting())) {
            return false;
        }
        if (!ipv6_setting.equals(((SiteIpInfo) obj).getIpv6Setting())) {
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

    public String toVdcSiteString(String vdcsiteid) {
        StringBuffer propStrBuf = new StringBuffer();
        propStrBuf.append(ipv4_setting.toVdcSiteString(vdcsiteid));
        propStrBuf.append(ipv6_setting.toVdcSiteString(vdcsiteid));
        propStrBuf.append(vdcsiteid).append(PropertyConstants.UNDERSCORE_DELIMITER)
                .append(PropertyConstants.NODE_COUNT_KEY).append(PropertyConstants.DELIMITER)
                .append(ipv4_setting.getNetworkAddrs().size()).append("\n");

        return propStrBuf.toString();
    }

    /*
     * Load key/value property map
     */
    public void loadFromPropertyMap(Map<String, String> propMap)
    {
        ipv4_setting = new SiteIpv4Setting();
        ipv4_setting.loadFromPropertyMap(propMap);

        ipv6_setting = new SiteIpv6Setting();
        ipv6_setting.loadFromPropertyMap(propMap);
    }

    @JsonIgnore
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

    @JsonIgnore
    public int getNodeCount() {
        int nodeCount = ipv4_setting.getNetworkAddrs().size();
        if (nodeCount == 0) {
            nodeCount = ipv6_setting.getNetworkAddrs().size();
        }
        return nodeCount;
    }
}
