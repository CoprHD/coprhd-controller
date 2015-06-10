/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import com.emc.vipr.model.catalog.AssetOption;
import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.HostTypeFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import static com.emc.vipr.client.core.filters.CompatibilityFilter.INCOMPATIBLE;
import static com.emc.vipr.client.core.filters.RegistrationFilter.REGISTERED;

@Component
@AssetNamespace("vipr")
public class ComputeProvider extends BaseAssetOptionsProvider {

    protected List<ComputeVirtualPoolRestRep> getComputeVirtualPools(AssetOptionsContext context) {
        debug("getting virtual compute pools");
        return api(context).computeVpools().getAll();
    }
    
    @Asset("computeVirtualPool")
    public List<AssetOption> getComputeVirtualPoolOptions(AssetOptionsContext ctx) {
        debug("getting compute virtual pools");
        Collection<ComputeVirtualPoolRestRep> computeVirtualPools = getComputeVirtualPools(ctx);
        List<AssetOption> options = Lists.newArrayList();
        for (ComputeVirtualPoolRestRep value : computeVirtualPools) {
            options.add(createComputeVirtualPoolOption(ctx, value));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }    

    protected AssetOption createComputeVirtualPoolOption(AssetOptionsContext ctx, ComputeVirtualPoolRestRep value) {
        String label = value.getName();
        return new AssetOption(value.getId(), label);
    }
    
}
