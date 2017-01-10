/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.model.catalog.AssetOption;

@Component
@AssetNamespace("vipr")
public class RemoteReplicationSetProvider extends BaseAssetOptionsProvider {

    @Asset("remoteReplicationSet")
    @AssetDependencies({ "project", "blockVirtualPool" })
    public List<AssetOption> getRemoteReplicationSets(AssetOptionsContext ctx, URI projectId, URI virtualPoolId) {
        BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);

        // Only provide remote Replication Sets if the selected VPool supports it
        if (isSupportedVPool(vpool)) {
            return createBaseResourceOptions(api(ctx).remoteReplicationSets().search().byProject(projectId).run());
        } else {
            return Collections.emptyList();
        }
    }

    @Asset("remoteReplicationSet")
    @AssetDependencies({ "project" })
    public List<AssetOption> getRemoteReplicationSets(AssetOptionsContext ctx, URI projectId) {
        return createBaseResourceOptions(api(ctx).remoteReplicationSets().search().byProject(projectId).run());
    }

    private boolean isSupportedVPool(BlockVirtualPoolRestRep vpool) {
        return vpool != null && vpool.getProtection().getRemoteReplicationParam() != null;
    }

}
