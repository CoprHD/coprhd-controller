/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.Disk;
import com.emc.storageos.vnxe.VNXeConstants;

public class DiskRequest extends KHRequests<Disk> {

    private static final String URL = "/api/types/disk/instances";
    private static final String FIELDS = "name,diskTechnology,pool";

    public DiskRequest(KHClient client) {
        super(client);
        _url = URL ;
        _fields = FIELDS;
    }

    public List<Disk> get() {
        return getDataForObjects(Disk.class);
    }

/**
     * get pool's disks.
     *
     * @param poolId pool internal id
     * @return list of disks
     */
    public List<Disk> getDisksForPool(String poolId) {
        setFilter(VNXeConstants.POOL_FILTER + "\"" + poolId + "\"");

        return getDataForObjects(Disk.class);
    }


}
