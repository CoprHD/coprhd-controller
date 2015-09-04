/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.db.client.URIUtil.uri;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList.TenantResourceFilter;
import com.emc.storageos.security.authentication.StorageOSUser;

public class ApprovalFilter
        extends TenantResourceFilter<ApprovalRequest> {

    public ApprovalFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        super(user, permissionsHelper);
    }

    @Override
    public boolean isAccessible(ApprovalRequest resource) {
        return isTenantResourceAccessible(uri(resource.getTenant()));
    }
}
