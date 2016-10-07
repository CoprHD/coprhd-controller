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

import com.emc.vipr.model.catalog.AssetOption;

/**
 * Interface for all Asset Options Providers
 */
public interface AssetOptionsProvider {

    boolean isAssetTypeSupported(String assetType);

    List<AssetOption> getAssetOptions(AssetOptionsContext context, String assetType, Map<String, String> availableAssets);

    List<String> getAssetDependencies(String assetType, Set<String> availableTypes);

    boolean useRawLabels();
}
