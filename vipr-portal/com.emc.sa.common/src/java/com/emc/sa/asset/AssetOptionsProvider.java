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
 * 
 * AssetOptionProviders dynamically provide AssetOptions, which are 
 * name/value pairs of data that populate drop down menus in the UI.
 * 
 */
public interface AssetOptionsProvider {

    /**
     * Determines whether this provider supports the given asset type.
     * Used to determine if this is the provider to use for the given
     * asset type.
     * 
     * @param assetType         The asset type that this provider supports.
     * @return                  true if supported, false otherwise
     */
    boolean isAssetTypeSupported(String assetType);

    /**
     * Gets the AssetOptions and returns them.
     *
     * @param context           Info to pass to method that gets the options
     * @param assetType         The type of asset 
     * @param availableAssets   The assets currently available in the system
     * @return                  Returns a list of AssetOptions
     */
    List<AssetOption> getAssetOptions(AssetOptionsContext context, String assetType, Map<String, String> availableAssets);

    /**
     * Returns the providers that depend on this one.
     * 
     * AssetProviders may depend on each other.  E.g.: VirtualArrayProvider 
     * may depend on the ProjectProvider, since the project is an input to
     * the varray provider.
     * 
     * @param assetType         The type of asset 
     * @param availableTypes    The assets currently available in the system
     * @return                  A list if providers that depend on this one
     */
    List<String> getAssetDependencies(String assetType, Set<String> availableTypes);

    /**
     * Use raw labels for options, instead of resource names from DB.
     * 
     * When providers lookup info representing resources that are in the DB, 
     * the resource name is retrieved from the DB for the UI to display.  (E.g.:
     * when volumes are being retrieved by a provider, the label "Volume" is
     * retrieved from the DB to use as the field label in Order in the UI.
     * 
     * Since this involves an additional lookup, if the provider does not use
     * DB resources, it can be told to use raw labels instead, increasing
     * performance.
     * 
     * @return      Whether the provider should use raw labels or lookup in DB
     */
    boolean useRawLabels();
}
