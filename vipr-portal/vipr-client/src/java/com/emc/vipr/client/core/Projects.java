/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TypedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.project.*;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.core.util.ResourceUtils;

/**
 * Projects resources.
 * <p>
 * Base URL: <tt>/projects</tt>
 */
public class Projects extends AbstractCoreBulkResources<ProjectRestRep> implements ACLResources, QuotaResources,
        TenantResources<ProjectRestRep> {
    public Projects(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ProjectRestRep.class, PathConstants.PROJECT_URL);
    }

    @Override
    public Projects withInactive(boolean inactive) {
        return (Projects) super.withInactive(inactive);
    }

    @Override
    public Projects withInternal(boolean internal) {
        return (Projects) super.withInternal(internal);
    }

    @Override
    protected List<ProjectRestRep> getBulkResources(BulkIdParam input) {
        ProjectBulkRep response = client.post(ProjectBulkRep.class, input, getBulkUrl());
        return defaultList(response.getProjects());
    }

    /**
     * Creates a project in the user's tenant.
     * 
     * @param input
     *            the project configuration.
     * @return the newly created project.
     */
    public ProjectRestRep create(ProjectParam input) {
        return create(parent.getUserTenantId(), input);
    }

    /**
     * Creates a project in the given tenant.
     * <p>
     * API Call: <tt>POST /tenants/{tenantId}/projects</tt>
     * 
     * @param tenantId
     *            the ID of the tenant.
     * @param input
     *            the project configuration.
     * @return the newly created project.
     */
    public ProjectRestRep create(URI tenantId, ProjectParam input) {
        ProjectElement element = client
                .post(ProjectElement.class, input, PathConstants.PROJECT_BY_TENANT_URL, tenantId);
        return get(element.getId());
    }

    /**
     * Updates the given project by ID.
     * <p>
     * API Call: <tt>PUT /projects/{id}</tt>
     * 
     * @param id
     *            the ID of the project to update.
     * @param input
     *            the update configuration.
     */
    public void update(URI id, ProjectUpdateParam input) {
        client.put(String.class, input, getIdUrl(), id);
    }

    /**
     * Deactivates the given project by ID.
     * <p>
     * API Call: <tt>POST /projects/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of project to deactivate.
     */
    public void deactivate(URI id) {
        doDeactivate(id);
    }

    @Override
    public List<ACLEntry> getACLs(URI id) {
        return doGetACLs(id);
    }

    @Override
    public List<ACLEntry> updateACLs(URI id, ACLAssignmentChanges aclChanges) {
        return doUpdateACLs(id, aclChanges);
    }

    /**
     * Lists project by tenant by ID.
     * <p>
     * API Call: <tt>GET /tenants/{tenantId}/projects</tt>
     * 
     * @param tenantId
     *            the ID of the tenant.
     * @return the list of project references.
     */
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        ProjectList response = client.get(ProjectList.class, PathConstants.PROJECT_BY_TENANT_URL, tenantId);
        return ResourceUtils.defaultList(response.getProjects());
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(parent.getUserTenantId());
    }

    public List<ProjectRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    public List<ProjectRestRep> getByUserTenant(ResourceFilter<ProjectRestRep> filter) {
        return getByTenant(parent.getUserTenantId(), filter);
    }

    public List<ProjectRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    public List<ProjectRestRep> getByTenant(URI tenantId, ResourceFilter<ProjectRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
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
     * Lists the resources for the given project by ID.
     * <p>
     * API Call: <tt>GET /projects/{id}/resources</tt>
     * 
     * @param id
     *            the ID of the project.
     * @return the list of project resource references.
     */
    public List<TypedRelatedResourceRep> listResources(URI id) {
        ResourceList response = client.get(ResourceList.class, getIdUrl() + "/resources", id);
        return defaultList(response.getResources());
    }
}