/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.db.client.URIUtil.uri;

import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList.TenantResourceFilter;
import com.emc.storageos.security.authentication.StorageOSUser;

public class ExecutionWindowFilter
        extends TenantResourceFilter<ExecutionWindow> {

    public ExecutionWindowFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        super(user, permissionsHelper);
    }

    @Override
    public boolean isAccessible(ExecutionWindow resource) {
        return isTenantResourceAccessible(uri(resource.getTenant()));
    }
}
