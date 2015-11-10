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

public class IPsecManager {

    private static final Logger _log = LoggerFactory.getLogger(IPsecManager.class);

    @Autowired
    IPsecConfig ipsecConfig;

    @Autowired
    IPsecKeyGenerator ipsecKeyGenerator;

    CoordinatorClientImpl coordinator;
    DrUtil drUtil;

    public IPsecStatus checkStatus() {

        String vdcConfigVersion = loadVdcConfigVersionFromZK();

        boolean runtimeGood = checkRunTimeStatus();

        List<IPsecNodeState> nodeStatus = getIPsecVersionsOnAllNodes();

        List<IPsecNodeState> problemNodeStatus = checkConfigurations(vdcConfigVersion, nodeStatus);
        boolean configGood = problemNodeStatus.isEmpty();

        IPsecStatus status = new IPsecStatus();

        boolean allGood = runtimeGood & configGood;

        status.setIsGood(allGood);
        status.setVersion(vdcConfigVersion);
        if (allGood) {
            return status;
        }

        // Send back more details if something error.
        status.setNodeStatus(problemNodeStatus);
        return status;
    }

    public String rotateKey() {
        String psk = ipsecKeyGenerator.generate();
        try {
            ipsecConfig.setPreSharedKey(psk);
            String version = updateTargetSiteInfo();
            _log.info("IPsec Key gets rotated successfully to the version {}", version);
            return version;
        } catch (Exception e) {
            throw com.emc.storageos.security.exceptions.SecurityException.fatals.failToRotateIPsecKey(e);
        }
    }

    private List<IPsecNodeState> checkConfigurations(String vdcConfigVersion, List<IPsecNodeState> nodeStatus) {
        List<IPsecNodeState> problemNodeStatus = new ArrayList<>();

        for (IPsecNodeState node : nodeStatus) {
            if (! node.getVersion().equals(vdcConfigVersion)) {
                problemNodeStatus.add(node);
            }
        }

        return problemNodeStatus;
    }

    private boolean checkRunTimeStatus() {
        LocalRepository localRepository = new LocalRepository();
        String[] problemIPs = localRepository.checkIpsecConnection();

        return (problemIPs == null || problemIPs.length == 0) ? true : false;
    }

    private List<IPsecNodeState> getIPsecVersionsOnAllNodes() {
        List<IPsecNodeState> nodeStatus = new ArrayList<>();

        LocalRepository localRepository = new LocalRepository();

        for (Site site : drUtil.listSites()) {
            for (String ip : site.getHostIPv4AddressMap().values()) {
                IPsecNodeState nodeState = new IPsecNodeState();
                nodeState.setIp(ip);
                try {
                    Map<String, String> ipsecProps = localRepository.getIpsecProperties(ip);
                    nodeState.setVersion(ipsecProps.get("version"));
                } catch (Exception e) {
                    nodeState.setVersion(null);
                }
            }
        }

        return nodeStatus;
    }

    private String loadVdcConfigVersionFromZK() {
        return Long.toString(coordinator.getTargetInfo(SiteInfo.class).getVdcConfigVersion());
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
            _log.info("VDC target version updated to {} for site {}", siteInfo.getVdcConfigVersion(), siteId);
        }

        return Long.toString(vdcConfigVersion);
    }

    public CoordinatorClientImpl getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClientImpl coordinator) {
        this.coordinator = coordinator;
        drUtil = new DrUtil(this.coordinator);
    }
}
