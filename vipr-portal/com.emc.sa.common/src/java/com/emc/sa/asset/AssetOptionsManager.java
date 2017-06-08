/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.asset;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.vipr.model.catalog.AssetOption;

public interface AssetOptionsManager {

    public List<AssetOptionsProvider> getAssetOptionsProviders();

    public void setAssetOptionsProviders(List<AssetOptionsProvider> assetOptionsProviders);

    public List<AssetOption> getOptions(AssetOptionsContext context, String assetType,
            Map<String, String> availableAssets);

    public AssetOptionsContext createDefaultContext(StorageOSUser user);

    public List<String> getAssetDependencies(String assetType, Set<String> availableTypes);
}
