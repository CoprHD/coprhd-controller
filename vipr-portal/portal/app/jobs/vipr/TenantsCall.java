/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package jobs.vipr;

import static com.emc.vipr.client.core.util.ResourceUtils.id;

import java.net.URI;
import java.util.List;

import util.BourneUtil;

import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.CachedResources;
import com.google.common.collect.Lists;

public class TenantsCall extends ViPRListCall<TenantOrgRestRep> {
    public TenantsCall() {
        this(BourneUtil.getViprClient());
    }

    public TenantsCall(ViPRCoreClient client) {
        this(client, new CachedResources<TenantOrgRestRep>(client.tenants()));
    }

    public TenantsCall(ViPRCoreClient client, CachedResources<TenantOrgRestRep> cache) {
        super(client, cache);
    }

    @Override
    public List<TenantOrgRestRep> call() {
        List<TenantOrgRestRep> tenants = Lists.newArrayList();
        TenantOrgRestRep rootTenant = findRootTenant();
        tenants.add(rootTenant);
        tenants.addAll(getSubTenants(id(rootTenant)));
        return tenants;
    }

    public TenantOrgRestRep findRootTenant() {
        URI userTenantId = client.getUserTenantId();
        TenantOrgRestRep currentTenant = get(userTenantId);
        while (currentTenant.getParentTenant() != null) {
            currentTenant = get(currentTenant.getParentTenant());
        }
        return currentTenant;
    }

    public List<TenantOrgRestRep> getSubTenants(URI parentTenantId) {
        return getByRefs(client.tenants().listSubtenants(parentTenantId));
    }
}
