/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;

/**
 * Utility class to generate VDC/Site property for syssvc.
 * 
 * The VDC/Site configurations are stored in ZK as follows:
 * /config/disasterRecoverySites/<vdc_short_id>/<site_uuid>     has all the VDC/site configurations
 * /config/disasterRecoveryActive/<vdc_short_id>               specifies which site is the active in each VDC
 * /config/geoLocalVDC/global                                   specifies the local VDC in the geo federation
 * 
 * The vdcconfig.properties includes the node IPs as the following
 * vdc_vdc*_site*_node_count(e.g vdc_vdc1_site1_node_count)             - number of nodes in specified site of specified vdc
 * vdc_vdc*_site*_network_*_ipaddr(e.g vdc_vdc1_site1_network_1_ipaddr) - IPv4 address of specified node in specified site of specified vdc 
 * vdc_vdc*_site*_network_*_vip                                         - VIP of specific site of specified vdc
 * vdc_vdc*_site*_network_*_ipaddr6                                     - IPv6 address of specified node in specified site of specified vdc
 * vdc_vdc*_site*_network_vip6                                          - IPv6 VIP specific site of specified vdc
 * vdc_ids  - all vdc ids (e.g vdc1, vdc2 .. )
 * vdc_myid - current vdc short id
 * site_ids - all site ids in current vdc
 * site_active_id - active site id in current vdc
 * site_myid   - current site id 
 * 
 * back_compat_preyoda - true/false. If it is upgraded from pre-yoda, we set it true so dbsvc/syssvc may keep 
 *                       some features(e.g ssl encryption) for backward compatibility
 * vdc_config_version  - timestamp for last vdc config change. It should be same for all instance in GEO/DR                      
 */
public class VdcConfigUtil {
    private static final String DEFAULT_ACTIVE_SITE_ID = "site1";

    private static final Logger log = LoggerFactory.getLogger(VdcConfigUtil.class);

    public static final String VDC_CONFIG_VERSION = "vdc_config_version";
    public static final String VDC_MYID = "vdc_myid";
    public static final String VDC_IDS = "vdc_ids";
    public static final String VDC_SITE_NODE_COUNT_PTN = "vdc_%s_%s_node_count";
    public static final String VDC_SITE_IPADDR6_PTN = "vdc_%s_%s_network_%d_ipaddr6";
    public static final String VDC_SITE_IPADDR_PTN = "vdc_%s_%s_network_%d_ipaddr";
    public static final String VDC_SITE_VIP_PTN = "vdc_%s_%s_network_vip";
    public static final String VDC_SITE_VIP6_PTN = "vdc_%s_%s_network_vip6";
    public static final String SITE_IS_STANDBY="site_is_standby";
    public static final String SITE_MY_UUID="site_my_uuid";
    public static final String SITE_MYID="site_myid";
    public static final String SITE_IDS="site_ids";
    public static final String SITE_ACTIVE_ID="site_active_id";
    public static final String BACK_COMPAT_PREYODA="back_compat_preyoda";
    
    private DrUtil drUtil;
    private Boolean backCompatPreYoda = false;
    private CoordinatorClient coordinator;
    
    public VdcConfigUtil(CoordinatorClient coordinator) {
        drUtil = new DrUtil(coordinator);
        this.coordinator = coordinator;
    }

    public void setBackCompatPreYoda(Boolean backCompatPreYoda) {
        this.backCompatPreYoda = backCompatPreYoda;
    }
    
