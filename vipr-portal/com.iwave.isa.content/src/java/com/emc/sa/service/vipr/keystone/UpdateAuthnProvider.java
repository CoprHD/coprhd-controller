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

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.keystone.OpenStackTenantParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

public class UpdateAuthnProvider extends WaitForTask<AuthnProviderRestRep> {
    private URI authnProviderId;
    private Set<String> tenantsSynchronizationOptionsChanges;

    public UpdateAuthnProvider(URI authnProviderId, Set<String> tenantsSynchronizationOptionsChanges) {
        super();
        this.authnProviderId = authnProviderId;
        this.tenantsSynchronizationOptionsChanges = tenantsSynchronizationOptionsChanges;
        provideDetailArgs(authnProviderId, tenantsSynchronizationOptionsChanges);
    }

    @Override
    public Task<AuthnProviderRestRep> doExecute() throws Exception {
        AuthnUpdateParam update = new AuthnUpdateParam();
        AuthnUpdateParam.TenantsSynchronizationOptionsChanges changes = new AuthnUpdateParam.TenantsSynchronizationOptionsChanges();
        changes.setAdd(tenantsSynchronizationOptionsChanges);

        //update.setTenantsSynchronizationOptionsChanges(changes);

        return getClient().authnProviders().updateWithTask(authnProviderId, update);
    }
}
