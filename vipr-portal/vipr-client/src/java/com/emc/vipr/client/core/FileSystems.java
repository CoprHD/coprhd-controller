/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileCifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParam;
import com.emc.storageos.model.file.FileShareBulkRep;
import com.emc.storageos.model.file.FileShareExportUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.storageos.model.file.FileSystemExpandParam;
import com.emc.storageos.model.file.FileSystemExportList;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.model.file.FileSystemShareList;
import com.emc.storageos.model.file.FileSystemShareParam;
import com.emc.storageos.model.file.NfsACL;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.model.file.NfsACLs;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * File Systems resources.
 * <p>
 * Base URL: <tt>/file/filesystems</tt>
 */
public class FileSystems extends ProjectResources<FileShareRestRep> implements TaskResources<FileShareRestRep> {
    private static final String PROJECT_PARAM = "project";
    private static final String SUBDIRECTORY_PARAM = "subDirectory";
    private static final String SUBDIR_PARAM = "subDir";
    private static final String ALLDIR_PARAM = "allDirs";

    public FileSystems(ViPRCoreClient parent, RestClient client) {
        super(parent, client, FileShareRestRep.class, PathConstants.FILESYSTEM_URL);
    }

    @Override
    public FileSystems withInactive(boolean inactive) {
        return (FileSystems) super.withInactive(inactive);
    }

    @Override
    public FileSystems withInternal(boolean internal) {
        return (FileSystems) super.withInternal(internal);
    }

    /**
     * Gets the base URL for exports for a single filesystem: <tt>/file/filesystems/{id}/exports</tt>
     * 
     * @return the exports URL.
     */
    protected String getExportsUrl() {
        return getIdUrl() + "/exports";
    }

    /**
     * Gets the base URL for export for a single filesystem: <tt>/file/filesystems/{id}/export</tt>
     * 
     * @return the export URL.
     */
    protected String getExportUrl() {
        return getIdUrl() + "/export";
    }

    /**
     * Gets the base URL for shares for a single snapshot: <tt>/file/filesystems/{id}/shares</tt>
     * 
     * @return the shares URL.
     */
    protected String getSharesUrl() {
        return getIdUrl() + "/shares";
    }

    /**
     * Gets the base URL for shares for a single snapshot: <tt>/file/filesystems/{id}/shares</tt>
     * 
     * @return the shares URL.
     */
    protected String getShareACLsUrl() {
        return getIdUrl() + "/shares/{shareName}/acl";
    }
    
	/**
	 * Gets the base URL for NFS ACL for a filesystem:
	 * <tt>/file/filesystems/{id}/acl</tt>
	 * 
	 * @return the NFS ACL URL.
	 */
	protected String getNfsACLsUrl() {
		return "/file/filesystems/{id}/acl";
	}
    

    @Override
    protected List<FileShareRestRep> getBulkResources(BulkIdParam input) {
        FileShareBulkRep response = client.post(FileShareBulkRep.class, input, getBulkUrl());
        return defaultList(response.getFileShares());
    }

    @Override
    public Tasks<FileShareRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<FileShareRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Begins creating a file system in the given project.
     * <p>
     * API Call: <tt>POST /file/filesystems?project={projectId}</tt>
     * 
     * @param projectId
     *            the ID of the project.
     * @param input
     *            the file system configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> create(URI projectId, FileSystemParam input) {
        URI targetUri = client.uriBuilder(baseUrl).queryParam(PROJECT_PARAM, projectId).build();
        return postTaskURI(input, targetUri);
    }

    /**
     * Begins expanding the given file system by ID.
     * <p>
     * API Call: <tt>POST /file/filesystems/{id}/expand</tt>
     * 
     * @param id
     *            the ID of the file system to expand.
     * @param input
     *            the expand configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> expand(URI id, FileSystemExpandParam input) {
        return postTask(input, getIdUrl() + "/expand", id);
    }

    /**
     * Begins deactivating the given file system by ID.
     * <p>
     * API Call: <tt>POST /file/filesystems/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the file system to deactivate.
     * @param input
     *            the delete configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> deactivate(URI id, FileSystemDeleteParam input) {
        return postTask(input, getDeactivateUrl(), id);
    }

    /**
     * Gets the list of exports for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{id}/exports</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @return the list of exports for the file system.
     */
    public List<FileSystemExportParam> getExports(URI id) {
        FileSystemExportList response = client.get(FileSystemExportList.class, getExportsUrl(), id);
        return defaultList(response.getExportList());
    }

