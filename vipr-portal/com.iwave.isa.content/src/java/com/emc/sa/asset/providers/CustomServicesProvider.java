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
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupList;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Component
@AssetNamespace("vipr")
public class CustomServicesProvider extends BaseAssetOptionsProvider {
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
    @AssetDependencies({ "allStorageSystems" })
    public List<AssetOption> getConsistencyGroupOptions(AssetOptionsContext ctx, URI storageSystemId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        final BlockConsistencyGroupList consistencyGrps = client.storageSystems().getConsistencyGroups(storageSystemId);

        final List <NamedRelatedResourceRep> consistencyGrpList = consistencyGrps.getConsistencyGroupList();
        for(NamedRelatedResourceRep consistencygrp: consistencyGrpList) {
            options.add(new AssetOption(consistencygrp.getId(), consistencygrp.getName()));
        }
        return options;
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
