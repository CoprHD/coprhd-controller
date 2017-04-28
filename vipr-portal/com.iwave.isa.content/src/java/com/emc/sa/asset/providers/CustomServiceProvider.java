/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.List;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.catalog.AssetOption;

@Component
@AssetNamespace("vipr")
public class CustomServiceProvider extends BaseAssetOptionsProvider {

    @Asset("inventoryFiles")
    public List<AssetOption> getInventoryFiles(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).primitiveResources().getInventoryFiles(null));
    }
	
    @Asset("ansiblePackages")
    public List<AssetOption> getAnsiblePackage(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).primitiveResources().getAnsiblePackages(null));
    }

    @Asset("inventoryFiles")
	@AssetDependencies({ "ansiblePackages" })
    public List<AssetOption> getInventoryFiles(AssetOptionsContext context, URI ansiblePackageId) {
        return getAnsiblePackageInventoryFiles(context, ansiblePackageId);
    }
    
    protected List<AssetOption> getAnsiblePackageInventoryFiles(AssetOptionsContext context, URI ansiblePackageId) {
        ViPRCoreClient client = api(context);
        List<CustomServicesPrimitiveResourceRestRep> inventoryFiles = client.primitiveResources().getInventoryFilesByAnsiblePackage(null, ansiblePackageId);
        return createBaseResourceOptions(inventoryFiles);        
    }

}
