/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;

/**
 * Utility class to generate Vdc property for syssvc.
 */
public class VdcConfigUtil {
    private static final Logger log = LoggerFactory.getLogger(VdcConfigUtil.class);

    // It's no longer a version since it's not incremental, but it serves the same
    // purpose
    public static final String VDC_CONFIG_HASHCODE = "vdc_config_hashcode";
    public static final String VDC_MYID = "vdc_myid";
    public static final String VDC_IDS = "vdc_ids";
    public static final String VDC_NODE_COUNT_PTN = "vdc_%s_node_count";
    public static final String VDC_IPADDR_PTN = "vdc_%s_network_%d_ipaddr";

    public static final String VDC_IPADDR6_PTN = "vdc_%s_network_%d_ipaddr6";
    public static final String VDC_VIP_PTN = "vdc_%s_network_vip";
    public static final String VDC_VIP6_PTN = "vdc_%s_network_vip6";

    private DbClient dbclient;

    @Autowired
    public void setDbclient(DbClient dbclient) {
        this.dbclient = dbclient;
    }

    /**
     * generates a Properties instance containing all the VDC information this VDC has in
     * its local db, to be used by syssvc to update the local system property.
     * 
     * @return a Properties instance containing VDC configs
     */
    public Map<String, String> genVdcProperties() {
        Map<String, String> vdcConfig = new HashMap<>();

        List<String> vdcShortIdList = new ArrayList<>();
        List<URI> vdcIds = dbclient.queryByType(VirtualDataCenter.class, true);
        int cnt = 0;
        for (URI vdcId : vdcIds) {
            VirtualDataCenter vdc = dbclient.queryObject(VirtualDataCenter.class, vdcId);
            if (shouldExcludeFromConfig(vdc)) {
                log.info("Ignore vdc {} with status {}", vdc.getShortId(), vdc.getConnectionStatus());
                continue;
            }

            String shortId = vdc.getShortId();
            vdcShortIdList.add(shortId);
            cnt++;

            if (vdc.getLocal()) {
                vdcConfig.put(VDC_MYID, shortId);
            }

            vdcConfig.put(String.format(VDC_NODE_COUNT_PTN, shortId),
                    vdc.getHostCount().toString());

            String address;
            StringMap IPv4Addresses = vdc.getHostIPv4AddressesMap();
            StringMap IPv6Addresses = vdc.getHostIPv6AddressesMap();
            List<String> hostNameListV4 = new ArrayList<>(IPv4Addresses.keySet());
            List<String> hostNameListV6 = new ArrayList<>(IPv6Addresses.keySet());
            List<String> hostNameList = hostNameListV4;

            if (hostNameListV4.isEmpty()) {
                hostNameList = hostNameListV6;
            }

            // sort the host names (node1, node2, node3 ...), 5 nodes tops so it's
            // simpler than sorting vdc short ids below
            Collections.sort(hostNameList);

            int i = 0;
            for (String hostName : hostNameList) {
                i++;
                address = IPv4Addresses.get(hostName);
                if (address == null) {
                    address = "";
                }

                vdcConfig.put(String.format(VDC_IPADDR_PTN, shortId, i), address);

                address = IPv6Addresses.get(hostName);
                if (address == null) {
                    address = "";
                }

                vdcConfig.put(String.format(VDC_IPADDR6_PTN, shortId, i), address);
            }

            String vip = vdc.getApiEndpoint();
            try {
                InetAddress vipInetAddr = InetAddress.getByName(vip);
                if (vipInetAddr instanceof Inet6Address) {
                    if (vip.startsWith("[")) {
                        // strip enclosing '[ and ]'
                        vip = vip.substring(1, vip.length() - 1);
                    }
                    vdcConfig.put(String.format(VDC_VIP6_PTN, shortId), vip);
                    vdcConfig.put(String.format(VDC_VIP_PTN, shortId), "");
                } else {
                    vdcConfig.put(String.format(VDC_VIP_PTN, shortId), vip);
                    vdcConfig.put(String.format(VDC_VIP6_PTN, shortId), "");
                }
            } catch (UnknownHostException ex) {
                log.error("Cannot recognize vip " + vip, ex);
            }

            if (i != vdc.getHostCount()) {
                throw new IllegalStateException(String.format("Mismatched node counts." +
                        "%d from hostCount, %d from hostList", vdc.getHostCount(), i));
            }
        }

        if (cnt == 0) {
            log.warn("No virtual data center defined in local db");
            return vdcConfig;
        }
        // sort the vdc short ids by their indices, note that vdc11 should be greater
        // than vdc2
        Collections.sort(vdcShortIdList, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                Integer leftInt = Integer.valueOf(left.substring(3));
                Integer rightInt = Integer.valueOf(right.substring(3));
                return leftInt.compareTo(rightInt);
            }
        });
        vdcConfig.put(VDC_IDS, StringUtils.join(vdcShortIdList, ","));

        setVdcConfigVersion(vdcConfig);

        log.info("vdc config property: \n{}", vdcConfig.toString());

        return vdcConfig;
    }

    /**
     * Return true to indicate current vdc need be excluded in vdc config properties
     * 
     * @param vdc
     * @return
     */
    private boolean shouldExcludeFromConfig(VirtualDataCenter vdc) {
        // No node ip available in the vdc object
        if (vdc.getHostCount() == null || vdc.getHostCount().intValue() < 1) {
            return true;
        }
        return false;
    }

    private void setVdcConfigVersion(Map<String, String> vdcConfig) {
        List<String> propertyValues = new ArrayList<>();
        propertyValues.add(vdcConfig.get(VDC_MYID));
        propertyValues.add(vdcConfig.get(VDC_IDS));
        for (String vdcShortId : vdcConfig.get(VDC_IDS).split(",")) {
            String nodeCountStr = vdcConfig.get(String.format(VDC_NODE_COUNT_PTN,
                    vdcShortId));
            propertyValues.add(nodeCountStr);

            int nodeCount = Integer.valueOf(nodeCountStr);
            for (int i = 1; i <= nodeCount; i++) {
                propertyValues.add(vdcConfig.get(String.format(VDC_IPADDR_PTN,
                        vdcShortId, i)));
            }
        }

        int hashCode = Arrays.hashCode(propertyValues.toArray(new String[propertyValues.size()]));
        log.info("the current vdc config hashcode is {}", hashCode);
        vdcConfig.put(VDC_CONFIG_HASHCODE, String.valueOf(hashCode));
    }
}
