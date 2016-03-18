/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import java.util.List;

import org.apache.cassandra.service.StorageService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Thread to rebuild the local db node if the local site is in STANDBY_SYNCING state.
 * Update the local site state to STANDBY_SYNCED when db rebuild finishes for both db and geodb on all nodes.
 */
public class DbRebuildRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DbRebuildRunnable.class);

    private CoordinatorClient coordinator;
    private int nodeCount;
    private Service service;

    private volatile boolean isRunning;

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public void setService(Service service) {
        this.service = service;
    }

    @Override
    public void run() {
        if (isRunning) {
            log.info("db rebuild in progress, nothing to do");
            return;
        }

        DrUtil drUtil = new DrUtil(coordinator);
        Site localSite = drUtil.getLocalSite();
        if (!localSite.getState().equals(SiteState.STANDBY_SYNCING)) {
            log.info("db in sync, nothing to do");
            return;
        }

        Configuration dbconfig = coordinator.queryConfiguration(coordinator.getSiteId(),
                coordinator.getVersionedDbConfigPath(service.getName(), service.getVersion()), service.getId());
        if (isLastDataSyncCurrent(dbconfig)) {
            log.info("last data sync time is later than the target site info update, nothing to do");
            return;
        }
        
        Site primarySite = drUtil.getActiveSite();
        String sourceDc = drUtil.getCassandraDcId(primarySite);

        log.info("starting db rebuild from source dc {}", sourceDc);
        isRunning = true;
        StorageService.instance.rebuild(sourceDc);

        long currentSyncTime = System.currentTimeMillis();
        log.info("local db rebuild finishes. Updating last data sync time to {}", currentSyncTime);
        dbconfig.setConfig(DbConfigConstants.LAST_DATA_SYNC_TIME, String.valueOf(currentSyncTime));
        coordinator.persistServiceConfiguration(coordinator.getSiteId(), dbconfig);

        if (dbRebuildComplete(Constants.DBSVC_NAME) && dbRebuildComplete(Constants.GEODBSVC_NAME)) {
            localSite = drUtil.getLocalSite();
            // update the site state only if it's still STANDBY_SYNCING
            // do nothing if it gets set to STANDBY_ERROR earlier
            if (localSite.getState().equals(SiteState.STANDBY_SYNCING)) {
                purgeOldDataRevision(drUtil);
                log.info("all db rebuild finish, updating site state to STANDBY_SYNCED");
                localSite.setState(SiteState.STANDBY_SYNCED);
                coordinator.persistServiceConfiguration(localSite.toConfiguration());
            }
        }
        isRunning = false;
    }

    private void purgeOldDataRevision(DrUtil drUtil) {
        try {
            coordinator.startTransaction();
            log.info("Purge old data revisions after resync data from active site");
            long vdcTargetVersion = DrUtil.newVdcConfigVersion();
            String localSiteId = drUtil.getLocalSite().getUuid();
            for (Site site : drUtil.listSites()) {
                drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.DR_OP_PURGE_DATA_REVISION, vdcTargetVersion, localSiteId, localSiteId);
            }
            coordinator.commitTransaction();
        } catch (Exception e) {
            log.warn("Failed to purge old data revision on local" , e);
            coordinator.discardTransaction();
        }
    }
    private boolean dbRebuildComplete(String svcName) {
        List<Configuration> configs = coordinator.queryAllConfiguration(coordinator.getSiteId(),
                coordinator.getVersionedDbConfigPath(svcName, service.getVersion()));
        int count = 0;
        for (Configuration config : configs) {
            if (isLastDataSyncCurrent(config)) {
                count++;
            }
        }
        return (count == nodeCount);
    }

    // check if the last data sync time is later than the target site info update
    private boolean isLastDataSyncCurrent(Configuration dbConfig) {
        SiteInfo targetSiteInfo = coordinator.getTargetInfo(SiteInfo.class);
        String value = dbConfig.getConfig(DbConfigConstants.LAST_DATA_SYNC_TIME);
        return value != null && Long.valueOf(value) > targetSiteInfo.getVdcConfigVersion();
    }
}
