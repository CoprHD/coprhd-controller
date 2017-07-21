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
package com.emc.sa.api.mapper;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList.TenantResourceFilter;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.security.authentication.StorageOSUser;

public class CustomServicesWorkflowFilter extends TenantResourceFilter<CustomServicesWorkflow> {

    public CustomServicesWorkflowFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        super(user, permissionsHelper);
    }

    @Override
    protected boolean isAccessible(CustomServicesWorkflow resource) {
        return true;
    }

}
