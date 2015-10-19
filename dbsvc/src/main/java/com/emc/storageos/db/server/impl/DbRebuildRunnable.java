/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import org.apache.cassandra.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;

/**
 * Thread to rebuild the local db node if the local site is in STANDBY_SYNCING state.
 * Update the local site state to STANDBY_SYNCED when finishes.
 */
public class DbRebuildRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DbRebuildRunnable.class);

    private CoordinatorClient coordinator;
    private String sourceDc;

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setSourceDc(String sourceDc) {
        this.sourceDc = sourceDc;
    }

    @Override
    public synchronized void run() {
        Configuration localSiteConfig = coordinator.queryConfiguration(Site.CONFIG_KIND, coordinator.getSiteId());
        Site localSite = new Site(localSiteConfig);
        if (localSite.getState().equals(SiteState.STANDBY_SYNCING)) {
            log.info("starting db rebuild from source dc {}", sourceDc);
            StorageService.instance.rebuild(sourceDc);

            //FIXME: shouldn't update the site state until each node has finished rebuilding both db and geodb
            log.info("db rebuild finishes, updating site state to STANDBY_SYNCED");
            localSite.setState(SiteState.STANDBY_SYNCED);
            coordinator.persistServiceConfiguration(localSite.toConfiguration());
        } else {
            log.info("db in sync, nothing to do");
        }
    }
}
