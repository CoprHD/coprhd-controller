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
package com.emc.sa.service.vipr.keystone;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.keystone.OpenStackTenantParam;

import static com.emc.sa.service.ServiceParams.*;

@Service("EditOSTenants")
public class EditOSTenantsService extends ViPRService {
    @Param(NAME)
    protected String name;

    @Param(OSTENANTS)
    private List<OpenStackTenantParam> ostenants;

    @Override
    public void execute() {
            /*Set<String> options = Sets.newHashSet(tenantsSynchronizationOptions.toString());
            options.add(interval);
            Task<AuthnProviderRestRep> task = execute(new UpdateOSTenants(authnProvider, options));
            addAffectedResource(task);*/
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        List<AuthnProviderRestRep> authnProviders = getClient().authnProviders().getAll();
        for (AuthnProviderRestRep authnProvider : authnProviders){
            if (authnProvider.getMode().equals(AuthnProvider.ProvidersType.keystone.name())) {
                //TODO:
            }
        }
    }
}
