/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.sa.asset.providers;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorList;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupList;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;

@Component
@AssetNamespace("vipr")
public class MigrationProvider extends BaseAssetOptionsProvider {
    private static final Logger log = LoggerFactory.getLogger(MigrationProvider.class);

    private final static String SRDF_TYPE = "SRDF";

    @Asset("allStorageSystems")
    public List<AssetOption> getStorageSystemOptions(AssetOptionsContext ctx) {
        final ViPRCoreClient client = api(ctx);
        return createBaseResourceOptions(client.storageSystems().getAll());
    }

    @Asset("targetStorageSystems")
    @AssetDependencies({ "allStorageSystems" })
    public List<AssetOption> getTargetStorageSystemOptions(AssetOptionsContext ctx, URI storageSystemId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        final List<StorageSystemConnectivityRestRep> connectedStorageList = client.storageSystems().getConnectivity(storageSystemId);

        for(StorageSystemConnectivityRestRep connectedsystem: connectedStorageList) {
            Set<String> types = connectedsystem.getConnectionTypes();
            boolean isSrdfType = false;
            for(String type : types) {
                if(StringUtils.equals(type, SRDF_TYPE)) {
                    isSrdfType = true;
                }
            }
            if(isSrdfType) {
                options.add(new AssetOption(connectedsystem.getProtectionSystem().getId(), connectedsystem.getProtectionSystem().getName()));
            }
        }
        return options;
    }

    @Asset("storageConsistencyGroup")
    @AssetDependencies({ "blockStorageType","host","allStorageSystems" })
    public List<AssetOption> getConsistencyGroupOptions(AssetOptionsContext ctx, String storageType, URI hostId, URI storageSystemId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        log.info("Getting storage groups based on storageType {}, host {}, storage system {}", storageType, hostId, storageSystemId);
        final BlockConsistencyGroupList consistencyGrps = client.storageSystems().getConsistencyGroups(storageSystemId);
        log.info("Total # of storage groups is {}", consistencyGrps.getConsistencyGroupList().size());
        final List <NamedRelatedResourceRep> consistencyGrpList = consistencyGrps.getConsistencyGroupList();
        for(NamedRelatedResourceRep consistencygrp: consistencyGrpList) {
            if (filterBCG(client, consistencygrp.getId(), storageType, hostId)) {
                options.add(new AssetOption(consistencygrp.getId(), consistencygrp.getName()));
            }
        }
        log.info("Filtered storage group size is {}", options.size());
        return options;
    }

    private boolean filterBCG(ViPRCoreClient client, URI bcgId, String storageType, URI hostId) {
        BlockConsistencyGroupRestRep bcg = client.blockConsistencyGroups().get(bcgId);
        if ( !bcg.getTypes().contains(BlockConsistencyGroup.Types.MIGRATION.name()) ||
                BlockConsistencyGroup.MigrationStatus.None.name().equalsIgnoreCase(bcg.getMigrationStatus()) ) {
            log.info("BCG [ id: {}, type: {}, status: {} ] is filtered out",
                    bcgId, bcg.getTypes(), bcg.getMigrationStatus());
            return false;
        }
        if (storageType.equals(BlockProvider.EXCLUSIVE_STORAGE)) {
            return matchBCGWithHost(client, bcg, hostId);
        } else { // Shared
            return matchBCGWithCluster(client, bcg, hostId);
        }
    }

    private boolean matchBCGWithCluster(ViPRCoreClient client, BlockConsistencyGroupRestRep bcg, URI clusterId) {
        /*  Requirements:
            1. All initiators in bcg should match to any one of a host of the cluster. If any one is not in cluster, return false.
            2. All hosts in cluster should have at least one initiator appearing in BCG.
         */
        Set<URI> matchedHosts = new HashSet<>();
        Map<URI, URI> initToHost = new HashMap<>();
        List<HostRestRep> hosts = client.hosts().getByCluster(clusterId);
        for (HostRestRep host: hosts) {
            List<InitiatorRestRep> initsByHost = client.initiators().getByHost(host.getId());
            for (InitiatorRestRep init: initsByHost) {
                initToHost.put(init.getId(), host.getId());
            }
        }

        for (NamedRelatedResourceRep bcgInit: bcg.getInitiators()) {
            if (!initToHost.containsKey(bcgInit.getId())) {
                log.info("BCG {} has an initiator [ {}, {} ] which doesn't belong to any host", bcg.getId(), bcgInit.getId(), bcgInit.getName());
                return false;
            }
            matchedHosts.add(initToHost.get(bcgInit.getId()));
        }

        return matchedHosts.size() == hosts.size();
    }

    private boolean matchBCGWithHost(ViPRCoreClient client, BlockConsistencyGroupRestRep bcg, URI hostId) {
        List<InitiatorRestRep> hostInitiators = client.initiators().getByHost(hostId);
        if (bcg.getInitiators().isEmpty()) return false;

        Set<URI> hostInitiatorSet = new HashSet<>();
        for (InitiatorRestRep initiator: hostInitiators) {
            getLog().info("host init is  " + initiator.getId());
            hostInitiatorSet.add(initiator.getId());
        }
        for (NamedRelatedResourceRep bcgInitiator: bcg.getInitiators()) {
            getLog().info("bcg init is  " + bcgInitiator.getId());
            if (!hostInitiatorSet.contains(bcgInitiator.getId())) return false;
        }
        return true;
    }

    @Asset("targetSystemStoragePorts")
    @AssetDependencies({ "targetStorageSystems" })
    public List<AssetOption> getStoragePortsOptions(AssetOptionsContext ctx, URI storageSystemId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        final StoragePortList storagePortsList = client.storageSystems().getStoragePorts(storageSystemId);
        final List<NamedRelatedResourceRep> portList = storagePortsList.getPorts();
        for(NamedRelatedResourceRep port: portList) {
            options.add(new AssetOption(port.getId(), port.getName()));
        }
        return options;
    }

}