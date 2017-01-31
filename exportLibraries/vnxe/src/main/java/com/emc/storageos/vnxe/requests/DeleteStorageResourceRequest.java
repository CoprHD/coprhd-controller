/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.DeleteStorageResourceParam;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.vnxe.models.VNXeLun;

public class DeleteStorageResourceRequest extends KHRequests<StorageResource> {
    private static final Logger _logger = LoggerFactory.getLogger(DeleteStorageResourceRequest.class);
    private static final String URL = "/api/instances/storageResource/";

    public DeleteStorageResourceRequest(KHClient client) {
        super(client);
        _url = URL;
    }

    /**
     * Delete file system in async mode
     * 
     * @param fileSystemId
     * @param isForceSnapDeletion
     * @return
     * @throws VNXeException
     */
    public VNXeCommandJob deleteFileSystemAsync(String fileSystemId, boolean isForceSnapDeletion)
            throws VNXeException {
        String resourceId = getStorageResourceId(fileSystemId);
        if (resourceId == null || resourceId.isEmpty()) {
            String error = "Could not find corresponding storage resource Id for the " +
                    "file system Id:" + fileSystemId;
            _logger.error(error);
            throw VNXeException.exceptions.vnxeCommandFailed(error);
        }
        return deleteResourceAsync(resourceId, isForceSnapDeletion);

    }

    /**
     * Delete the file system in sync mode
     * 
     * @param fileSystemId
     * @param isForceSnapDeletion
     * @return VNXeCommandResult
     * @throws VNXeException
     */
    public VNXeCommandResult deleteFileSystemSync(String fileSystemId, boolean isForceSnapDeletion) throws VNXeException {
        String resourceId = getStorageResourceId(fileSystemId);
        if (resourceId == null || resourceId.isEmpty()) {
            String error = "Could not find corresponding storage resource Id for the " +
                    "file system Id:" + fileSystemId;
            _logger.error(error);
            throw VNXeException.exceptions.vnxeCommandFailed(error);
        }
        return deleteResourceSync(resourceId, isForceSnapDeletion);
    }

    /**
     * Get storageResource Id using filesystem Id
     * 
     * @param fsId fileSystem Id
     * @return storageResource Id
     */
    private String getStorageResourceId(String fsId) {
        FileSystemRequest fsReq = new FileSystemRequest(_client, fsId);
        VNXeFileSystem fs = fsReq.get();
        VNXeBase resource = fs.getStorageResource();
        String result = null;
        if (resource != null) {
            result = resource.getId();
        }
        return result;
    }

    public VNXeCommandResult deleteLunSync(String id, boolean isForceSnapDeletion) throws VNXeException {
        _logger.info("deleting lun : {}", id);
        BlockLunRequests req = new BlockLunRequests(_client);
        VNXeLun lun = req.getLun(id);
        if (lun == null) {
            String error = "Could not find lun: " + id;
            _logger.error(error);
            throw VNXeException.exceptions.vnxeCommandFailed(error);
        }
        return deleteResourceSync(id, isForceSnapDeletion);

    }

    /**
     * Delete lun in async mode
     * 
     * @param lunId
     * @param isForceSnapDeletion
     * @return
     * @throws VNXeException
     */
    public VNXeCommandJob deleteLunAsync(String lunId, boolean isForceSnapDeletion)
            throws VNXeException {
        BlockLunRequests req = new BlockLunRequests(_client);
        VNXeLun lun = req.getLun(lunId);
        if (lun == null) {
            String error = "Could not find lun: " + lunId;
            _logger.error(error);
            throw VNXeException.exceptions.vnxeCommandFailed(error);
        }

        return deleteResourceAsync(lunId, isForceSnapDeletion);

    }

    /**
     * Delete lun group
     * 
     * @param groupId
     * @param isForceSnapDeletion
     * @return
     * @throws VNXeException
     */
    public VNXeCommandResult deleteLunGroup(String groupId, boolean isForceSnapDeletion)
            throws VNXeException {
        StorageResourceRequest req = new StorageResourceRequest(_client);
        StorageResource group = req.get(groupId);
        if (group == null) {
            String error = "Could not find lun group: " + groupId;
            _logger.error(error);
            throw VNXeException.exceptions.vnxeCommandFailed(error);
        }

        return deleteResourceSync(groupId, isForceSnapDeletion);
    }

    private VNXeCommandJob deleteResourceAsync(String resourceId, boolean isForceSnapDeletion) {
        _url = URL + resourceId;
        DeleteStorageResourceParam parm = new DeleteStorageResourceParam();
        parm.setForceSnapDeletion(isForceSnapDeletion);
        return deleteRequestAsync(parm);
    }

    private VNXeCommandResult deleteResourceSync(String resourceId, boolean isForceSnapDeletion) {
        _url = URL + resourceId;
        DeleteStorageResourceParam parm = new DeleteStorageResourceParam();
        parm.setForceSnapDeletion(isForceSnapDeletion);
        deleteRequest(parm);
        VNXeCommandResult result = new VNXeCommandResult();
        result.setSuccess(true);
        return result;
    }

}
