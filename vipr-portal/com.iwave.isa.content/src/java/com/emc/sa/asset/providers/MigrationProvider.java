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
import com.emc.storageos.model.NamedRelatedResourceRep;
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
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;

@Component
@AssetNamespace("vipr")
public class MigrationProvider extends BaseAssetOptionsProvider {
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

        final BlockConsistencyGroupList consistencyGrps = client.storageSystems().getConsistencyGroups(storageSystemId);
        final List <NamedRelatedResourceRep> consistencyGrpList = consistencyGrps.getConsistencyGroupList();
        for(NamedRelatedResourceRep consistencygrp: consistencyGrpList) {
            if (filterBCG(client, consistencygrp.getId(), storageType, hostId)) {
                options.add(new AssetOption(consistencygrp.getId(), consistencygrp.getName()));
            }
        }
        return options;
    }

    private boolean filterBCG(ViPRCoreClient client, URI bcgId, String storageType, URI hostId) {
        if (storageType.equals(BlockProvider.EXCLUSIVE_STORAGE)) {
            return matchBCGWithHost(client, bcgId, hostId);
        } else { // Shared
            return matchBCGWithCluster(client, hostId, bcgId);
        }
    }

    private boolean matchBCGWithCluster(ViPRCoreClient client, URI clusterId, URI bcgId) {
        getLog().info("========== matching cg with cluster " + clusterId);
        /*  Requirements:
            1. All initiators in bcg should match to any one of a host of the cluster. If any one is not in cluster, return false.
            2. All hosts in cluster should have at least one initiator appearing in BCG.
         */
        Set<URI> matchedHosts = new HashSet<>();
        Map<URI, URI> initToHost = new HashMap<>();
        List<HostRestRep> hosts = client.hosts().getByCluster(clusterId);
        getLog().info("========== hosts size " + hosts.size());
        for (HostRestRep host: hosts) {
            List<InitiatorRestRep> initsByHost = client.initiators().getByHost(host.getId());
            for (InitiatorRestRep init: initsByHost) {
                initToHost.put(init.getId(), host.getId());
            }
        }

        InitiatorList bcgInitiators = client.blockConsistencyGroups().getInitiators(bcgId);
        for (NamedRelatedResourceRep bcgInit: bcgInitiators.getInitiators()) {
            if (!initToHost.containsKey(bcgInit.getId())) return false;
            matchedHosts.add(initToHost.get(bcgInit.getId()));
        }
        getLog().info("========== matched host size " + matchedHosts.size());

        return matchedHosts.size() == hosts.size();
    }

    private boolean matchBCGWithHost(ViPRCoreClient client, URI bcg, URI hostId) {
        getLog().info("========== matching cg " + bcg);
        List<InitiatorRestRep> hostInitiators = client.initiators().getByHost(hostId);
        InitiatorList bcgInitiators = client.blockConsistencyGroups().getInitiators(bcg);
        if (bcgInitiators.getInitiators().isEmpty()) return false;

        Set<URI> hostInitiatorSet = new HashSet<>();
        for (InitiatorRestRep initiator: hostInitiators) {
            getLog().info("host init is  " + initiator.getId());
            hostInitiatorSet.add(initiator.getId());
        }
        for (NamedRelatedResourceRep bcgInitiator: bcgInitiators.getInitiators()) {
            getLog().info("bcg init is  " + bcgInitiator.getId());
            if (!hostInitiatorSet.contains(bcgInitiator.getId())) return false;
        }
        getLog().info("========== cg " + bcg + " matched. Init # is " + bcgInitiators.getInitiators().size());
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