/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import com.emc.vipr.model.catalog.AssetOption;
import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.google.common.collect.Lists;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

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
