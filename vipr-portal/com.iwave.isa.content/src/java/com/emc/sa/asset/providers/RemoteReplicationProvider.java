/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
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
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.core.RemoteReplicationSets;
import com.emc.vipr.model.catalog.AssetOption;

@Component
@AssetNamespace("vipr")
public class RemoteReplicationProvider extends BaseAssetOptionsProvider {

    private RemoteReplicationSets setsForPoolVarray;

    @Asset("remoteReplicationMode")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool" })
    public List<AssetOption>
    getRemoteReplicationModes(AssetOptionsContext ctx, URI virtualArrayId, URI virtualPoolId) {

        NamedRelatedResourceRep rrSet = getRrSet(ctx,virtualArrayId, virtualPoolId);
        if(rrSet == null) {
            return Collections.emptyList();
        }

        RemoteReplicationSetRestRep rrSetObj = api(ctx).remoteReplicationSets().
                getRemoteReplicationSetsRestRep(rrSet.getId().toString());

        List<AssetOption> options = new ArrayList<>();
        for(String mode : rrSetObj.getSupportedReplicationModes()) {
            options.add(new AssetOption(mode,mode));
        }
        return options;
    }

    @Asset("remoteReplicationGroup")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool" })
    public List<AssetOption> getRemoteReplicationGroups(AssetOptionsContext ctx,
            URI virtualArrayId, URI virtualPoolId) throws Exception {

        NamedRelatedResourceRep rrSet = getRrSet(ctx,virtualArrayId, virtualPoolId);
        if(rrSet == null) {
            return Collections.emptyList();
        }

        return createNamedResourceOptions(setsForPoolVarray.
                getGroupsForSet(rrSet.getId()).getRemoteReplicationGroups());
    }

    private NamedRelatedResourceRep getRrSet(AssetOptionsContext ctx,URI virtualArrayId, URI virtualPoolId) {

        if(setsForPoolVarray == null) {
            setsForPoolVarray = api(ctx).remoteReplicationSets();
        }

        BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);
        if ((vpool == null) || (vpool.getProtection().getRemoteReplicationParam() == null)) {
            return null;
        }

        List<NamedRelatedResourceRep> rrSets = setsForPoolVarray.
                listRemoteReplicationSets(virtualArrayId,virtualPoolId).getRemoteReplicationSets();

        if ((rrSets == null) || rrSets.isEmpty()) {
            return null;
        }

        if (rrSets.size() > 1) {
            throw new IllegalStateException("Invalid number of RemoteReplicationSets (" + rrSets.size() +
                    ") found for VirtualArray (" + virtualArrayId + ") and VirtualPool (" + virtualPoolId +
                    ").  RemoteReplicationSets found were: " + rrSets);
        }

        return rrSets.get(0);
    }
}
