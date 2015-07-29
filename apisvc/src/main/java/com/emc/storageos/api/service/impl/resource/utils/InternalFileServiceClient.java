/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.storageos.model.file.FileSystemExportList;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.model.file.FileExportUpdateParam;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.helpers.BaseServiceClient;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.emc.storageos.model.TaskResourceRep;

/**
 * This class provides access to the file services API for the data nodes and control operations
 * such as create, delete, unexport and export
 */
public class InternalFileServiceClient extends BaseServiceClient {

    private static final String INTERNAL_FILE_CREATE = "/internal/file/filesystems";
    private static final String INTERNAL_FILE_ROOT = INTERNAL_FILE_CREATE + "/";
    private static final String EXPORTS = "/exports";
    private static final String MODIFYEXPORTS = "/internal/file/filesystems/%s/exports/%s,%s,%s,%s";
    private static final String UNEXPORTS = "/internal/file/filesystems/%s/exports/%s,%s,%s,%s";
    private static final String DEACTIVATE = "/deactivate";
    private static final String RELEASE = "/release";
    private static final String RELEASE_UNDO = RELEASE + "/undo";
    private static final String TASK = "/tasks/";

    private static final String SUB_DIRECTORY_QUERY_KEY = "subDirectory";
    private static final String TASK_QUERY_KEY = "task";

    final private Logger _log = LoggerFactory
            .getLogger(InternalFileServiceClient.class);

    /**
     * Client without target hosts
     */
    public InternalFileServiceClient() {
    }

    /**
     * Client with specific host
     * 
     * @param server
     */
    public InternalFileServiceClient(String server) {
        setServer(server);
    }

    /**
     * Make client associated with this api server host (IP)
     * 
     * @param server IP
     */
    @Override
    public void setServer(String server) {
        setServiceURI(URI.create("https://" + server + ":8443"));
    }

    /**
     * Create file system
     * 
     * @param fileSystemParam
     * @param token user authentication token
     * @return
     */
    public TaskResourceRep createFileSystem(
            FileSystemParam fileSystemParam,
            String token) {

        WebResource rRoot = createRequest(INTERNAL_FILE_CREATE);
        WebResource.Builder requestBuilder = addSignature(rRoot);
        TaskResourceRep resp = addToken(requestBuilder, token)
                .post(TaskResourceRep.class, fileSystemParam);
        return resp;
    }

    /**
     * Export a file share
     * 
     * @param fsId
     * @param exportParam
     * @return
     */
    public TaskResourceRep exportFileSystem(URI fsId, FileSystemExportParam exportParam) {

        WebResource rRoot = createRequest(INTERNAL_FILE_ROOT + fsId + EXPORTS);
        TaskResourceRep resp = addSignature(rRoot)
                .post(TaskResourceRep.class, exportParam);
        return resp;
    }

    /**
     * Modify existing export for a file share
     * 
     * @param fsId fileshare id
     * @param protocol protocol for the existing export
     * @param securityType security type for the existing export
     * @param permissions permissios for the existing export
     * @param rootUserMapping usermapping for the existing export
     * @param updateParam export update param, which contains a list of end points which needs to be added/removed for
     *            this export
     * @return Task in
     */
    public TaskResourceRep modifyExports(URI fsId, String protocol,
            String securityType, String permissions, String rootUserMapping,
            FileExportUpdateParam updateParam) {

        String modifyExportPath = String.format(MODIFYEXPORTS, fsId, protocol,
                securityType, permissions, rootUserMapping);
        WebResource rRoot = createRequest(modifyExportPath);
        TaskResourceRep resp = null;
        try {
            resp = addSignature(rRoot)
                    .put(TaskResourceRep.class, updateParam);
        } catch (UniformInterfaceException e) {
            _log.warn("could not modify exports", e);
        }
        return resp;

    }

    /**
     * Unexport a file share
     * 
     * @param fsId
     * @param protocol
     * @param securityType
     * @param permissions
     * @param rootUserMapping
     * @param subDirectory
     * @return
     */
    public TaskResourceRep unexportFileSystem(URI fsId, String protocol,
            String securityType, String permissions, String rootUserMapping,
            String subDirectory) {

        String unexportPath = String.format(UNEXPORTS, fsId, protocol,
                securityType, permissions, rootUserMapping);
        WebResource rRoot = createRequest(unexportPath);
        // add query params before signing the request
        if (StringUtils.isNotEmpty(subDirectory)) {
            rRoot = rRoot.queryParam(SUB_DIRECTORY_QUERY_KEY, subDirectory);
        }

        TaskResourceRep resp = null;
        try {
            resp = addSignature(rRoot)
                    .delete(TaskResourceRep.class);
        } catch (UniformInterfaceException e) {
            _log.warn("could not unexport", e);
        }
        return resp;
    }

    /**
     * Delete file system (must be unexported first)
     * 
     * @param fsId file system ID
     * @param token user authentication token
     * @param fileSystemDeleteParam parameter for file system deletion
     * @return
     */
    public TaskResourceRep deactivateFileSystem(URI fsId, String token,
            FileSystemDeleteParam fileSystemDeleteParam) {

        WebResource rRoot = createRequest(INTERNAL_FILE_ROOT + fsId + DEACTIVATE);
        WebResource.Builder requestBuilder = addSignature(rRoot);
        TaskResourceRep resp = addToken(requestBuilder, token)
                .post(TaskResourceRep.class, fileSystemDeleteParam);
        return resp;
    }

    /**
     * Get list of exports for this file system
     * 
     * @param fsId file share ID
     * @return
     */
    public FileSystemExportList getFileSystemExportList(URI fsId) {

        WebResource rRoot = createRequest(INTERNAL_FILE_ROOT + fsId + EXPORTS);
        FileSystemExportList resp = addSignature(rRoot)
                .get(FileSystemExportList.class);
        return resp;
    }

    /**
     * Get the status of a file system task
     * 
     * @param fsId file share ID
     * @param task task ID
     * @return
     */
    public TaskResourceRep getTaskStatus(URI fsId, String task) {

        WebResource rRoot = createRequest(INTERNAL_FILE_ROOT + fsId + TASK + task);
        TaskResourceRep resp = addSignature(rRoot)
                .get(TaskResourceRep.class);
        return resp;
    }

    /**
     * Release a ViPR file system for use by Object
     * 
     * @param fileSystemParam
     * @param token user authentication token
     * @return a response object containing the previously set tenant and project
     */
    public FileShareRestRep releaseFileSystem(URI fsId, StorageOSUser user) {

        WebResource rRoot = createRequest(INTERNAL_FILE_ROOT + fsId + RELEASE);
        WebResource.Builder requestBuilder = addSignature(rRoot);
        FileShareRestRep resp = addTokens(requestBuilder, user.getToken(), user.getProxyToken())
                .post(FileShareRestRep.class);
        return resp;
    }

    /**
     * Release a ViPR file system for use by Object
     * 
     * @param fileSystemParam
     * @return
     * @throws Exception
     * @throws ClientHandlerException
     * @throws UniformInterfaceException
     */
    public FileShareRestRep undoReleaseFileSystem(URI fsId) {
        WebResource rRoot = createRequest(INTERNAL_FILE_ROOT + fsId + RELEASE_UNDO);
        FileShareRestRep resp = addSignature(rRoot).post(FileShareRestRep.class);
        return resp;
    }
}
