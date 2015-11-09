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
package com.emc.storageos.security.ipsec;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.model.ipsec.IPsecNodeState;
import com.emc.storageos.model.ipsec.IPsecStatus;
import com.emc.storageos.services.OperationTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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

        List<IPsecNodeState> nodeStatus = getAllIPsecStatusAndConfiguration();

        boolean allGood = checkAllGood(vdcConfigVersion, nodeStatus);
        IPsecStatus status = new IPsecStatus();
        status.setIsGood(allGood);
        status.setVersion(vdcConfigVersion);

        if (allGood) {
            return status;
        }

        // Send back more details if something error.
        status.setNodeStatus(nodeStatus);
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

    private List<IPsecNodeState> getAllIPsecStatusAndConfiguration() {
        List<IPsecNodeState> ipsecStatus = getLocalIPsecStatus();
        List<IPsecNodeState> standbyStatus = getSandbyIPsecStatus();

        ipsecStatus.addAll(standbyStatus);
        return ipsecStatus;
    }

    private List<IPsecNodeState> getLocalIPsecStatus() {
        return null;
    }

    public List<IPsecNodeState> getSandbyIPsecStatus() {
        return null;
    }

    private boolean checkAllGood(String vdcConfigVersion, List<IPsecNodeState> nodeStatus) {
        return false;
    }

    private String loadVdcConfigVersionFromZK() {
        return null;
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
