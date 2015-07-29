/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.LunSnapCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.vnxe.models.VNXeSnapRestoreParam;

public class LunSnapRequests extends KHRequests<VNXeLunSnap> {

    private static final Logger _logger = LoggerFactory.getLogger(LunSnapRequests.class);
    private static final String URL = "/api/types/lunSnap/instances";
    private static final String URL_INSTANCE = "/api/instances/lunSnap/";
    private static final String URL_RESTORE = "/action/restore";
    private static final String URL_ATTACH = "/action/attach";
    private static final String URL_DETACH = "/action/detach";

    public LunSnapRequests(KHClient client) {
        super(client);
        _url = URL;
    }

    /**
     * create lun snap in async mode
     * 
     * @param param: LunSnapCreateParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createLunSnap(LunSnapCreateParam param) throws VNXeException {

        return postRequestAsync(param);
    }

    /**
     * Delete lun snap
     * 
     * @param snapId
     * @return
     * @throws VNXeException
     */
    public VNXeCommandJob deleteLunSnap(String snapId) throws VNXeException {
        _url = URL_INSTANCE + snapId;
        setQueryParameters(null);
        if (getDataForOneObject(VNXeLunSnap.class) != null) {
            return deleteRequestAsync(null);
        } else {
            throw VNXeException.exceptions.vnxeCommandFailed(String.format("No lun snap %s found",
                    snapId));
        }

    }

    /**
     * Restore lun snapshot
     * 
     * @param snapId snapshot VNXe Id
     * @param restoreParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob restoreLunSnap(String snapId, VNXeSnapRestoreParam restoreParam)
            throws VNXeException {

        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_RESTORE);
        _url = urlBuilder.toString();

        return postRequestAsync(restoreParam);

    }

    /**
     * attach lun snap
     * 
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob attachLunSnap(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_ATTACH);
        _url = urlBuilder.toString();
        return postRequestAsync(null);
    }

    public VNXeCommandResult attachLunSnapSync(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_ATTACH);
        _url = urlBuilder.toString();
        return postRequestSync(null);
    }

    /**
     * detach lun snap
     * 
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob detachLunSnap(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_DETACH);
        _url = urlBuilder.toString();
        return postRequestAsync(null);
    }

    /**
     * detach lun snap synchronously
     * 
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandResult detachLunSnapSync(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_DETACH);
        _url = urlBuilder.toString();
        return postRequestSync(null);
    }

    /**
     * Get a lun's snaps by its storageResource id.
     * 
     * @param resourceId lun Id
     * @return list of VNXeLunSnap
     */
    public List<VNXeLunSnap> getLunSnaps(String resourceId) {
        setFilter(VNXeConstants.LUN_FILTER + resourceId);
        return getDataForObjects(VNXeLunSnap.class);
    }

    /**
     * Get snapshot details by its name
     * 
     * @param name
     * @return
     */
    public VNXeLunSnap getLunSnapByName(String name) {

        setFilter(VNXeConstants.NAME_FILTER + name);

        VNXeLunSnap result = null;
        List<VNXeLunSnap> snapList = getDataForObjects(VNXeLunSnap.class);
        // it should just return 1
        if (snapList != null && !snapList.isEmpty()) {
            result = snapList.get(0);
        } else {
            _logger.info("No lun snapshot found using the name: " + name);
        }
        return result;
    }

    /**
     * Get snapshot details by its id
     * 
     * @param id
     * @return
     */
    public VNXeLunSnap getLunSnap(String snapId) {
        _url = URL_INSTANCE + snapId;
        return getDataForOneObject(VNXeLunSnap.class);
    }

}
