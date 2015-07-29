/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.SearchConstants.VALIDATE_CONNECTION_PARAM;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.auth.RoleAssignments;
import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.tenant.*;

import static com.emc.vipr.client.core.impl.PathConstants.*;

import com.emc.vipr.client.Task;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.util.ResourceUtils;

import javax.ws.rs.core.UriBuilder;

/**
 * Tenants resources.
 * <p>
 * Base URL: <tt>/tenants</tt>
 */
public class Tenants extends AbstractCoreBulkResources<TenantOrgRestRep> implements QuotaResources {
    public Tenants(ViPRCoreClient parent, RestClient client) {
        super(parent, client, TenantOrgRestRep.class, TENANT_URL);
    }

    @Override
    public Tenants withInactive(boolean inactive) {
        return (Tenants) super.withInactive(inactive);
    }

    @Override
    public Tenants withInternal(boolean internal) {
        return (Tenants) super.withInternal(internal);
    }

    @Override
    protected List<TenantOrgRestRep> getBulkResources(BulkIdParam input) {
        TenantOrgBulkRep response = client.post(TenantOrgBulkRep.class, input, getBulkUrl());
        return defaultList(response.getTenants());
    }

    /**
     * Gets the tenant for the current user.
     * <p>
     * API Call: <tt>GET /tenant</tt>
     * 
     * @return the current tenant.
     */
    public TenantResponse current() {
        TenantResponse tenant = client.get(TenantResponse.class, CURRENT_TENANT_URL);
        return tenant;
    }

    /**
     * Gets the ID of the tenant for the current user.
     * 
     * @return the current tenant ID.
     */
    public URI currentId() {
        TenantResponse tenant = current();
        return tenant != null ? tenant.getTenant() : null;
    }

    /**
     * Creates a sub-tenant within the current tenant.
     * 
     * @param input
     *            the tenant configuration.
     * @return the newly created tenant.
     */
    public TenantOrgRestRep create(TenantCreateParam input) {
        URI currentTenantId = currentId();
        return create(currentTenantId, input);
    }

    /**
     * Creates a sub-tenant within the given tenant.
     * <p>
     * API Call: <tt>POST /tenants/{parentTenantId}/subtenants</tt>
     * 
     * @param parentTenantId
     *            the ID of the parent tenant.
     * @param input
     *            the tenant configuration.
     * @return the newly created tenant.
     */
    public TenantOrgRestRep create(URI parentTenantId, TenantCreateParam input) {
        return client.post(TenantOrgRestRep.class, input, SUBTENANTS_URL, parentTenantId);
    }

    /**
     * Creates a host within the given tenant.
     * <p>
     * API Call: <tt>POST /tenants/{tenantId}/hosts</tt>
     * 
     * @param tenantId
     *            the ID of the tenant.
     * @param input
     *            the create configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<HostRestRep> createHost(URI tenantId, HostCreateParam input) {
        return createHost(tenantId, input, false);
    }

    public Task<HostRestRep> createHost(URI tenantId, HostCreateParam input, boolean validateConnection) {
        UriBuilder uriBuilder = client.uriBuilder(HOST_BY_TENANT_URL);
        if (validateConnection) {
            uriBuilder.queryParam(VALIDATE_CONNECTION_PARAM, Boolean.TRUE);
        }
        TaskResourceRep task = client.postURI(TaskResourceRep.class, input, uriBuilder.build(tenantId));
        return new Task<HostRestRep>(client, task, HostRestRep.class);
    }

    /**
     * Updates the given tenant by ID.
     * <p>
     * API Call: <tt>PUT /tenants/{id}</tt>
     * 
     * @param id
     *            the ID of the tenant to update.
     * @param input
     *            the update configuration.
     * @return the updated tenant configuration.
     */
    public TenantOrgRestRep update(URI id, TenantUpdateParam input) {
        return client.put(TenantOrgRestRep.class, input, getIdUrl(), id);
    }

    @Override
    public QuotaInfo getQuota(URI id) {
        return doGetQuota(id);
    }

    @Override
    public QuotaInfo updateQuota(URI id, QuotaUpdateParam quota) {
        return doUpdateQuota(id, quota);
    }

    /**
     * Get Role Assignments for the specified tenant.
     * <p>
     * API Call: <tt>GET /tenants/{id}/role-assignments</tt>
     * 
     * @param id
     *            the ID of the tenant.
     * @return the list of RoleAssignmentEntry
     */
    public List<RoleAssignmentEntry> getRoleAssignments(URI id) {
        RoleAssignments response = client.get(RoleAssignments.class, getRoleAssignmentsUrl(), id);
        return ResourceUtils.defaultList(response.getAssignments());
    }

    /**
     * Update Role Assignments for the specified tenant.
     * <p>
     * API Call: <tt>PUT /tenants/{id}/role-assignments</tt>
     * 
     * @param id
     *            the ID of the tenant.
     * @param roleChanges
     *            Role assignment changes
     * @return the list of RoleAssignmentEntry
     */
    public List<RoleAssignmentEntry> updateRoleAssignments(URI id, RoleAssignmentChanges roleChanges) {
        RoleAssignments response = client.put(RoleAssignments.class, roleChanges, getRoleAssignmentsUrl(), id);
        return ResourceUtils.defaultList(response.getAssignments());
    }

    /**
     * Lists the sub-tenants of the given tenant.
     * <p>
     * API Call: <tt>GET /tenants/{parentId}/subtenants</tt>
     * 
     * @param parentId
     *            the ID of the parent tenant.
     * @return the list of sub-tenant references.
     */
    public List<NamedRelatedResourceRep> listSubtenants(URI parentId) {
        return client.get(TenantOrgList.class, SUBTENANTS_URL, parentId).getSubtenants();
    }

    /**
     * Gets the list of all sub-tenants for the given tenant. This is a convenience method for: <tt>getByRefs(listSubtenants(parentId))</tt>
     * 
     * @param parentId
     *            the ID of the parent tenant.
     * @return the list of sub-tenants.
     */
    public List<TenantOrgRestRep> getAllSubtenants(URI parentId) {
        return getByRefs(listSubtenants(parentId));
    }

    /**
     * Gets the list of all sub-tenants for the given tenant. This is a convenience method for: <tt>getByRefs(listSubtenants(parentId))</tt>
     * 
     * @param parentId
     *            the ID of the parent tenant.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of sub-tenants.
     */
    public List<TenantOrgRestRep> getAllSubtenants(URI parentId, ResourceFilter<TenantOrgRestRep> filter) {
        return getByRefs(listSubtenants(parentId), filter);
    }

    /**
     * Deactivates the given tenant by ID.
     * <p>
     * API Call: <tt>POST /vdc/tenants/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the tenant to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }
}
