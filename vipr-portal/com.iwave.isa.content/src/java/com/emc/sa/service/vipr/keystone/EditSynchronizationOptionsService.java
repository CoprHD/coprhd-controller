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

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.keystone.OpenStackTenantParam;
import com.emc.vipr.client.Task;
import com.google.common.collect.Sets;

import static com.emc.sa.service.ServiceParams.*;

@Service("EditSynchronizationOptions")
public class EditSynchronizationOptionsService extends ViPRService {

    @Param(AUTHN_PROVIDER)
    protected URI authnProvider;

    @Param(INTERVAL)
    protected String interval;

    @Param(AUTOMATIC_ADDITION)
    protected Boolean automaticAddition;

    @Param(AUTOMATIC_DELETION)
    protected Boolean automaticDeletion;


    @Override
    public void execute() {
        Set<String> options = Sets.newHashSet();
        if (automaticAddition) {
            options.add("ADDITION");
        }
        if (automaticDeletion) {
            options.add("DELETION");
        }
        options.add(interval);
        Task<AuthnProviderRestRep> task = execute(new UpdateAuthnProvider(authnProvider, options));
        addAffectedResource(task);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        List<AuthnProviderRestRep> authnProviders = getClient().authnProviders().getAll();
        for (AuthnProviderRestRep authnProvider : authnProviders) {
            if (authnProvider.getMode().equals(AuthnProvider.ProvidersType.keystone.name())) {
                // TODO:
            }
        }
    }

    @Override
    public void init() throws Exception {
        super.init();
    }
}
