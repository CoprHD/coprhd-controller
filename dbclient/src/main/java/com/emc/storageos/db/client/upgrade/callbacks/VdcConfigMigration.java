/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Cleanup db config from pre-yoda release
 */
public class VdcConfigMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(
            VdcConfigMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        cleanupStaleDbConfig();
        migrateVdcConfigToZk();
    }
    
    /**
     * We store dbconfig/geodbconfig in under /config znode in pre-yoda versions. Since yoda, we move it 
     * to site specific area /site/<site uuid>/config. So after yoda upgrade is done, remove those stale config
     * entries in zk
     */
    private void cleanupStaleDbConfig() {
        coordinatorClient.deletePath(String.format("%s/%s", ZkPath.CONFIG, Constants.DB_CONFIG));
        coordinatorClient.deletePath(String.format("%s/%s", ZkPath.CONFIG, Constants.GEODB_CONFIG));
        log.info("Removed dbconfig and geodbconfig in zk global area successfully");
    }
    
    /**
     * We store vdc ip addresses in local db(VirtualDataCenter CF) in pre-yoda. Since yoda, we move it
     * to zookeeper. We move all vdcs config to zk
     */
    private void migrateVdcConfigToZk() {
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        for(URI vdcId : vdcIds) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            if (vdc.getLocal()) {
                continue;
            }
            // Insert vdc info
            ConfigurationImpl vdcConfig = new ConfigurationImpl();
            vdcConfig.setKind(Site.CONFIG_KIND);
            vdcConfig.setId(vdc.getShortId());
            coordinatorClient.persistServiceConfiguration(vdcConfig);
            
            // insert DR active site info to ZK
            Site site = new Site();
            // TODO - we have no way to know site uuid in remote vdc during upgrade
            // no harm for now. We don't care site uuid in remote vdc at all 
            site.setUuid(UUID.randomUUID().toString()); 
            site.setName("Default Active Site");
            site.setVdcShortId(vdc.getShortId());
            site.setSiteShortId(Constants.CONFIG_DR_FIRST_SITE_SHORT_ID);
            site.setHostIPv4AddressMap(vdc.getHostIPv4AddressesMap());
            site.setHostIPv6AddressMap(vdc.getHostIPv6AddressesMap());
            site.setState(SiteState.ACTIVE);
            site.setCreationTime(System.currentTimeMillis());
            site.setVip(vdc.getApiEndpoint());
            site.setNodeCount(vdc.getHostCount());
            
            coordinatorClient.persistServiceConfiguration(site.toConfiguration());
            
            // update Site version in ZK
            SiteInfo siteInfo = new SiteInfo(System.currentTimeMillis(), SiteInfo.NONE);
            coordinatorClient.setTargetInfo(siteInfo);
        }
        log.info("Migrated vdc config from db to zk");
    }
}
