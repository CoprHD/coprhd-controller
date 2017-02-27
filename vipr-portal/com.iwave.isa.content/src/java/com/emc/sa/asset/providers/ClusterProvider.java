/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class ClusterProvider extends BaseAssetOptionsProvider {

    protected List<ClusterRestRep> getClusters(AssetOptionsContext context) {
        debug("getting clusters");
        return api(context).clusters().getByTenant(context.getTenant());
    }

    @Asset("cluster")
    public List<AssetOption> getClusterOptions(AssetOptionsContext ctx) {
        debug("getting clusters");
        Collection<ClusterRestRep> clusters = getClusters(ctx);
        List<AssetOption> options = Lists.newArrayList();
        for (ClusterRestRep value : clusters) {
            options.add(createClusterOption(ctx, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected AssetOption createClusterOption(AssetOptionsContext ctx, ClusterRestRep value) {
        String label = value.getName();
        return new AssetOption(value.getId(), label);
    }

    @Asset("esxCluster")
    public List<AssetOption> getEsxClusterOptions(AssetOptionsContext ctx) {
        debug("getting esx clusters");
        Collection<ClusterRestRep> clusters = getClusters(ctx);
        List<AssetOption> options = Lists.newArrayList();
        for (ClusterRestRep value : clusters) {
            // If Cluster has an esx host - then add it to the list
            List<HostRestRep> hostList = api(ctx).hosts().getByCluster(value.getId());
            for (HostRestRep host : hostList) {
                if (host.getType().equalsIgnoreCase(Host.HostType.Esx.name())) {
                    options.add(createClusterOption(ctx, value));
                    break;
                }
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("vblockCluster")
    public List<AssetOption> getVblockClusterOptions(AssetOptionsContext ctx) {
        debug("getting vblock clusters");
        Collection<ClusterRestRep> clusters = getClusters(ctx);
        List<AssetOption> options = Lists.newArrayList();
        for (ClusterRestRep value : clusters) {
            List<HostRestRep> hostList = api(ctx).hosts().getByCluster(value.getId());
            for (HostRestRep host : hostList) {
                // If Cluster has an esx or No-OS host and if host has a computeElement - then add it to the list
                if (host.getType() != null &&
                    (host.getType().equalsIgnoreCase(Host.HostType.Esx.name()) ||
                     host.getType().equalsIgnoreCase(Host.HostType.No_OS.name())) &&
                    host.getComputeElement() != null && 
                    !NullColumnValueGetter.isNullURI(host.getComputeElement().getId())) {
                    options.add(createClusterOption(ctx, value));
                    break;
                }
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

}