    /**
     * Gets the list of export rules for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{id}/export</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param allDirs
     *            boolean value for indicating for all directories
     * @param subDir
     *            string indicating on what subdirectory to query
     * @return the list of export rules for the file system.
     */
    public List<ExportRule> getExport(URI id, boolean allDirs, String subDir) {
        UriBuilder builder = client.uriBuilder(getExportUrl());
        if (allDirs) {
            builder.queryParam(ALLDIR_PARAM, allDirs);
        }
        else if (subDir != null) {
            builder.queryParam(SUBDIR_PARAM, subDir);
        }
        URI targetUri = builder.build(id);
        ExportRules response = client.getURI(ExportRules.class, targetUri);
        return defaultList(response.getExportRules());
    }

    /**
     * Exports the given file system by ID.
     * <p>
     * API Call: <tt>POST /file/filesystems/{id}/exports</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param input
     *            the export configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> export(URI id, FileSystemExportParam input) {
        return postTask(input, getExportsUrl(), id);
    }

    /**
     * Removes an export from the given file system by ID.
     * <p>
     * API Call: <tt>DELETE /file/filesystems/{id}/exports/{protocol},{securityType},{permissions},{rootUserMapping}</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param protocol
     *            the protocol of the export.
     * @param securityType
     *            the security type of the export.
     * @param permissions
     *            the permissions of the export.
     * @param rootUserMapping
     *            the root user mapping of the export.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> removeExport(URI id, String protocol, String securityType, String permissions,
            String rootUserMapping) {
        return removeExport(id, protocol, securityType, permissions, rootUserMapping, null);
    }

    /**
     * Removes an export from the given file system by ID.
     * <p>
     * API Call:
     * <tt>DELETE /file/filesystems/{id}/exports/{protocol},{securityType},{permissions},{rootUserMapping}?subDirectory={subDirectory}</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param protocol
     *            the protocol of the export.
     * @param securityType
     *            the security type of the export.
     * @param permissions
     *            the permissions of the export.
     * @param rootUserMapping
     *            the root user mapping of the export.
     * @param subDirectory
     *            the sub directory of the export.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> removeExport(URI id, String protocol, String securityType, String permissions,
            String rootUserMapping, String subDirectory) {
        UriBuilder builder = client.uriBuilder(getExportsUrl()
                + "/{protocol},{securityType},{permissions},{rootUserMapping}");
        if ((subDirectory != null) && (subDirectory.length() > 0)) {
            builder.queryParam(SUBDIRECTORY_PARAM, subDirectory);
        }
        return deleteTaskURI(builder.build(id, protocol, securityType, permissions, rootUserMapping));
    }

    /**
     * Updates an export from the given file system by ID.
     * <p>
     * API Call: <tt>PUT /file/filesystems/{id}/exports/{protocol},{securityType},{permissions},{rootUserMapping}</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param protocol
     *            the protocol of the export.
     * @param securityType
     *            the security type of the export.
     * @param permissions
     *            the permissions of the export.
     * @param rootUserMapping
     *            the root user mapping of the export.
     * @param update
     *            the update configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> updateExport(URI id, String protocol, String securityType, String permissions,
            String rootUserMapping, FileExportUpdateParam update) {
        return putTask(update, getExportsUrl() + "/{protocol},{securityType},{permissions},{rootUserMapping}", id,
                protocol, securityType, permissions, rootUserMapping);
    }

    /**
     * Gets the shares for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{id}/shares</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @return the list of shares for the given file system.
     */
    public List<SmbShareResponse> getShares(URI id) {
        FileSystemShareList response = client.get(FileSystemShareList.class, getSharesUrl(), id);
        return defaultList(response.getShareList());
    }

    /**
     * Begins sharing a file system by ID.
     * <p>
     * API Call: <tt>POST /file/filesystems/{id}/shares</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param input
     *            the share configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> share(URI id, FileSystemShareParam input) {
        return postTask(input, getSharesUrl(), id);
    }

    /**
     * Begins removing a share from the given file system by ID.
     * <p>
     * API Call: <tt>POST /file/filesystems/{id}/shares/{shareName}</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param shareName
     *            the name of the share to remove.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> removeShare(URI id, String shareName) {
        return deleteTask(getSharesUrl() + "/{shareName}", id, shareName);
    }

    /**
     * Update file system exports
     * 
     * API Call: <tt>PUT /file/filesystems/{id}/export</tt>
     * 
     * @param id
     *            the ID of the filesystem.
     * @param subDirectory
     *            the subdirectory to be exported
     * @param input
     *            the update/create configuration
     */
    public Task<FileShareRestRep> updateExport(URI id, String subDirectory, FileShareExportUpdateParams input) {
        UriBuilder builder = client.uriBuilder(getExportUrl());
        if (subDirectory != null) {
            builder.queryParam(SUBDIR_PARAM, subDirectory);
        }
        URI targetUri = builder.build(id);
        return putTaskURI(input, targetUri);
    }

