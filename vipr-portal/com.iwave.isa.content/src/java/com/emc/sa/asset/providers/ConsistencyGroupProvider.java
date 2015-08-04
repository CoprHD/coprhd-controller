/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.vipr.client.core.filters.ConsistencyGroupFilter;
import com.emc.vipr.model.catalog.AssetOption;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@Component
@AssetNamespace("vipr")
public class ConsistencyGroupProvider extends BaseAssetOptionsProvider {

    @Asset("consistencyGroup")
    @AssetDependencies({ "project", "blockVirtualPool" })
    public List<AssetOption> getConsistencyGroups(AssetOptionsContext ctx, URI projectId, URI virtualPoolId) {
        BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);

        // Only provide consistency groups if the selected VPool supports it
        if (vpool != null && vpool.getMultiVolumeConsistent() != null && vpool.getMultiVolumeConsistent()) {
            return createBaseResourceOptions(api(ctx).blockConsistencyGroups().search().byProject(projectId).run());
        } else {
            return Collections.emptyList();
        }
    }

    @Asset("consistencyGroupWithVirtualPoolChangeOperation")
    @AssetDependencies({ "project", "virtualPoolChangeOperation", "targetVirtualPool" })
    public List<AssetOption> getConsistencyGroupsChangeVPool(AssetOptionsContext ctx, URI projectId,
            String virtualPoolChangeOperation, URI virtualPoolId) {
        // Only support for RP Protection for now
        if (virtualPoolChangeOperation.equals(VirtualPoolChangeOperationEnum.RP_PROTECTED.name())) {
            BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);
            // Only provide consistency groups if the selected VPool supports it
            if (vpool != null && vpool.getMultiVolumeConsistent() != null && vpool.getMultiVolumeConsistent()) {
                return createBaseResourceOptions(api(ctx).blockConsistencyGroups().search().byProject(projectId)
                        .filter(new ConsistencyGroupFilter(BlockConsistencyGroup.Types.RP.toString(), true)).run());
            }
        }
        return Collections.emptyList();
    }
}
