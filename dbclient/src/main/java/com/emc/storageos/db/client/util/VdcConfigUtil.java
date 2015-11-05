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
import com.emc.storageos.coordinator.client.service.DrUtil;
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
    public static final String VDC_STANDBY_VIP_PTN = "vdc_%s_%s_network_vip";
    public static final String SITE_IS_STANDBY="site_is_standby";
    public static final String SITE_IS_SWITCHING_OVER="site_is_switchingover";
    public static final String SITE_MYID="site_myid";
    public static final String SITE_IDS="site_ids";

    private CoordinatorClient coordinator;
    private DrUtil drUtil;

    @Autowired
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
        drUtil = new DrUtil(coordinator);
    }

    /**
     * generates a Properties instance containing all the VDC information this VDC has in
     * its local db, to be used by syssvc to update the local system property.
     * 
     * @return a Properties instance containing VDC configs
     */
    public Map<String, String> genVdcProperties() {
        Map<String, String> vdcConfig = new HashMap<>();

        Map<String, List<Site>> vdcSiteMap = getVdcSiteMap();
        List<String> vdcShortIdList = new ArrayList<>(vdcSiteMap.keySet());
        for (String vdcShortId : vdcShortIdList) {

            // FIXME: need to determine if the vdc is local or not.
            // THere's no way to know that without querying db, unless we persist such information in ZK too.
            vdcConfig.put(VDC_MYID, vdcShortId);

            genSiteProperties(vdcConfig, vdcShortId, vdcSiteMap.get(vdcShortId));
        }

        if (vdcSiteMap.isEmpty()) {
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

    private Map<String, List<Site>> getVdcSiteMap() {
        Map<String, List<Site>> vdcSiteMap = new HashMap<>();
        List<Site> sites = drUtil.listSites();
        for (Site site : sites) {
            String vdcId = site.getVdcShortId();
            if (!vdcSiteMap.containsKey(vdcId)) {
                vdcSiteMap.put(vdcId, new ArrayList<Site>());
            }
            vdcSiteMap.get(vdcId).add(site);
        }
        return vdcSiteMap;
    }

    private void genSiteProperties(Map<String, String> vdcConfig, String vdcShortId, List<Site> sites) {
        DrUtil drUtil = new DrUtil(coordinator);
        String primarySiteId = drUtil.getPrimarySiteId();
        
        Collections.sort(sites, new Comparator<Site>() {
            @Override
            public int compare(Site a, Site b) {
                return (int)(a.getCreationTime() - b.getCreationTime());
            }
        });
        
        List<String> shortIds = new ArrayList<>();
        for (Site site : sites) {
            boolean isPrimarySite = site.getUuid().equals(primarySiteId);

            if (shouldExcludeFromConfig(site)) {
                log.info("Ignore site {} of vdc {}", site.getStandbyShortId(), site.getVdcShortId());
                continue;
            }

            // exclude the paused sites from the standby site list on every site except the paused site
            // this will make it easier to resume the data replication.
            if (!drUtil.isLocalSite(site)) {
                if (site.getState().equals(SiteState.STANDBY_PAUSED) || site.getState().equals(SiteState.STANDBY_REMOVING) ) {
                    continue;
                }
            }
            
            int siteNodeCnt = 0;
            Map<String, String> siteIPv4Addrs = site.getHostIPv4AddressMap();
            Map<String, String> siteIPv6Addrs = site.getHostIPv6AddressMap();

            List<String> siteHosts = getHostsFromIPAddrMap(siteIPv4Addrs, siteIPv6Addrs);
            String siteShortId = site.getStandbyShortId();
            
            // sort the host names as vipr1, vipr2 ...
            Collections.sort(siteHosts);
            
            for (String hostName : siteHosts) {
                siteNodeCnt++;
                String address = siteIPv4Addrs.get(hostName);
                if (isPrimarySite) {
                    vdcConfig.put(String.format(VDC_IPADDR_PTN, vdcShortId, siteNodeCnt),
                            address == null ? "" : address);
                } else {
                    vdcConfig.put(String.format(VDC_STANDBY_IPADDR_PTN, vdcShortId, siteShortId, siteNodeCnt),
                            address == null ? "" : address);
                }

                address = siteIPv6Addrs.get(hostName);
                if (isPrimarySite) {
                    vdcConfig.put(String.format(VDC_IPADDR6_PTN, vdcShortId, siteNodeCnt),
                            address == null ? "" : address);
                } else {
                    vdcConfig.put(String.format(VDC_STANDBY_IPADDR6_PTN, vdcShortId, siteShortId, siteNodeCnt),
                            address == null ? "" : address);
                }
            }

            if (isPrimarySite) {
                vdcConfig.put(String.format(VDC_NODE_COUNT_PTN, vdcShortId), String.valueOf(siteNodeCnt));
            } else {
                vdcConfig.put(String.format(VDC_STANDBY_NODE_COUNT_PTN, vdcShortId, siteShortId),
                        String.valueOf(siteNodeCnt));
            }

            if (isPrimarySite) {
                vdcConfig.put(String.format(VDC_VIP_PTN, vdcShortId), site.getVip());
            } else {
                vdcConfig.put(String.format(VDC_STANDBY_VIP_PTN, vdcShortId, siteShortId), site.getVip());
            }

            if (drUtil.isLocalSite(site) && site.getState().equals(SiteState.STANDBY_SWITCHING_OVER)) {
                vdcConfig.put(SITE_MYID, siteShortId);
            }

            if (!isPrimarySite) {
                shortIds.add(siteShortId);
            }
        }
        Collections.sort(shortIds);
        vdcConfig.put(SITE_IDS, StringUtils.join(shortIds, ','));
        
        vdcConfig.put(SITE_IS_STANDBY, String.valueOf(!drUtil.isPrimary()));
        
        //set whether this site is doing switch over, this flag is used in etc/genconfig.d/geodb for generating DC name
        Site currentSite = drUtil.getSite(coordinator.getSiteId());
        vdcConfig.put(SITE_IS_SWITCHING_OVER, String.valueOf(currentSite.getState() == SiteState.PRIMARY_SWITCHING_OVER
                        || currentSite.getState() == SiteState.STANDBY_SWITCHING_OVER));
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
     * Return true to indicate current site need be excluded in vdc config properties
     * 
     * @param site
     * @return
     */
    private boolean shouldExcludeFromConfig(Site site) {
        // No node ip available in the site config
        return site.getNodeCount() < 1;
    }
}