    /**
     * Delete file system export rules
     * 
     * API Call: <tt>DELETE /file/filesystems/{id}/export</tt>
     * 
     * @param id
     *            the ID of the file system
     * @param allDir
     *            Boolean to specify all directories
     * @param subDir
     *            specific directory to delete export rules
     */
    public Task<FileShareRestRep> deleteExport(URI id, Boolean allDir, String subDir) {
        UriBuilder builder = client.uriBuilder(getExportUrl());
        if (subDir != null) {
            builder.queryParam(SUBDIR_PARAM, subDir);
        }
        URI targetUri = builder.build(id);
        return deleteTaskURI(targetUri);
    }

    public Task<FileShareRestRep> deleteAllExport(URI id, Boolean allDir) {
        URI targetUri = client.uriBuilder(getExportUrl()).queryParam(ALLDIR_PARAM, allDir).build(id);
        return deleteTaskURI(targetUri);
    }

    /**
     * Gets the share ACLs for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{id}/shares/{shareName}/acl</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param shareName
     *            the shareName to get list of ACLS associated.
     * @return the list of share ACLs for the given file system.
     */
    public List<ShareACL> getShareACLs(URI id, String shareName) {
        ShareACLs response = client.get(ShareACLs.class, getShareACLsUrl(), id, shareName);
        return defaultList(response.getShareACLs());
    }
    
    /**
     * Gets the all NFS ACLs for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{id}/acl?allDir=true</tt>
     * 
     * @param id
     *            the ID of the file system.
     * 
     * @return the list of NFS ACLs for the given file system.
     */
    public List<NfsACL> getAllNfsACLs(URI id) {
        Properties queryParam = new Properties();
        queryParam.setProperty("allDirs", "true");
        NfsACLs response = client.get(NfsACLs.class, getNfsACLsUrl(),
                queryParam, id);
        return defaultList(response.getNfsACLs());
    }

    /**
     * Gets the NFS ACLs for the given file system by ID.
     * <p>
     * API Call: <tt>GET /file/filesystems/{id}/acl?subDir=<subDirId></tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param subDir
     *            the subDir to get list of ACLS associated.
     * @return the list of NFS ACLs for the given file system.
     */
    public List<NfsACL> getNfsACLs(URI id, String subDir) {
        Properties queryParam = new Properties();

        NfsACLs response;
        if (subDir != null && !"null".equals(subDir)) {
            queryParam.setProperty("subDir", subDir);
            response = client.get(NfsACLs.class, getNfsACLsUrl(), queryParam,
                    id);
        } else {
            response = client.get(NfsACLs.class, getNfsACLsUrl(), id);
        }
        return defaultList(response.getNfsACLs());
    }

    /**
     * Update file system NFSv4 ACL
     * 
     * API Call: <tt>PUT /file/filesystems/{id}/acl</tt>
     * 
     * @param id
     *            the ID of the filesystem.
     * @param param
     *            the update/create configuration
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> updateNfsACL(URI id, NfsACLUpdateParams param) {
        UriBuilder builder = client.uriBuilder(getNfsACLsUrl());
        URI targetUri = builder.build(id);
        return putTaskURI(param, targetUri);
    }

    /**
     * Update file system share ACL
     * 
     * API Call: <tt>PUT /file/filesystems/{id}/shares/{shareName}/acl</tt>
     * 
     * @param id
     *            the ID of the filesystem.
     * @param shareName
     *            the shareName to update associated ACLs
     * @param param
     *            the update/create configuration
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> updateShareACL(URI id, String shareName, FileCifsShareACLUpdateParams param) {
        UriBuilder builder = client.uriBuilder(getShareACLsUrl());
        URI targetUri = builder.build(id, shareName);
        return putTaskURI(param, targetUri);
    }

    /**
     * Begins removing a share ACL from the given file system by ID.
     * <p>
     * API Call: <tt>Delete /file/filesystems/{id}/shares/{shareName}/acl</tt>
     * 
     * @param id
     *            the ID of the file system.
     * @param shareName
     *            the name of the share to remove associated ACLs.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<FileShareRestRep> deleteShareACL(URI id, String shareName) {
        return deleteTask(getShareACLsUrl(), id, shareName);
    }
}