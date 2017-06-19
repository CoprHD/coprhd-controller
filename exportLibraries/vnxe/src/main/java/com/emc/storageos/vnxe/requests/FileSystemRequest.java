/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXeFileSystem;

public class FileSystemRequest extends KHRequests<VNXeFileSystem> {
    private static final String URL = "/api/instances/filesystem/";
    private static final String FIELDS = "isThinEnabled,sizeAllocated,supportedProtocols,storageResource,nasServer,nfsShare,accessPolicy,sizeTotal,sizeUsed,cifsShareCount,nfsShareCount";

    public FileSystemRequest(KHClient client, String id) {
        super(client);
        _url = URL + id;
        _fields = FIELDS;
    }

    /**
     * Get the specific file system details
     * 
     * @return
     */
    public VNXeFileSystem get() {
        return getDataForOneObject(VNXeFileSystem.class);

    }

    /**
     * Get the specific file system details
     * 
     * @return
     */
    public VNXeFileSystem get(String id) {
        _url = URL + id;
        return getDataForOneObject(VNXeFileSystem.class);

    }

}
