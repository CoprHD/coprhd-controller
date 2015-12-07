/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class FileSystemListRequest extends KHRequests<VNXeFileSystem> {
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemListRequest.class);
    private static final String URL = "/api/types/filesystem/instances";

    public FileSystemListRequest(KHClient client) {
        super(client);
        _url = URL;
    }

    /**
     * Get all file systems in the array
     * 
     * @return List of VNXeFileSystem
     */
    public List<VNXeFileSystem> get() {
        _queryParams = null;
        return getDataForObjects(VNXeFileSystem.class);

    }

    /**
     * Get file system using its storageResourceId
     * 
     * @param storageResourceId
     * @return
     */
    public VNXeFileSystem getByStorageResource(String storageResourceId) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.STORAGE_RESOURCE_FILTER + storageResourceId);
        setQueryParameters(queryParams);
        VNXeFileSystem result = null;
        List<VNXeFileSystem> fsList = getDataForObjects(VNXeFileSystem.class);
        // it should just return 1
        if (fsList != null && !fsList.isEmpty()) {
            result = fsList.get(0);
        } else {
            _logger.info("No file system found using the storage resource id: " + storageResourceId);
        }
        return result;
    }

    /**
     * Get file system using its name
     * 
     * @param fsName fileSystem name
     * @return VNXeFileSystem
     */
    public VNXeFileSystem getByFSName(String fsName) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.NAME_FILTER + "\"" + fsName + "\"");
        setQueryParameters(queryParams);
        VNXeFileSystem result = null;
        List<VNXeFileSystem> fsList = getDataForObjects(VNXeFileSystem.class);
        // it should just return 1
        if (fsList != null && !fsList.isEmpty()) {
            result = fsList.get(0);
        } else {
            _logger.info("No file system found using the name: " + fsName);
        }
        return result;
    }

}