    /**
     * generates a property map containing all the VDC/site information this VDC has in
     * ZK, to be used by syssvc to update the local system property.
     * 
     * @return a map containing VDC/site configs
     */
    public Map<String, String> genVdcProperties() {
        Map<String, String> vdcConfig = new HashMap<>();

        Map<String, List<Site>> vdcSiteMap = drUtil.getVdcSiteMap();
        if (vdcSiteMap.isEmpty()) {
            log.warn("No virtual data center defined in ZK");
            throw new IllegalStateException("No virtual data center defined in ZK");
        }

        vdcConfig.put(VDC_MYID, drUtil.getLocalVdcShortId());

        List<String> vdcShortIdList = new ArrayList<>(vdcSiteMap.keySet());
        for (String vdcShortId : vdcShortIdList) {
            genSiteProperties(vdcConfig, vdcShortId, vdcSiteMap.get(vdcShortId));
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
        vdcConfig.put(BACK_COMPAT_PREYODA, String.valueOf(backCompatPreYoda));
        
        log.info("vdc config property: \n{}", vdcConfig.toString());

        return vdcConfig;
    }

    private void genSiteProperties(Map<String, String> vdcConfig, String vdcShortId, List<Site> sites) {
        String activeSiteId = null;
        try {
            activeSiteId = drUtil.getActiveSite().getUuid();
        } catch (RetryableCoordinatorException e) {
            log.warn("Failed to find active site id from ZK, go on since it maybe switchover case");
        }
        
        SiteInfo siteInfo = coordinator.getTargetInfo(SiteInfo.class);
        Site localSite = drUtil.getLocalSite();
        
        if (StringUtils.isEmpty(activeSiteId) && SiteInfo.DR_OP_SWITCHOVER.equals(siteInfo.getActionRequired())) {
            activeSiteId = drUtil.getSiteFromLocalVdc(siteInfo.getTargetSiteUUID()).getUuid();
        }
        
        Collections.sort(sites, new Comparator<Site>() {
            @Override
            public int compare(Site a, Site b) {
                return (int)(a.getCreationTime() - b.getCreationTime());
            }
        });
        
        List<String> shortIds = new ArrayList<>();
        for (Site site : sites) {
            
            if (shouldExcludeFromConfig(site)) {
                log.info("Ignore site {} of vdc {}", site.getSiteShortId(), site.getVdcShortId());
                continue;
            }

            // exclude the paused sites from the standby site list on every site except the paused site
            // this will make it easier to resume the data replication.
            if (!drUtil.isLocalSite(site)
                    && (site.getState().equals(SiteState.STANDBY_PAUSING)
                    || site.getState().equals(SiteState.STANDBY_PAUSED)
                    || site.getState().equals(SiteState.STANDBY_REMOVING)
                    || site.getState().equals(SiteState.ACTIVE_FAILING_OVER))) {

                continue;
            }
            
            int siteNodeCnt = 0;
            Map<String, String> siteIPv4Addrs = site.getHostIPv4AddressMap();
            Map<String, String> siteIPv6Addrs = site.getHostIPv6AddressMap();

            List<String> siteHosts = getHostsFromIPAddrMap(siteIPv4Addrs, siteIPv6Addrs);
            String siteShortId = site.getSiteShortId();
            
            // sort the host names as vipr1, vipr2 ...
            Collections.sort(siteHosts);
            
            for (String hostName : siteHosts) {
                siteNodeCnt++;
                String address = siteIPv4Addrs.get(hostName);
                
                vdcConfig.put(String.format(VDC_SITE_IPADDR_PTN, vdcShortId, siteShortId, siteNodeCnt),
                      address == null ? "" : address);

                address = siteIPv6Addrs.get(hostName);
                vdcConfig.put(String.format(VDC_SITE_IPADDR6_PTN, vdcShortId, siteShortId, siteNodeCnt),
                      address == null ? "" : address);
            }

            vdcConfig.put(String.format(VDC_SITE_NODE_COUNT_PTN, vdcShortId, siteShortId),
                    String.valueOf(siteNodeCnt));

            vdcConfig.put(String.format(VDC_SITE_VIP_PTN, vdcShortId, siteShortId), site.getVip());
            vdcConfig.put(String.format(VDC_SITE_VIP6_PTN, vdcShortId, siteShortId), site.getVip6());

            if (drUtil.isLocalSite(site)) {
                vdcConfig.put(SITE_MYID, siteShortId);
                vdcConfig.put(SITE_MY_UUID, site.getUuid());
            }
            shortIds.add(siteShortId);
        }
        Collections.sort(shortIds);

        if (drUtil.getLocalVdcShortId().equals(vdcShortId)) {
            // right now we assume that SITE_IDS and SITE_IS_STANDBY only makes sense for local VDC
            // moving forward this may or may not be the case.
            vdcConfig.put(SITE_IDS, StringUtils.join(shortIds, ','));
            vdcConfig.put(SITE_IS_STANDBY, String.valueOf(!localSite.getUuid().equals(activeSiteId)));
            vdcConfig.put(SITE_ACTIVE_ID, StringUtils.isEmpty(activeSiteId) ?
                    DEFAULT_ACTIVE_SITE_ID :
                    drUtil.getSiteFromLocalVdc(activeSiteId).getSiteShortId());
        }
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
