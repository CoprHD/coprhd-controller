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
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.vipr.client.core.RemoteReplicationSets;
import com.emc.vipr.model.catalog.AssetOption;

@Component
@AssetNamespace("vipr")
public class RemoteReplicationProvider extends BaseAssetOptionsProvider {

    @Asset("remoteReplicationMode")
    @AssetDependencies({ "project", "remoteReplicationSet" })
    public List<AssetOption>
            getRemoteReplicationModes(AssetOptionsContext ctx, URI projectId, URI remoteReplicationSetID) {

        RemoteReplicationSetRestRep remoteReplicationSet = api(ctx).remoteReplicationSets().getRemoteReplicationSetsRestRep(
                remoteReplicationSetID.toString());
        if (remoteReplicationSet != null)
            return createStringOptions(remoteReplicationSet.getSupportedReplicationModes());
        else {
            return Collections.emptyList();
        }
    }

    @Asset("remoteReplicationGroup")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool" })
    public List<AssetOption> getRemoteReplicationGroups(AssetOptionsContext ctx,
            URI virtualArrayId, URI virtualPoolId) throws Exception {
        RemoteReplicationSets setsForPoolVarray = api(ctx).remoteReplicationSets();

        List<NamedRelatedResourceRep> rrSets = setsForPoolVarray.
                listRemoteReplicationSets(virtualArrayId,virtualPoolId).getRemoteReplicationSets();

        if ((rrSets == null) || rrSets.isEmpty()) {
            return Collections.emptyList();
        }

        if (rrSets.size() > 1) {
            throw new IllegalStateException("Invalid number of RemoteReplicationSets (" + rrSets.size() +
                    ") found for VirtualArray (" + virtualArrayId + ") and VirtualPool (" + virtualPoolId +
                    ")  RemoteReplicationSets found were: " + rrSets);
        }

        return createNamedResourceOptions(setsForPoolVarray.
                getGroupsForSet(rrSets.get(0).getId()).getRemoteReplicationGroups());
    }
}
