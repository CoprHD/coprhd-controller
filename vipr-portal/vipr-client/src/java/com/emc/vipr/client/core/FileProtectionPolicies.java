/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.file.policy.FilePolicyAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyBulkRep;
import com.emc.storageos.model.file.policy.FilePolicyCreateParam;
import com.emc.storageos.model.file.policy.FilePolicyCreateResp;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyStorageResourceRestRep;
import com.emc.storageos.model.file.policy.FilePolicyStorageResources;
import com.emc.storageos.model.file.policy.FilePolicyUnAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class FileProtectionPolicies extends AbstractCoreBulkResources<FilePolicyRestRep>
        implements TaskResources<FilePolicyRestRep>, ACLResources {

    public FileProtectionPolicies(ViPRCoreClient parent, RestClient client) {

        super(parent, client, FilePolicyRestRep.class, PathConstants.FILE_PROTECTION_POLICIES_URL);
    }

    private String getAssignPolicyUrl() {
        return PathConstants.FILE_PROTECTION_POLICY_URL + "/assign-policy";
    }

    private String getUnAssignPolicyUrl() {
        return PathConstants.FILE_PROTECTION_POLICY_URL + "/unassign-policy";
    }

    private String getPolicyStorageResourcesUrl() {
        return PathConstants.FILE_PROTECTION_POLICY_URL + "/policy-storage-resources";
    }

    /**
     * Deletes the given file protection policy by ID.
     * <p>
     * API Call: <tt>DELETE /file/file-policies/{id}</tt>
     * 
     * @param id
     *            the ID of policy to deactivate.
     */
    public void delete(URI id) {
        client.delete(String.class, PathConstants.FILE_PROTECTION_POLICY_URL, id);
    }

    /**
     * Creates a File Protection Policy.
     * <p>
     * API Call: <tt>POST /file/file-policies/</tt>
     * 
     * @param input
     *            the Policy configuration.
     * @return the newly created schedule policy.
     */
    public FilePolicyCreateResp create(FilePolicyCreateParam input) {
        FilePolicyCreateResp element = client
                .post(FilePolicyCreateResp.class, input, PathConstants.FILE_PROTECTION_POLICIES_URL);
        return element;
    }

    /**
     * Updates the given File Protection Policy by ID.
     * <p>
     * API Call: <tt>PUT /file/file-policies/{id}</tt>
     * 
     * @param id
     *            the ID of the policy to update.
     * @param input
     *            the update configuration.
     */
    public TaskResourceRep update(URI id, FilePolicyUpdateParam input) {
        return client.put(TaskResourceRep.class, input, PathConstants.FILE_PROTECTION_POLICY_URL, id);
    }

    /**
     * Get the details of a given policy.
     * <p>
     * API Call: <tt>GET /file/file-policies/{id}</tt>
     * 
     * @param id
     *            the ID of the policy.
     * @return get the details of given policy.
     */
    public FilePolicyRestRep getFilePolicy(URI id) {
        FilePolicyRestRep response = client.get(FilePolicyRestRep.class, PathConstants.FILE_PROTECTION_POLICY_URL, id);
        return response;
    }

    /**
     * Lists the file policies.
     * <p>
     * API Call: <tt>GET /file/file-policies</tt>
     * 
     * @return get the details of given policy.
     */
    public FilePolicyListRestRep listFilePolicies() {
        FilePolicyListRestRep response = client.get(FilePolicyListRestRep.class, PathConstants.FILE_PROTECTION_POLICIES_URL);
        return response;
    }

    @Override
    protected List<FilePolicyRestRep> getBulkResources(BulkIdParam input) {
        FilePolicyBulkRep response = client.post(FilePolicyBulkRep.class, input, getBulkUrl());
        return defaultList(response.getFilePolicies());
    }

    /**
     * Assign the given policy to a give level.
     * <p>
     * API Call: <tt>POST /file/file-policies/{id}/assign-policy</tt>
     * 
     * @param id
     *            the ID of the policy to be assigned.
     * @param input
     *            the update configuration.
     */
    public TaskResourceRep assignPolicy(URI id, FilePolicyAssignParam input) {
        return client.post(TaskResourceRep.class, input, getAssignPolicyUrl(), id);
    }

    /**
     * Un-assign the given policy from a level.
     * <p>
     * API Call: <tt>POST /file/file-policies/{id}/unassign-policy</tt>
     * 
     * @param id
     *            the ID of the policy to be assigned.
     * @param input
     *            the update configuration.
     */
    public TaskResourceRep unassignPolicy(URI id, FilePolicyUnAssignParam input) {
        return client.post(TaskResourceRep.class, input, getUnAssignPolicyUrl(), id);
    }

    @Override
    public Tasks<FilePolicyRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<FilePolicyRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
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
     * Lists the file policy storage resources.
     * <p>
     * API Call: <tt>GET /file/file-policies/{id}/policy-storage-resources</tt>
     * 
     * @return get the details policy storage resource for given policy.
     */
    public List<FilePolicyStorageResourceRestRep> getPolicyStorageResources(URI id) {
        FilePolicyStorageResources response = client.get(FilePolicyStorageResources.class, getPolicyStorageResourcesUrl(), id);
        return defaultList(response.getStorageResources());
    }

}
