/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.asset;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.vipr.model.catalog.AssetOption;

/**
 * Interface for all Asset Options Providers
 */
public interface AssetOptionsProvider {

    boolean isAssetTypeSupported(String assetType);

    List<AssetOption> getAssetOptions(AssetOptionsContext context, String assetType, Map<String, String> availableAssets);

    List<String> getAssetDependencies(String assetType, Set<String> availableTypes);
}
