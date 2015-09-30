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
import com.emc.storageos.model.file.QuotaDirectoryBulkRep;
import com.emc.storageos.model.file.QuotaDirectoryCreateParam;
import com.emc.storageos.model.file.QuotaDirectoryDeleteParam;
import com.emc.storageos.model.file.QuotaDirectoryList;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;
import com.emc.storageos.model.file.QuotaDirectoryUpdateParam;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * File Systems quota directory resources.
 * <p>
 * Base URL: <tt>/file/quotadirectories</tt>
 */

public class QuotaDirectories extends ProjectResources<QuotaDirectoryRestRep> implements TaskResources<QuotaDirectoryRestRep> {

    public QuotaDirectories(ViPRCoreClient parent, RestClient client) {
        super(parent, client, QuotaDirectoryRestRep.class, PathConstants.FILESYSTEM_QDIR_URL);
    }

    @Override
    public QuotaDirectories withInactive(boolean inactive) {
        return (QuotaDirectories) super.withInactive(inactive);
    }

    @Override
    public QuotaDirectories withInternal(boolean internal) {
        return (QuotaDirectories) super.withInternal(internal);
    }

    @Override
    public Tasks<QuotaDirectoryRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<QuotaDirectoryRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Begins creating quota-directory for a file system
     * <p>
     * API Call: <tt>POST /file/filesystems/{id}/quota-directories</tt>
     * 
     * @param fileSystemId
     *            the ID of the file system.
     * @param input
     *            the Quota directory configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<QuotaDirectoryRestRep> createQuotaDirectory(URI fileSystemId, QuotaDirectoryCreateParam input) {
        return postTask(input, getByFileSystemUrl(), fileSystemId);
    }

    /**
     * Updates an quota directory from the given quota directory ID.
     * <p>
     * API Call: <tt>POST /file/quotadirectories/{id}</tt>
     * 
     * @param id
     *            the ID of the quota directory.
     * @param update
     *            the update configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<QuotaDirectoryRestRep> updateQuotaDirectory(URI id, QuotaDirectoryUpdateParam update) {
        return postTask(update, getIdUrl(), id);
    }

    /**
     * Begins deleting quota-directory from a file system
     * <p>
     * API Call: <tt>POST /file/quotadirectories/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the quota directory.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<QuotaDirectoryRestRep> deleteQuotaDirectory(URI id, QuotaDirectoryDeleteParam input) {
        return postTask(input, getDeactivateUrl(), id);
    }

    /**
     * Gets the quota directory by ID.
     * <p>
     * API Call: <tt>GET /file/quotadirectories/{id}</tt>
     * 
     * @param id
     *            the ID of the Quota Directory
     * @return the quota directory for the given id.
     */
    public QuotaDirectoryRestRep getQuotaDirectory(URI id) {
        QuotaDirectoryRestRep response = client.get(QuotaDirectoryRestRep.class,
                getIdUrl(), id);
        return response;
    }

    @Override
    protected List<QuotaDirectoryRestRep> getBulkResources(BulkIdParam input) {
        QuotaDirectoryBulkRep response = client.post(QuotaDirectoryBulkRep.class, input, getBulkUrl());
        return defaultList(response.getQuotaDirectories());
    }

    /**
     * Gets the base URL for finding quota directories by file system: <tt>/file/filesystems/{fileSystemId}/quota-directories</tt>
     * 
     * @return the URL for finding by file system.
     */
    protected String getByFileSystemUrl() {
        return PathConstants.FILESYSTEM_URL + "/{fileSystemId}/quota-directories";
    }

    /**
     * Lists the file quota directories for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{fileSystemId}/quota-directories</tt>
     * 
     * @param fileSystemId
     *            the ID of the file system.
     * @return the list of file quota directory references for the file system.
     */
    public List<NamedRelatedResourceRep> listByFileSystem(URI fileSystemId) {
        QuotaDirectoryList response = client.get(QuotaDirectoryList.class, getByFileSystemUrl(), fileSystemId);
        return defaultList(response.getQuotaDirs());
    }

    /**
     * Gets the list of file quota directories for the given file system by ID.
     * <p>
     * This is a convenience method for: <tt>getByRefs(listByFileSystem(fileSystemId))</tt>
     * 
     * @param fileSystemId
     *            the ID of the file system.
     * @return the list of file quota directories for the file system.
     */
    public List<QuotaDirectoryRestRep> getByFileSystem(URI fileSystemId) {
        return getByFileSystem(fileSystemId, null);
    }

    /**
     * Gets the list of file quota directories for the given file system by ID, optionally filtering the results.
     * <p>
     * This is a convenience method for: <tt>getByRefs(listByFileSystem(fileSystemId), filter)</tt>
     * 
     * @param fileSystemId
     *            the ID of the file system.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of file quota directories for the file system.
     */
    public List<QuotaDirectoryRestRep> getByFileSystem(URI fileSystemId, ResourceFilter<QuotaDirectoryRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByFileSystem(fileSystemId);
        return getByRefs(refs, filter);
    }

}
