/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.ipsec;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.security.exceptions.SecurityException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This class is to handle all ipsec related requests from web app.
 */
public class IPsecManager {

    private static final Logger log = LoggerFactory.getLogger(IPsecManager.class);

    @Autowired
    IPsecConfig ipsecConfig;

    CoordinatorClient coordinator;
    
    @Autowired
    DrUtil drUtil;

    /**
     * Checking ipsec status against the entire system.
     * @return
     */
    public IPsecStatus checkStatus() {
        log.info("Checking ipsec status ...");

        String vdcConfigVersion = loadVdcConfigVersionFromZK();

        List<String> disconnectedNodes = checkIPsecStatus();

        IPsecStatus status = new IPsecStatus();
        status.setIsGood(disconnectedNodes == null);
        status.setVersion(vdcConfigVersion);
        if (disconnectedNodes != null) {
            status.setDisconnectedNodes(disconnectedNodes);
        }

        return status;
    }

    /**
     * Rotate IPsec preshared key for the entired system.
     * @return
     */
    public String rotateKey() {
        String psk = ipsecConfig.generateKey();
        try {
            ipsecConfig.setPreSharedKey(psk);
            String version = updateTargetSiteInfo();
            log.info("IPsec Key gets rotated successfully to the version {}", version);
            return version;
        } catch (Exception e) {
            log.warn("Fail to rotate ipsec key due to: {}", e);
            throw SecurityException.fatals.failToRotateIPsecKey(e);
        }
    }

    public boolean isKeyRotationDoneWithinLocalSite(long targetKeyVersion) throws Exception {
        log.info("Checking ipsec key rotation is done ...");
        Site localSite = drUtil.getLocalSite();
        Map<String, String> ips = localSite.getHostIPv4AddressMap(); // TODO: should consider ipv6
        for (String ip : ips.values()) {
            String nodeVersion = ipsecConfig.getKeyVersionByNode(ip);
            if (StringUtils.isEmpty(nodeVersion)) {
                log.info("The node {} is not done", ip);
                return false;
            }

            if (!nodeVersion.equals(targetKeyVersion)) {
                log.info("The node {} is not done. Target version is {}, The local version is {}", new Object[] {ip, targetKeyVersion, nodeVersion});
                return false;
            }

            log.info("The node {} is done", ip);
        }

        return true;
    }

    public boolean isKeyRotationDone() throws Exception {
        return CollectionUtils.isEmpty(checkIPsecStatus());
    }

    private List<String> checkIPsecStatus() {
        LocalRepository localRepository = new LocalRepository();
        String[] disconnectedIPs = localRepository.checkIpsecConnection();
        if (disconnectedIPs[0].isEmpty()) {
            log.info("IPsec runtime status is good.");
            return new ArrayList<String>(); // return empty list to avoid null pointer in java client.
        } else {
            log.info("Some nodes disconnected over IPsec {}", disconnectedIPs);
            return Arrays.asList(disconnectedIPs);
        }
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
                siteInfo = new SiteInfo(vdcConfigVersion, SiteInfo.IPSEC_OP_ROTATE_KEY, currentSiteInfo.getTargetDataRevision(), SiteInfo.ActionScope.VDC);
            } else {
                siteInfo = new SiteInfo(vdcConfigVersion, SiteInfo.IPSEC_OP_ROTATE_KEY, SiteInfo.ActionScope.VDC);
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
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    /**
     * set the coordinator client.
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }
}
