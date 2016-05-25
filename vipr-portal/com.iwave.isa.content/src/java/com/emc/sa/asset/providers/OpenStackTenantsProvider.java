/*
 * Copyright 2016 Intel Corporation
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
package com.emc.sa.asset.providers;

import java.util.List;

import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class OpenStackTenantsProvider extends BaseAssetOptionsProvider {
    @Asset("myProvider")
    public List<AssetOption> getProjects(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).authnProviders().getAll());
    }

    @Asset("tenantsSynchronizationOptions")
    public List<AssetOption> getTenantsSynchronizationOptions(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (AuthnProvider.TenantsSynchronizationOptions option : AuthnProvider.TenantsSynchronizationOptions.values()) {
            options.add(newAssetOption(option.name(), String.format("Tenants.Synchronization.Options.%s", option.name())));
        }
        return options;
    }

    @Asset("ostenants")
    public List<AssetOption> getOStenants(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).coprhdOsTenants().getAll());
    }

    @Asset("interval")
    public AssetOption getInterval(AssetOptionsContext ctx) {
        List<AuthnProviderRestRep> authnProviders = api(ctx).authnProviders().getAll();
        for (AuthnProviderRestRep authnProvider : authnProviders) {
            if (authnProvider.getMode().equals(AuthnProvider.ProvidersType.keystone.name())) {
                for (String option : authnProvider.getTenantsSynchronizationOptions()) {
                    // There is only ADDITION, DELETION and interval in this StringSet.
                    if (!AuthnProvider.TenantsSynchronizationOptions.ADDITION.toString().equals(option)
                            && !AuthnProvider.TenantsSynchronizationOptions.DELETION.toString().equals(option)) {
                        return newAssetOption("interval", option);
                    }
                }
            }
        }
        return newAssetOption("interval", "100");
    }
}
