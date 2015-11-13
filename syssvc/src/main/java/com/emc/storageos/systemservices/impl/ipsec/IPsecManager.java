/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.ipsec;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.model.ipsec.IPsecNodeState;
import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.security.ipsec.IPsecKeyGenerator;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is to handle all ipsec related requests from web app.
 */
public class IPsecManager {

    private static final Logger log = LoggerFactory.getLogger(IPsecManager.class);

    private static final String VDC_CONFIG_VERSION = "vdc_config_version";

    @Autowired
    IPsecConfig ipsecConfig;

    @Autowired
    IPsecKeyGenerator ipsecKeyGenerator;

    CoordinatorClientImpl coordinator;
    DrUtil drUtil;

    /**
     * Checking ipsec status against the entire system.
     * @return
     */
    public IPsecStatus checkStatus() {

        log.info("Checking ipsec status ...");

        String vdcConfigVersion = loadVdcConfigVersionFromZK();

        boolean runtimeGood = checkRunTimeStatus();

        boolean configGood = false;
        List<IPsecNodeState> problemNodeStatus = null;
        if (vdcConfigVersion.equals("0")) {
            configGood = true;
        } else {
            List<IPsecNodeState> nodeStatus = getIPsecVersionsOnAllNodes();
            problemNodeStatus = checkConfigurations(vdcConfigVersion, nodeStatus);
            configGood = problemNodeStatus.isEmpty();
        }
        log.info("IPsec configuration check is done. The result is {}", configGood);

        IPsecStatus status = new IPsecStatus();

        boolean allGood = runtimeGood && configGood;

        status.setIsGood(allGood);
        status.setVersion(vdcConfigVersion);
        if (allGood) {
            return status;
        }

        // Send back more details if something error.
        status.setNodeStatus(problemNodeStatus);
        log.info("ipsec status is {}", allGood);
        return status;
    }

    /**
     * Rotate IPsec preshared key for the entired system.
     * @return
     */
    public String rotateKey() {
        String psk = ipsecKeyGenerator.generate();
        try {
            ipsecConfig.setPreSharedKey(psk);
            String version = updateTargetSiteInfo();
            log.info("IPsec Key gets rotated successfully to the version {}", version);
            return version;
        } catch (Exception e) {
            throw com.emc.storageos.security.exceptions.SecurityException.fatals.failToRotateIPsecKey(e);
        }
    }

    private List<IPsecNodeState> checkConfigurations(String vdcConfigVersion, List<IPsecNodeState> nodeStatus) {
        List<IPsecNodeState> problemNodeStatus = new ArrayList<>();

        for (IPsecNodeState node : nodeStatus) {
            log.info("vdcVersion = {}, node version = {}", vdcConfigVersion, node.getVersion());
            if ( (node.getVersion() == null) || ! vdcConfigVersion.equals(node.getVersion()) ) {
                log.info("Found problem on the node {} where the config version is {}", node.getIp(), node.getVersion());
                problemNodeStatus.add(node);
            }
        }

        return problemNodeStatus;
    }

    private boolean checkRunTimeStatus() {
        LocalRepository localRepository = new LocalRepository();
        String[] problemIPs = localRepository.checkIpsecConnection();
        boolean runtimeGood = problemIPs[0].isEmpty();
        log.info("Checked IPsec local runtime status which is {}", runtimeGood);
        return runtimeGood;
    }

    private List<IPsecNodeState> getIPsecVersionsOnAllNodes() {
        List<IPsecNodeState> nodeStatus = new ArrayList<>();

        LocalRepository localRepository = new LocalRepository();

        for (Site site : drUtil.listSites()) {
            for (String ip : site.getHostIPv4AddressMap().values()) {
                log.info("Collecting ipsec config version from {}", ip);
                IPsecNodeState nodeState = new IPsecNodeState();
                nodeState.setIp(ip);
                try {
                    Map<String, String> ipsecProps = localRepository.getIpsecProperties(ip);
                    nodeState.setVersion(ipsecProps.get(VDC_CONFIG_VERSION));
                    log.info("Collected ipsec config version from {}, which is {}", ip, ipsecProps.get(VDC_CONFIG_VERSION));
                } catch (Exception e) {
                    log.info("Failed to collect ipsec config version from {}. Just set to null", ip);
                    nodeState.setVersion(null);
                }
            }
        }

        return nodeStatus;
    }

    private String loadVdcConfigVersionFromZK() {
        String vdcConfigVersion = Long.toString(coordinator.getTargetInfo(SiteInfo.class).getVdcConfigVersion());
        log.info("Loaded Vdc config version is {}", vdcConfigVersion);
        return vdcConfigVersion;
    }

    private String updateTargetSiteInfo() {

        long vdcConfigVersion = System.currentTimeMillis();

        for (Site site : drUtil.listSites()) {
            SiteInfo siteInfo;
            String siteId = site.getUuid();

            SiteInfo currentSiteInfo = coordinator.getTargetInfo(siteId, SiteInfo.class);
            if (currentSiteInfo != null) {
                siteInfo = new SiteInfo(vdcConfigVersion, SiteInfo.RECONFIG_IPSEC, currentSiteInfo.getTargetDataRevision(), SiteInfo.ActionScope.VDC);
            } else {
                siteInfo = new SiteInfo(vdcConfigVersion, SiteInfo.RECONFIG_IPSEC, SiteInfo.ActionScope.VDC);
            }
            coordinator.setTargetInfo(siteId, siteInfo);
            log.info("VDC target version updated to {} for site {}", siteInfo.getVdcConfigVersion(), siteId);
        }

        return Long.toString(vdcConfigVersion);
    }

    /**
     * get the coordinator client
     * @return
     */
    public CoordinatorClientImpl getCoordinator() {
        return coordinator;
    }

    /**
     * set the coordinator client.
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClientImpl coordinator) {
        this.coordinator = coordinator;
        drUtil = new DrUtil(this.coordinator);
    }
}
