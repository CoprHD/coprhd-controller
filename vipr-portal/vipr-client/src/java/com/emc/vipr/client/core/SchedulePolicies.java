package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.project.PolicyParam;
import com.emc.storageos.model.project.SchedulePolicyBulkRep;
import com.emc.storageos.model.project.SchedulePolicyList;
import com.emc.storageos.model.project.SchedulePolicyRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

public class SchedulePolicies extends AbstractCoreBulkResources<SchedulePolicyRestRep> implements TenantResources<SchedulePolicyRestRep>{

    public SchedulePolicies(ViPRCoreClient parent, RestClient client) {
        
        super(parent, client, SchedulePolicyRestRep.class, PathConstants.SCHEDULE_POLICIES_URL);
    }
    
    /**
     * Deletes the given schedule policy by ID.
     * <p>
     * API Call: <tt>DELETE /schedule-policies/{policyId}</tt>
     * 
     * @param id
     *            the ID of policy to deactivate.
     */
    public void delete(URI id) {
        client.delete(String.class, PathConstants.SCHEDULE_POLICIES_BY_POLICY_URL, id);
    }
    
    /**
     * Creates a project in the given tenant.
     * <p>
     * API Call: <tt>POST /tenants/{tenantId}/schedule-policies</tt>
     * 
     * @param tenantId
     *            the ID of the tenant.
     * @param input
     *            the project configuration.
     * @return the newly created project.
     */
    public SchedulePolicyRestRep create(URI tenantId, PolicyParam input) {
        SchedulePolicyRestRep element = client
                .post(SchedulePolicyRestRep.class, input, PathConstants.SCHEDULE_POLICIES_BY_TENANT_URL, tenantId);
        return get(element.getId());
    }

    @Override
    protected List<SchedulePolicyRestRep> getBulkResources(BulkIdParam input) {
        SchedulePolicyBulkRep response = client.post(SchedulePolicyBulkRep.class, input, getBulkUrl());
        return defaultList(response.getSchedulePolicies());
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    @Override
    public List<SchedulePolicyRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<SchedulePolicyRestRep> getByUserTenant(ResourceFilter<SchedulePolicyRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        SchedulePolicyList response = client.get(SchedulePolicyList.class, PathConstants.SCHEDULE_POLICIES_BY_TENANT_URL, tenantId);
        return ResourceUtils.defaultList(response.getSchdulePolicies());
    }

    @Override
    public List<SchedulePolicyRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<SchedulePolicyRestRep> getByTenant(URI tenantId, ResourceFilter<SchedulePolicyRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }

}
