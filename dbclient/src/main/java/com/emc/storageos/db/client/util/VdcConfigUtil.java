/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
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
    public static final String VDC_CONFIG_VERSION = "vdc_config_version";
    public static final String VDC_MYID = "vdc_myid";
    public static final String VDC_IDS = "vdc_ids";
    public static final String VDC_NODE_COUNT_PTN = "vdc_%s_node_count";
    public static final String VDC_IPADDR_PTN = "vdc_%s_network_%d_ipaddr";
    public static final String VDC_IPADDR6_PTN = "vdc_%s_network_%d_ipaddr6";
    public static final String VDC_STANDBY_NODE_COUNT_PTN = "vdc_%s_%s_node_count";
    public static final String VDC_STANDBY_IPADDR6_PTN = "vdc_%s_%s_network_%d_ipaddr6";
    public static final String VDC_STANDBY_IPADDR_PTN = "vdc_%s_%s_network_%d_ipaddr";
    public static final String VDC_VIP_PTN = "vdc_%s_network_vip";
    public static final String VDC_VIP6_PTN = "vdc_%s_network_vip6";
    public static final String SITE_IS_STANDBY="site_is_standby";
    public static final String SITE_MYID="site_myid";
    public static final String SITE_IDS="site_ids";

    private DbClient dbclient;
    private CoordinatorClient coordinator;

    @Autowired
    public void setDbclient(DbClient dbclient) {
        this.dbclient = dbclient;
    }

    @Autowired
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
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
            List<String> hostNameList = getHostsFromIPAddrMap(IPv4Addresses, IPv6Addresses);

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

            genSiteProperties(vdcConfig, vdc);
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

        log.info("vdc config property: \n{}", vdcConfig.toString());

        return vdcConfig;
    }

    private void genSiteProperties(Map<String, String> vdcConfig, VirtualDataCenter vdc) {
        String shortId = vdc.getShortId();
        String primarySiteId = coordinator.getPrimarySiteId();
        String currentSiteId = coordinator.getSiteId();
        
        // Sort the sites by creation time - ascending order
        List<Site> siteList = new ArrayList<>();
        for(Configuration config : coordinator.queryAllConfiguration(Site.CONFIG_KIND)) {
            Site site = new Site(config);
            if (site.getVdc().equals(vdc.getId()) && site.getState() != SiteState.PRIMARY) {
                siteList.add(site);
            }
        }
        Collections.sort(siteList, new Comparator<Site>() {
            @Override
            public int compare(Site a, Site b) {
                return (int)(a.getCreationTime() - b.getCreationTime());
            }
        });
        
        List<String> shortIds = new ArrayList<>();
        for (Site site : siteList) {
            if (site.getUuid().equals(primarySiteId)) {
                continue; // ignore primary site 
            }

            // exclude the paused sites from the standby site list on every site except the paused site
            // this will make it easier to resume the data replication.
            if (!isLocalSite(site)) {
                if (site.getState().equals(SiteState.STANDBY_PAUSED) || site.getState().equals(SiteState.STANDBY_REMOVING) ) {
                    continue;
                }
            }
            
            int standbyNodeCnt = 0;
            Map<String, String> standbyIPv4Addrs = site.getHostIPv4AddressMap();
            Map<String, String> standbyIPv6Addrs = site.getHostIPv6AddressMap();

            List<String> standbyHosts = getHostsFromIPAddrMap(standbyIPv4Addrs, standbyIPv6Addrs);
            String standbyShortId = site.getStandbyShortId();
            
            for (String hostName : standbyHosts) {
                standbyNodeCnt++;
                String address = standbyIPv4Addrs.get(hostName);
                vdcConfig.put(String.format(VDC_STANDBY_IPADDR_PTN, shortId, standbyShortId, standbyNodeCnt),
                        address == null ? "" : address);

                address = standbyIPv6Addrs.get(hostName);
                vdcConfig.put(String.format(VDC_STANDBY_IPADDR6_PTN, shortId, standbyShortId, standbyNodeCnt),
                        address == null ? "" : address);
            }
            vdcConfig.put(String.format(VDC_STANDBY_NODE_COUNT_PTN, shortId, standbyShortId), String.valueOf(standbyNodeCnt));
            if (isLocalSite(site)) {
                vdcConfig.put(SITE_MYID, standbyShortId);
            }
            
            shortIds.add(standbyShortId);
        }
        Collections.sort(shortIds);
        vdcConfig.put(SITE_IDS, StringUtils.join(shortIds, ','));
        
        
        boolean isStandby = !currentSiteId.equals(primarySiteId);
        vdcConfig.put(SITE_IS_STANDBY, String.valueOf(isStandby));
    }

    private boolean isLocalSite(Site site) {
        return site.getUuid().equals(coordinator.getSiteId());
    }

    private List<String> getHostsFromIPAddrMap(Map<String, String> IPv4Addresses, Map<String, String> IPv6Addresses) {
        List<String> hostNameListV4 = new ArrayList<>(IPv4Addresses.keySet());
        List<String> hostNameListV6 = new ArrayList<>(IPv6Addresses.keySet());
        List<String> hostNameList = hostNameListV4;

        if (hostNameListV4.isEmpty()) {
            hostNameList = hostNameListV6;
        }
        return hostNameList;
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
}
