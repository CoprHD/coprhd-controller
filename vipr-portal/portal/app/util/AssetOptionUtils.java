/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getCatalogClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.AssetDependencyRequest;
import com.emc.vipr.model.catalog.AssetOption;
import com.emc.vipr.model.catalog.AssetOptionsRequest;

import controllers.util.Models;

public class AssetOptionUtils {

    public static List<AssetOption> getAssetOptions(String assetType) {
        return getAssetOptions(assetType, Collections.<String, String> emptyMap());
    }

    public static List<AssetOption> getAssetOptions(String assetType, Map<String, String> assetParameters) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        AssetOptionsRequest request = new AssetOptionsRequest();
        request.setTenantId(uri(Models.currentAdminTenant()));
        request.setAvailableAssets(assetParameters);
        return catalog.assetOptions().getAssetOptions(assetType, request);
    }

    public static List<String> getAssetDependencies(String assetType, Set<String> availableAssetTypes) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        AssetDependencyRequest request = new AssetDependencyRequest();
        request.setTenantId(uri(Models.currentAdminTenant()));
        request.setAvailableAssetTypes(availableAssetTypes);
        return catalog.assetOptions().getAssetDependencies(assetType, availableAssetTypes);
    }
}
