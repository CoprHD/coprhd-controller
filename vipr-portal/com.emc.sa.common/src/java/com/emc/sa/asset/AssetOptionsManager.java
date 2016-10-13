/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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

    public AssetOptionsProvider getProviderForAssetType(String assetType);
}
