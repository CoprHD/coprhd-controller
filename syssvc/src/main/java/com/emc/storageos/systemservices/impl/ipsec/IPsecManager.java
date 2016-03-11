/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.ipsec;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.storageos.model.ipsec.IpsecParam;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.helpers.SecurityUtil;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.security.exceptions.SecurityException;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is to handle all ipsec related requests from web app.
 */
public class IPsecManager {

    private static final int KEY_LENGTH = 64;
    private static final char[] charsForKey =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private static final Logger log = LoggerFactory.getLogger(IPsecManager.class);
    public static final String STATUS_ENABLED = "enabled";
    public static final String STATUS_DISABLED = "disabled";
    private static final String STATUS_GOOD = "good";
    private static final String STATUS_DEGRADED = "degraded";

    @Autowired
    private IPsecConfig ipsecConfig;

    private CoordinatorClient coordinator;
    
    @Autowired
    private DrUtil drUtil;

    @Autowired
    DbClient dbClient;

    @Autowired
    private GeoClientCacheManager geoClientManager;

    /**
     * generate a 64-byte key for IPsec
     * @return
     */
    public String generateKey() throws Exception {
        return RandomStringUtils.random(KEY_LENGTH, 0, charsForKey.length-1,
                true, true, charsForKey, SecureRandom.getInstance(SecurityUtil.getSecuredRandomAlgorithm()));
    }

    /**
     * Checking ipsec status against the entire system.
     * @return
     */
    public IPsecStatus checkStatus() {
        log.info("Checking ipsec status ...");
        IPsecStatus status = new IPsecStatus();

        String vdcConfigVersion = loadVdcConfigVersionFromZK();
        status.setVersion(vdcConfigVersion);

        String ipsecStatus = ipsecConfig.getIpsecStatus();
        if (ipsecStatus != null && ipsecStatus.equals(STATUS_DISABLED)) {
            status.setStatus(ipsecStatus);
        } else {
            List<String> disconnectedNodes = checkIPsecStatus();

            if (CollectionUtils.isEmpty(disconnectedNodes)) {
                status.setStatus(STATUS_GOOD);
            } else {
                status.setStatus(STATUS_DEGRADED);
                status.setDisconnectedNodes(disconnectedNodes);
            }
        }

        return status;
    }

    /**
     * Rotate IPsec preshared key for the entired system.
     * @return
     */
    public String rotateKey(boolean enableIpsec) {
        try {
            String psk = generateKey();

            long vdcConfigVersion = DrUtil.newVdcConfigVersion();

            String ipsecStatus = null;
            if (enableIpsec) {
                ipsecStatus = STATUS_ENABLED;
            }

            // send to other VDCs if has.
            updateIPsecKeyToOtherVDCs(psk, vdcConfigVersion, ipsecStatus);

            // finally update local vdc
            if (enableIpsec) {
                ipsecConfig.setIpsecStatus(ipsecStatus);
            }
            ipsecConfig.setPreSharedKey(psk);
            updateTargetSiteInfo(vdcConfigVersion);

            log.info("IPsec Key gets rotated successfully to the version {}", vdcConfigVersion);
            return Long.toString(vdcConfigVersion);
        } catch (Exception e) {
            log.warn("Fail to rotate ipsec key.", e);
            throw SecurityException.fatals.failToRotateIPsecKey(e);
        }
    }

    /**
     * Rotate preshared key for the entired system.
     * @return
     */
    public String rotateKey() {
        return rotateKey(false);
    }

    private void updateIPsecKeyToOtherVDCs(String psk, long vdcConfigVersion, String ipsecStatus) {

        if (! drUtil.isMultivdc()) {
            log.info("This is not Geo deployment. No need to update ipsec key to other VDCs");
            return;
        }

        List<String> vdcIds = drUtil.getOtherVdcIds();
        for (String peerVdcId : vdcIds) {
            IpsecParam ipsecParam = buildIpsecParam(vdcConfigVersion, psk, ipsecStatus);
            geoClientManager.getGeoClient(peerVdcId).rotateIpsecKey(peerVdcId, ipsecParam);
        }

        log.info("Updated all the VDCs latest ipsec properties");
    }

    private IpsecParam buildIpsecParam(long vdcConfigVersion, String ipsecKey, String ipsecStatus) {
        IpsecParam param = new IpsecParam();
        param.setIpsecKey(ipsecKey);
        param.setVdcConfigVersion(vdcConfigVersion);
        param.setIpsecStatus(ipsecStatus);
        return param;
    }

    /**
     * enable/disable IPSec for the vdc
     *
     * @param status
     * @return
     */
    public String changeIpsecStatus(String status) {
        return changeIpsecStatus(status, true);
    }

    public String changeIpsecStatus(String status, boolean bChangeStatusForOtherVdcs) {
        if (status != null && (status.equalsIgnoreCase(STATUS_ENABLED) || status.equalsIgnoreCase(STATUS_DISABLED))) {
            String oldState = ipsecConfig.getIpsecStatus();
            if (status.equalsIgnoreCase(oldState)) {
                log.info("ipsec already in state: " + oldState + ", skip the operation.");
                return oldState;
            }
            log.info("changing Ipsec State from " + oldState + " to " + status);

            // in GEO env, sending request to other vdcs
            if (bChangeStatusForOtherVdcs && drUtil.isMultivdc()) {
                List<String> vdcIds = drUtil.getOtherVdcIds();
                String vdcConfigVersion = loadVdcConfigVersionFromZK();
                for (String peerVdcId : vdcIds) {
                    log.info("changing ipsec status for: " + vdcIds);
                    geoClientManager.getGeoClient(peerVdcId).changeIpsecStatus(peerVdcId,
                            status, vdcConfigVersion);
                }
            }

            ipsecConfig.setIpsecStatus(status);
        } else {
            throw APIException.badRequests.invalidIpsecStatus();
        }
        String version = updateTargetSiteInfo(DrUtil.newVdcConfigVersion());
        log.info("ipsec state changed, and new config version is {}", version);
        return status;
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

    private String updateTargetSiteInfo(long vdcConfigVersion) {
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
     * make sure cluster is in stable status
     */
    public void verifyClusterIsStable() {

        // in GEO env, check if other vdcs are stable
        if (drUtil.isMultivdc()) {
            List<String> vdcIds = drUtil.getOtherVdcIds();
            for (String peerVdcId : vdcIds) {
                if (!geoClientManager.getGeoClient(peerVdcId).isVdcStable()) {
                    log.error(vdcIds + " is not stable");
                    throw APIException.serviceUnavailable.vdcNotStable(peerVdcId);
                }
            }

            verifyOngingVdcJob();
        }

        drUtil.verifyNoOngoingJobOnSite();
        drUtil.verifyAllSitesStable();
    }

    private void verifyOngingVdcJob() {
        VdcUtil.setDbClient(dbClient);
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        VirtualDataCenter.ConnectionStatus vdcStatus = localVdc.getConnectionStatus();

        if (! vdcStatus.equals(VirtualDataCenter.ConnectionStatus.CONNECTED) &&
                ! vdcStatus.equals(VirtualDataCenter.ConnectionStatus.ISOLATED)) {
            throw APIException.serviceUnavailable.vdcOngingJob(localVdc.getShortId(), vdcStatus.name());
        }
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

    /**
     * Check if ipsec is enabled.
     * @return
     */
    public boolean isEnabled() {
        return ipsecConfig.getIpsecStatus() == null ||
                ipsecConfig.getIpsecStatus().equals(STATUS_ENABLED);
    }
}
