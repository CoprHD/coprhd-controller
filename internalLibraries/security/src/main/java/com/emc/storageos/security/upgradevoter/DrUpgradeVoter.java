/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.upgradevoter;

import java.util.List;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.svcs.errorhandling.resources.APIException;


/**
 * Upgrade voter for disaster recovery. Disallow upgrade in the following cases:
 * 1) any dr operation in progress
 * 2) there are no sites in STANDBY_PAUSED state
 */
public class DrUpgradeVoter implements UpgradeVoter {
    private static Logger log = LoggerFactory.getLogger(DrUpgradeVoter.class);

    private DrUtil drUtil;

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    @Override
    public void isOKForUpgrade(String currentVersion, String targetVersion) {
        List<Site> standbySites = drUtil.listStandbySites();
        if (standbySites.isEmpty()) {
            log.info("Not a DR configuration, skipping all DR pre-checks");
            return;
        }

        InterProcessLock lock = null;
        try {
            lock = drUtil.getDROperationLock();
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    log.error("Failed to release the DR lock", e);
                }
            }
        }

        List<Site> pausedSites = drUtil.listSitesInState(SiteState.STANDBY_PAUSED);
        if (pausedSites.isEmpty()) {
            log.error("There's no paused standby site for DR Upgrade");
            throw APIException.internalServerErrors.upgradeNotAllowedWithoutPausedSite();
        }
    }
}
