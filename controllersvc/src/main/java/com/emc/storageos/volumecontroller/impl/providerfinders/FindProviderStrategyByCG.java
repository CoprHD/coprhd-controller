/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.providerfinders;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.google.common.collect.Collections2;

/**
 * FindProviderStrategyByCG returns 
 *      storage system (as is) if CG is found on its active provider,
 *      or storage system (updated with provider info) if CG is found on its passive provider,
 *      or null (if CG is not found on any of its provider).
 */
public class FindProviderStrategyByCG implements FindProviderStrategy {

    private static final Logger log = LoggerFactory.getLogger(FindProviderStrategyByCG.class);

    private DbClient dbClient;
    private StorageSystem system;
    private String groupName;
    private SmisCommandHelper helper;
    private CIMObjectPathFactory cimPathFactory;

    public FindProviderStrategyByCG(DbClient dbClient, StorageSystem system, String groupName,
            SmisCommandHelper helper, CIMObjectPathFactory cimPathFactory) {
        this.dbClient = dbClient;
        this.system = system;
        this.groupName = groupName;
        this.helper = helper;
        this.cimPathFactory = cimPathFactory;
    }

    @Override
    public StorageSystem find() {

        // first check if active Provider has CG.
        // Do not unnecessarily check on passive providers when active provider has it.
        CIMObjectPath cimPath = cimPathFactory.getReplicationGroupPath(system, groupName);
        if (null == cimPath) {
            log.warn("Replication Group {} not found in Provider {}", groupName, system.getActiveProviderURI());
        } else {
            return system;
        }

        Set<URI> providerUris = new HashSet<URI>();
        if (null != system.getProviders()) {
            providerUris.addAll(Collections2.transform(system.getProviders(),
                    CommonTransformerFunctions.FCTN_STRING_TO_URI));
        }

        /**
         * Check which of the passive SMI-S provider has CG available on it.
         * This may be required when the active Provider for a system changes.
         * 
         * Get all providers managing this system
         * for each provider (except active provider)
         *      get 1 storage system which is actively managed by this provider
         *      (list should contain unique systems (except this.system))
         * look for the group name for this system
         */

        List<String> storageSystemUriStrs = new ArrayList<String>();
        List<StorageSystem> passiveProviderSystems = new ArrayList<StorageSystem>();
        // add source system so that we will take other system reference on passive provider
        storageSystemUriStrs.add(system.getId().toString());
        providerUris.remove(system.getActiveProviderURI());

        if (!providerUris.isEmpty()) {
            List<StorageProvider> passiveSmisProviderList = dbClient.queryObject(StorageProvider.class, providerUris);
            for (StorageProvider provider : passiveSmisProviderList) {
                if (null != provider.getStorageSystems()) {
                    for (String systemUriStr : provider.getStorageSystems()) {
                        if (!storageSystemUriStrs.contains(systemUriStr)) {
                            StorageSystem passiveProviderSystem = dbClient.queryObject(StorageSystem.class, URI.create(systemUriStr));
                            if (provider.getId().toString().equalsIgnoreCase(passiveProviderSystem.getActiveProviderURI().toString())) {
                                storageSystemUriStrs.add(systemUriStr);
                                passiveProviderSystems.add(passiveProviderSystem);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Each Storage System in turn refers to a Provider, we loop through each Storage System
        // check whether CG is found.
        StorageSystem systemWithCGFound = null;
        for (StorageSystem passiveProviderSystem : passiveProviderSystems) {
            cimPath = cimPathFactory.getReplicationGroupPath(passiveProviderSystem, system.getSerialNumber(), groupName);
            if (null == cimPath) {
                log.warn("Replication Group {} not found in Provider {}", groupName, passiveProviderSystem.getActiveProviderURI());
                continue;
            }
            systemWithCGFound = passiveProviderSystem;
            break;
        }

        // make this passive provider with CG active for this system
        //   Scanner process does not change active provider as long as the previous one is UP.
        //   So, there won't be conflict if we update it here.
        if (systemWithCGFound != null) {
            URI providerURI = systemWithCGFound.getActiveProviderURI();
            log.info("Passive provider {} with Replication Group found. Making it active for system {}",
                    providerURI, system.getId());
            StorageProvider provider = dbClient.queryObject(StorageProvider.class, providerURI);
            system.setActiveProviderURI(providerURI);
            system.setSmisPassword(provider.getPassword());
            system.setSmisPortNumber(provider.getPortNumber());
            system.setSmisProviderIP(provider.getIPAddress());
            system.setSmisUserName(provider.getUserName());
            system.setSmisUseSSL(provider.getUseSSL());
            dbClient.persistObject(system);
            log.info("Active provider for system {} has changed to {}.",
                    system.getId(), system.getActiveProviderURI());

            // refresh SMI-S provider so that the current active provider (P1) is aware of changes made by previous active provider (P2).
            // (For instance, addVolume to CG - Volume created in P2 needs to be available in P1)
            try {
                helper.callRefreshSystem(system, null);
            } catch (WBEMException e) {
                log.error("EMCRefresh against StorageSystem {} failed.", system.getNativeGuid(), e);
            }

            return system;
        }
        return null;
    }
}
