/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.ModifyFileSystemParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.CreateFileSystemParam;
import com.emc.storageos.vnxe.models.StorageResource;

public class FileSystemActionRequest extends KHRequests<StorageResource> {

    private static final String URL_CREATE = "/api/types/storageResource/action/createFilesystem";
    private static final String URL_RESOURCE = "/api/instances/storageResource/";
    private static final String MODIFY = "/action/modifyFilesystem";

    public FileSystemActionRequest(KHClient client) {
        super(client);
    }

    /**
     * create file system with async call
     * 
     * @param param: CreateFileSystemParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createFileSystemAsync(CreateFileSystemParam param) throws VNXeException {
        _url = URL_CREATE;

        return postRequestAsync(param);
    }

    /**
     * create file system with sync call
     * 
     * @param param: CreateFileSystemParam
     * @return VNXeCommandResult
     * @throws VNXeException
     */
    public VNXeCommandResult createFileSystemSync(CreateFileSystemParam param) throws VNXeException {
        _url = URL_CREATE;
        return postRequestSync(param);

    }

    /**
     * modify file system, export/unexport, etc
     * 
     * @param param: ModifyFileSystemParam
     * @param resourceId: storage resource Id
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob modifyFileSystemAsync(ModifyFileSystemParam param, String resourceId) throws VNXeException {
        StringBuilder urlBld = new StringBuilder(URL_RESOURCE);
        urlBld.append(resourceId);
        urlBld.append(MODIFY);
        _url = urlBld.toString();

        return postRequestAsync(param);
    }

}