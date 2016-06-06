/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.ipreconfig;

import com.emc.storageos.model.property.PropertyConstants;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.io.*;
import java.util.*;

/**
 * Cluster IP Information
 * Each cluster could include multiple physical sites
 */
@XmlRootElement(name = "cluster_ipinfo")
public class ClusterIpInfo implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(ClusterIpInfo.class);

    private Map<String, SiteIpInfo> siteIpInfoMap = new HashMap<String, SiteIpInfo>();

    public ClusterIpInfo() {
    }

    @XmlElementWrapper(name="sites")
    public Map<String, SiteIpInfo> getSiteIpInfoMap() {
        return siteIpInfoMap;
    }

    public void setSiteIpInfoMap(Map<String, SiteIpInfo> siteIpInfoMap) {
        this.siteIpInfoMap = siteIpInfoMap;
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
                 + ((siteIpInfoMap == null) ? 0 : siteIpInfoMap.hashCode());
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

        if (!siteIpInfoMap.equals(((ClusterIpInfo) obj).getSiteIpInfoMap())) {
            return false;
        }

        return true;
    }

	@Override
    public String toString() {
        StringBuffer propStrBuf = new StringBuffer();

        SortedSet<String> keys = new TreeSet<String>(siteIpInfoMap.keySet());
        for (String key : keys) {
            propStrBuf.append(key);
            propStrBuf.append(PropertyConstants.DELIMITER);
            propStrBuf.append(siteIpInfoMap.get(key).toString());
            propStrBuf.append("\n");
        }

        return propStrBuf.toString();
    }

    public String toVdcSiteString() {
        StringBuffer propStrBuf = new StringBuffer();

        SortedSet<String> vdcsiteids = new TreeSet<String>(siteIpInfoMap.keySet());
        for (String vdcsiteid : vdcsiteids) {
            propStrBuf.append(siteIpInfoMap.get(vdcsiteid).toVdcSiteString(vdcsiteid));
        }

        return propStrBuf.toString();
    }

    /*
     * Load key/value property map
     *
     */
    public void loadFromPropertyMap(Map<String, String> globalPropMap)
    {
        /* vdc site property prefix is vdc_<vdcshortId>_<siteShortId>
        property example: vdc_vdc1_site2_network_1_ipaddr */

        // group global properties in site level - "<vdcsiteId> -> <vdcsiteInternalProperties>"
        Map<String, Map<String, String>> vdcsitePropMap = new HashMap<String, Map<String, String>>();
        SortedSet<String> globalPropNames = new TreeSet<String>(globalPropMap.keySet());
        for (String globalPropName : globalPropNames) {
            if (!globalPropName.startsWith("vdc_vdc")) continue;

            // separate global prop name to vdcsiteId and internal prop name;
            String[] tmpFields = globalPropName.split(PropertyConstants.UNDERSCORE_DELIMITER);
            String vdcsiteId = tmpFields[0] + PropertyConstants.UNDERSCORE_DELIMITER + tmpFields[1] + PropertyConstants.UNDERSCORE_DELIMITER + tmpFields[2];
            String vdcsiteInternalPropName = globalPropName.substring(vdcsiteId.length()+1);
            log.debug("vdcsiteId={}, vdcsiteInternalPropName={}", vdcsiteId, vdcsiteInternalPropName);

            // put globalPropMap into vdcsitePropMap;
            Map<String, String> sitePropMap = vdcsitePropMap.get(vdcsiteId);
            if (null == sitePropMap) {
                sitePropMap = new HashMap<String, String>();
            }
            String propValue = globalPropMap.get(globalPropName);
            log.debug("PropValue={}", propValue);

            sitePropMap.put(vdcsiteInternalPropName, propValue);
            vdcsitePropMap.put(vdcsiteId, sitePropMap);
        }

        // load all the site level properties
        SortedSet<String> vdcsiteIds = new TreeSet<String>(vdcsitePropMap.keySet());
        for (String vdcsiteId : vdcsiteIds) {
            SiteIpInfo siteIpInfo = siteIpInfoMap.get(vdcsiteId);
            if (null == siteIpInfo) {
                siteIpInfo = new SiteIpInfo();
            }
            siteIpInfo.loadFromPropertyMap(vdcsitePropMap.get(vdcsiteId));
            siteIpInfoMap.put(vdcsiteId, siteIpInfo);
        }
    }

    /**
     * Validate if target addresses are acceptable
     *
     * @return
     */
    public String validate(ClusterIpInfo currentIpInfo) {
        String errmsg = "";
        log.info("target cluster ip prop = {}", toVdcSiteString());

        for (Map.Entry<String, SiteIpInfo> me: getSiteIpInfoMap().entrySet()) {
            int nodecount = currentIpInfo.getSiteIpInfoMap().get(me.getKey()).getNodeCount();
            errmsg = me.getValue().validate(nodecount);
            if (!errmsg.isEmpty()) {
                return errmsg;
            }
        }

        if(this.equals(currentIpInfo)) {
            errmsg="Target IPs are the same as current ones.";
        }

        if(isDuplicated()) {
            errmsg="IPs duplicate among sites.";
        }
        return errmsg;
    }

    /**
     * Validate if address duplicats among sites
     *
     * @return
     */
    @JsonIgnore
    public boolean isDuplicated() {
        List<String> list = new ArrayList<String>();

        for (Map.Entry<String, SiteIpInfo> me: getSiteIpInfoMap().entrySet()) {
            SiteIpInfo siteipInfo = me.getValue();
            SiteIpv4Setting ipv4 = siteipInfo.getIpv4Setting();
            if(!ipv4.getNetworkVip().equals(PropertyConstants.IPV4_ADDR_DEFAULT)) {
                list.add(ipv4.getNetworkVip());
            }
            for (String network_addr : ipv4.getNetworkAddrs()) {
                if(!network_addr.equals(PropertyConstants.IPV4_ADDR_DEFAULT)) {
                    list.add(network_addr);
                }
            }
            SiteIpv6Setting ipv6 = siteipInfo.getIpv6Setting();
            if(!ipv6.getNetworkVip6().equals(PropertyConstants.IPV6_ADDR_DEFAULT)) {
                list.add(ipv6.getNetworkVip6());
            }
            for (String network_addr : ipv6.getNetworkAddrs()) {
                if(!network_addr.equals(PropertyConstants.IPV6_ADDR_DEFAULT)) {
                    list.add(network_addr);
                }
            }
        }

        Set<String> set = new HashSet<String>(list);
        if (set.size() < list.size()) {
            return true;
        }
        return false;
    }

}
