/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.geo;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.security.geo.exceptions.GeoException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.db.client.model.VdcVersion;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.GlobalLockImpl;
import com.emc.storageos.db.client.model.GlobalLock;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.security.upgradevoter.UpgradeVoter;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

/**
 * Upgrade voter for geosvc. Disallow upgrade in the following cases, unless it's a
 * SP/patch/hotfix upgrade, in which case there's no expected db schema changes:
 * 1) any vdc operation in progress
 * 2) more than 2 geodb schema versions
 * 3) there are any ongoing upgrades in the federation
 * 4) there are any VDCs not in the CONNECTED state
 */
public class GeoUpgradeVoter implements UpgradeVoter{
    private static Logger log = LoggerFactory.getLogger(GeoUpgradeVoter.class);
    
    @Autowired
    private DbClient dbClient;
    
    @Autowired
    private GeoClientCacheManager geoClientCache;

    @Override
    public void isOKForUpgrade(String currentVersion, String targetVersion) {
        if (isCurrentVdcIsolated()) {
            log.info("The current VDC is isolated, skipping all pre-checks");
            return;
        }

        if (isMinorVersionUpgrade(currentVersion, targetVersion)) {
            log.info("This is a SP/patch/hotfix upgrade, skipping further pre-checks");
            return;
        }

        StringBuffer msg = new StringBuffer();
        if (isVdcOpLockHold(msg)) {
            throw GeoException.fatals.geoOperationDetected(msg.toString());
        }

        List<String> unstableVdcs = getUnstableVdcs();
        if (unstableVdcs != null && !unstableVdcs.isEmpty()) {
            throw GeoException.fatals.vdcNotStable(
                    unstableVdcs.toString());
        }

        if (hasTripleDbVersionsInFederation(targetVersion)) {
            throw GeoException.fatals.versionIsNotUpgradableInGeo(
                    targetVersion);
        }
    }

    /**
     * Check if the current VDC is isolated.
     *
     * @return True if the current VDC is isolated, false otherwise.
     */
    private boolean isCurrentVdcIsolated() {
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        return VirtualDataCenter.ConnectionStatus.ISOLATED.equals(
                localVdc.getConnectionStatus());
    }

    /**
     * Check if any vdc op lock is hold - it means some vdc op is in progress
     * 
     * @return True if a vdc op lock is hold
     * @throws Exception
     */
    private boolean isVdcOpLockHold(StringBuffer msg) {
        try {
            // it doesn't matter if it's nodesvcshared or vdcshared
            GlobalLockImpl glock = new GlobalLockImpl((DbClientImpl)dbClient,
                    GeoServiceClient.VDCOP_LOCK_NAME, GlobalLock.GL_Mode.GL_NodeSvcShared_MODE,
                    GeoServiceClient.VDCOP_LOCK_TIMEOUT, VdcUtil.getLocalShortVdcId());
            String owner = glock.getOwner();
            boolean locked = owner != null && !owner.isEmpty();
            log.info("Vdc op lock is locked {}", locked);
            if(locked && msg != null && !StringUtils.isEmpty(glock.getErrorMessage())){
                msg.append(glock.getErrorMessage());
            }
            return locked;
        } catch (Exception ex) {
            log.error("Unexpected exception during check vdc lock", ex);
            throw GeoException.fatals.accessGlobalLockFail();
        }
    }
  
    /**
     * Check if we'll have 3 geodb schema version after upgrading to give version.
     * 
     * @param targetVersion target ViPR version that current instance is going to upgrade
     * @return true if there are 3 geodb schema versions
     */
    private boolean hasTripleDbVersionsInFederation(String targetVersion) {
        Set<String> allSchemaVersions = new HashSet<>();
        allSchemaVersions.add(VdcUtil.getDbSchemaVersion(targetVersion));
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        List<URI> vdcVersionIds = dbClient.queryByType(VdcVersion.class, true);
        List<VdcVersion> vdcVersions = dbClient.queryObject(VdcVersion.class, vdcVersionIds);
        Map<URI, VdcVersion> vdcIdVdcVersionMap = new HashMap<>();
        for (VdcVersion geoVersion : vdcVersions) {
            vdcIdVdcVersionMap.put(geoVersion.getVdcId(), geoVersion);
        }
        
        for (URI vdcId : vdcIds) {
            if (vdcIdVdcVersionMap.containsKey(vdcId)) {
                String schemaVersion = vdcIdVdcVersionMap.get(vdcId).getVersion();
                log.info("Get db schema version {} on {}", schemaVersion, vdcId);
                allSchemaVersions.add(schemaVersion);
            } else {
                log.info(
                        "Can not get db schema version on {}, will use default version instead",
                        vdcId);
                allSchemaVersions.add(VdcUtil.DEFAULT_VDC_DB_VERSION);
            }
        }

        log.info("Current geodb schema versions in federation {}", allSchemaVersions);
        return allSchemaVersions.size() > 2;
    }
    
    /**
     * Check if the current upgrade is a SP/patch/hotfix upgrade.
     * @param currentVersion
     * @param targetVersion
     * @return true if the current upgrade is a SP/patch/hotfix upgrade, false otherwise
     */
    private boolean isMinorVersionUpgrade(String currentVersion, String targetVersion) {
        String currentDbSchemaVersion = VdcUtil.getDbSchemaVersion(currentVersion);
        if (currentDbSchemaVersion == null)
            return false;
        
        return currentDbSchemaVersion.equals(VdcUtil.getDbSchemaVersion(targetVersion));
    }

    /**
     * Return a list of unstable/non-connected VDCs if any.
     * Throw different exceptions accordingly if such a VDC is found.
     */
    private List<String> getUnstableVdcs() {
        List<URI> vdcIdIter = dbClient.queryByType(VirtualDataCenter.class, true);
        List<String> unstableVdcs = new ArrayList<>();
        
        for (URI vdcId : vdcIdIter) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            // Iterated each remote vdc to check stability
            if (vdc.getRepStatus().equals(VirtualDataCenter.GeoReplicationStatus.REP_ALL)
                    && !vdc.getLocal()) {
                if (! VirtualDataCenter.ConnectionStatus.CONNECTED.equals(
                        vdc.getConnectionStatus())) {
                    throw GeoException.fatals.vdcNotConnected(
                            vdc.getShortId());
                }

                try {
                    GeoServiceClient client = geoClientCache.getGeoClient(vdc.getShortId());
                    if (!client.isVdcStable()) {
                        log.info("VDC {} is not stable", vdc.getShortId());
                        unstableVdcs.add(vdc.getShortId());
                    }
                } catch (Exception ex) {
                    log.error("Unexpected exception during VDC stability check", ex);
                    throw GeoException.fatals.vdcNotReachable(
                            vdc.getShortId());
                }
            }
        }

        return unstableVdcs;
    }
}
