/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class BaseHostProvider extends BaseAssetOptionsProvider {
    private static final String DISCOVERY_ERROR = "ERROR";
    private static final String DISCOVERY_NOT_CONNECTED = "NOTCONNECTED";

    protected String getDiscoveryError(DiscoveredSystemObjectRestRep system) {
        if (DISCOVERY_ERROR.equals(system.getDiscoveryJobStatus())) {
            return getMessage("discovery.failed");
        } else if (DISCOVERY_NOT_CONNECTED.equals(system.getDiscoveryJobStatus())) {
            return getMessage("discovery.notConnected");
        }
        // No error
        return null;
    }

    protected String getClusterName(AssetOptionsContext ctx, HostRestRep host) {
        if (host.getCluster() != null) {
            ClusterRestRep cluster = api(ctx).clusters().get(host.getCluster());
            if (cluster != null) {
                return cluster.getName();
            }
        }
        return null;
    }

    protected List<AssetOption> createHostOptions(AssetOptionsContext ctx, Collection<HostRestRep> hosts) {
        List<AssetOption> options = Lists.newArrayList();
        for (HostRestRep value : hosts) {
            options.add(createHostOption(ctx, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> createFileHostOptions(AssetOptionsContext ctx, Collection<HostRestRep> hosts) {
        List<AssetOption> options = Lists.newArrayList();
        for (HostRestRep value : hosts) {
            options.add(createFileHostOption(ctx, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected AssetOption createHostOption(AssetOptionsContext ctx, HostRestRep host) {
        String discoveryMessage = getDiscoveryError(host);
        String clusterName = getClusterName(ctx, host);
        String label = host.getName();
        if (StringUtils.isNotBlank(clusterName)) {
            label = getMessage("host.memberOfCluster", host.getName(), clusterName);
        }
        if (discoveryMessage != null) {
            label = getMessage("host.withDiscovery", host.getName(), discoveryMessage);
        }
        return new AssetOption(host.getId(), label);
    }

    protected AssetOption createFileHostOption(AssetOptionsContext ctx, HostRestRep host) {
        String clusterName = getClusterName(ctx, host);
        String label = host.getName();
        if (StringUtils.isNotBlank(clusterName)) {
            label = getMessage("host.memberOfCluster", host.getName(), clusterName);
        }
        return new AssetOption(host.getId(), label);
    }

    protected List<ClusterRestRep> getClusters(AssetOptionsContext context, List<HostRestRep> hosts) {
        Set<URI> clusterIds = Sets.newHashSet();
        for (HostRestRep host : hosts) {
            URI clusterId = ResourceUtils.id(host.getCluster());
            if (clusterId != null) {
                clusterIds.add(clusterId);
            }
        }
        return api(context).clusters().getByIds(clusterIds);
    }

    protected List<AssetOption> createClusterOptions(AssetOptionsContext ctx, Collection<ClusterRestRep> clusters) {
        List<AssetOption> options = Lists.newArrayList();
        for (ClusterRestRep value : clusters) {
            options.add(createClusterOption(ctx, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected AssetOption createClusterOption(AssetOptionsContext ctx, ClusterRestRep cluster) {
        String label = null;
        if (cluster.getAutoExportEnabled()) {
            label = cluster.getName();
        } else {
            label = getMessage("cluster.autoExportDisabled", cluster.getName());
        }
        return new AssetOption(cluster.getId(), label);
    }

    protected List<AssetOption> getHostOrClusterOptions(AssetOptionsContext context, List<HostRestRep> hosts,
            String storageType) {
        if (BlockProvider.isExclusiveStorage(storageType)) {
            return createHostOptions(context, hosts);
        } else {
            List<ClusterRestRep> clusters = getClusters(context, hosts);
            return createClusterOptions(context, clusters);
        }
    }
}
