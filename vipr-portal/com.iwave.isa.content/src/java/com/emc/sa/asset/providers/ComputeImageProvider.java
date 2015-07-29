/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.emc.vipr.model.catalog.AssetOption;
import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.model.compute.ComputeImageRestRep;

@Component
@AssetNamespace("vipr")
public class ComputeImageProvider extends BaseAssetOptionsProvider {

    protected List<ComputeImageRestRep> getComputeImages(AssetOptionsContext context) {
        debug("getting compute images");
        return api(context).computeImages().getAll();
    }

    @Asset("computeImage")
    public List<AssetOption> getComputeImageOptions(AssetOptionsContext ctx) {
        debug("getting compute images");
        List<ComputeImageRestRep> availCis = new ArrayList<ComputeImageRestRep>();
        for (ComputeImageRestRep ci : getComputeImages(ctx)) {
            if (ComputeImage.ComputeImageStatus.AVAILABLE.name().equals(ci.getComputeImageStatus())) {
                availCis.add(ci);
            }
        }
        return createBaseResourceOptions(availCis);
    }

    protected AssetOption createComputeImageOption(AssetOptionsContext ctx, ComputeImageRestRep value) {
        String label = value.getName();
        return new AssetOption(value.getId(), label);
    }
}
