/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.CifsShareCreateForSnapParam;
import com.emc.storageos.vnxe.models.VNXeCifsShare;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class CifsShareRequests extends KHRequests<VNXeCifsShare> {

    private static final String URL = "/api/types/cifsShare/instances";
    private static final String URL_SHARE = "/api/instances/cifsShare/";

    public CifsShareRequests(KHClient client) {
        super(client);
        _url = URL;
    }

    /**
     * Get all cifsShares in the array
     * 
     * @return
     */
    public List<VNXeCifsShare> get() {
        _queryParams = null;
        return getDataForObjects(VNXeCifsShare.class);

    }

    /**
     * Get cifs share per its name.
     * 
     * @param shareName cifsShare name
     * @return list of cifsShare
     */
    public List<VNXeCifsShare> getCifsShareByName(String shareName) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, VNXeConstants.NAME_FILTER + "\"" + shareName + "\"");
        setQueryParameters(queryParams);
        return getDataForObjects(VNXeCifsShare.class);
    }

    /**
     * Create CIFS share for snapshot
     * 
     * @param createParam
     * @return
     */
    public VNXeCommandJob createShareForSnapshot(CifsShareCreateForSnapParam createParam) {
        return postRequestAsync(createParam);
    }

    /**
     * Delete CIFS share
     * 
     * @param shareId cifsShare id
     * @return VNXeCommandJob
     */
    public VNXeCommandJob deleteShareForSnapshot(String shareId) {
        _url = URL_SHARE + shareId;
        if (getShare(shareId) != null) {
            return deleteRequestAsync(null);
        } else {
            throw VNXeException.exceptions.vnxeCommandFailed("The shareId is not found: " + shareId);
        }
    }

    /**
     * Get the specific CIFS share
     * 
     * @param shareId
     * @return
     */
    public VNXeCifsShare getShare(String shareId) {
        _url = URL_SHARE + shareId;
        return getDataForOneObject(VNXeCifsShare.class);
    }
}
