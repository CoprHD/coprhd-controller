/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.tenant.TenantOrgRestRep;

public class GetTenant extends ViPRExecutionTask<TenantOrgRestRep> {
    private URI tenantId;

    public GetTenant(URI tenantId) {
        this.tenantId = tenantId;
        provideDetailArgs(tenantId);
    }

    @Override
    public TenantOrgRestRep executeTask() throws Exception {
        return getClient().tenants().get(tenantId);
    }
}
