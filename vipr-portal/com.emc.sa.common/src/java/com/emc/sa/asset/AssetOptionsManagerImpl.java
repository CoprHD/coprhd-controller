/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.asset;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.vipr.model.catalog.AssetOption;

/**
 * Finds, introspects and registers all {@link AbstractAssetOptionsProvider}s
 */
@Component
public class AssetOptionsManagerImpl implements AssetOptionsManager {

    private static final Logger log = Logger.getLogger(AssetOptionsManagerImpl.class);

    @Autowired
    private List<AssetOptionsProvider> assetOptionsProviders;

    @Override
    public List<AssetOptionsProvider> getAssetOptionsProviders() {
        return assetOptionsProviders;
    }

    @Override
    public void setAssetOptionsProviders(List<AssetOptionsProvider> assetOptionsProviders) {
        this.assetOptionsProviders = assetOptionsProviders;
    }

    @Override
    public List<AssetOption> getOptions(AssetOptionsContext context, String assetType, Map<String, String> availableAssets) {
        log.info("retrieving asset options for asset [" + assetType + "]");
        AssetOptionsProvider provider = getProviderForAssetType(assetType);
        if (provider != null) {
            return provider.getAssetOptions(context, assetType, availableAssets);
        }
        else {
            return Collections.emptyList();
        }
    }

    public AssetOptionsContext createDefaultContext(StorageOSUser user) {
        AssetOptionsContext context = new AssetOptionsContext();
        context.setAuthToken(user.getToken());
        context.setTenant(URIUtil.uri(user.getTenantId()));
        context.setUserName(user.getUserName());

        return context;
    }

    @Override
    public List<String> getAssetDependencies(String assetType, Set<String> availableTypes) {
        AssetOptionsProvider provider = getProviderForAssetType(assetType);
        if (provider != null) {
            return provider.getAssetDependencies(assetType, availableTypes);
        }
        else {
            return Collections.emptyList();
        }
    }

    public AssetOptionsProvider getProviderForAssetType(String assetType) {
        for (AssetOptionsProvider provider : assetOptionsProviders) {
            if (provider.isAssetTypeSupported(assetType)) {
                return provider;
            }
        }
        return null;
    }
}
