/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.file.FilePolicyList;
import com.emc.storageos.model.file.FilePolicyRestRep;
import com.emc.storageos.model.schedulepolicy.PolicyParam;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyBulkRep;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyList;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyResp;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

public class SchedulePolicies extends AbstractCoreBulkResources<SchedulePolicyRestRep> implements TenantResources<SchedulePolicyRestRep> {

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
     * Creates a Schedule Policy in the given tenant.
     * <p>
     * API Call: <tt>POST /tenants/{tenantId}/schedule-policies</tt>
     * 
     * @param tenantId
     *            the ID of the tenant.
     * @param input
     *            the Policy configuration.
     * @return the newly created schedule policy.
     */
    public SchedulePolicyRestRep create(URI tenantId, PolicyParam input) {
        SchedulePolicyResp element = client
                .post(SchedulePolicyResp.class, input, PathConstants.SCHEDULE_POLICIES_BY_TENANT_URL, tenantId);
        return get(element.getId());
    }

    /**
     * Updates the given Schedule Policy by ID.
     * <p>
     * API Call: <tt>PUT /schedule-policies/{policyId}</tt>
     * 
     * @param id
     *            the ID of the policy to update.
     * @param input
     *            the update configuration.
     */
    public void update(URI id, PolicyParam input) {
        client.put(String.class, input, PathConstants.SCHEDULE_POLICIES_BY_POLICY_URL, id);
    }

    /**
     * Gets the base URL for finding Schedule Snapshot policies by file system: <tt>/file/filesystems/{fileSystemId}/file-policies</tt>
     * 
     * @return the URL for finding by file system.
     */
    protected String getByFileSystemUrl() {
        return PathConstants.FILE_POLICIES_BY_FILESYSTEM_URL;
    }

    /**
     * Lists the file Schedule Snapshot policies for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{fileSystemId}/file-policies</tt>
     * 
     * @param fileSystemId
     *            the ID of the file system.
     * @return the list of file policies references for the file system.
     */
    public List<FilePolicyRestRep> listByFileSystem(URI fileSystemId) {
        FilePolicyList response = client.get(FilePolicyList.class, getByFileSystemUrl(), fileSystemId);
        return defaultList(response.getFilePolicies());
    }

    /**
     * This method assigns a policy to a file system.
     * <p>
     * API Call: <tt>PUT /file/filesystems/{fs_id}/assign-file-policy/{policy_id}</tt>
     * 
     * @param fileSystemId the ID of the file system.
     * @param policyId the ID of the policy.
     */
    public void assignPolicyToFileSystem(URI fileSystemId, URI policyId) {

        client.put(String.class, PathConstants.ASSIGN_POLICY_URL, fileSystemId, policyId);

    }

    /**
     * This method unassigna policy from the file system.
     * <p>
     * API Call: <tt>PUT /file/filesystems/{fs_id}/unassign-file-policy/{policy_id}</tt>
     * 
     * @param fileSystemId the ID of the file system.
     * @param policyId the ID of the policy.
     */
    public void unassignPolicyToFileSystem(URI fileSystemId, URI policyId) {

        client.put(String.class, PathConstants.UNASSIGN_POLICY_URL, fileSystemId, policyId);

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
