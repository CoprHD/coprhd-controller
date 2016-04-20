/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.SnapCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeSnapRestoreParam;

/**
 * This is the Snap requests class for VNX Unity
 */
public class SnapRequests extends KHRequests<Snap> {
    private static final Logger _logger = LoggerFactory.getLogger(SnapRequests.class);
    private static final String URL = "/api/types/snap/instances";
    private static final String URL_INSTANCE = "/api/instances/snap/";
    private static final String URL_RESTORE = "/action/restore";
    private static final String URL_ATTACH = "/action/attach";
    private static final String URL_DETACH = "/action/detach";
    private static final String FIELDS = "attachedWWN,isSystemSnap,size,lun,storageResource,name,snapGroup,parentSnap,description,creationTime,"
            + "expirationTime,isModifiable,isReadOnly,lastWritableTime,isAutoDelete,state";

    public SnapRequests(KHClient client) {
        super(client);
        _url = URL;
        _fields = FIELDS;
    }

    /**
     * create snap in async mode
     * 
     * @param param: SnapCreateParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createSnap(SnapCreateParam param) throws VNXeException {

        return postRequestAsync(param);
    }

    /**
     * Delete lun snap
     * 
     * @param snapId
     * @return
     * @throws VNXeException
     */
    public VNXeCommandResult deleteSnap(String snapId) throws VNXeException {
        _url = URL_INSTANCE + snapId;
        setQueryParameters(null);
        deleteRequest(null); 
        VNXeCommandResult result = new VNXeCommandResult();
        result.setSuccess(true);
        return result;

    }

    /**
     * Restore snapshot
     * 
     * @param snapId snapshot VNXe Id
     * @param restoreParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob restoreSnap(String snapId, VNXeSnapRestoreParam restoreParam)
            throws VNXeException {

        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_RESTORE);
        _url = urlBuilder.toString();

        return postRequestAsync(restoreParam);

    }

    /**
     * attach snap
     * 
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob attachSnap(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_ATTACH);
        _url = urlBuilder.toString();
        return postRequestAsync(null);
    }

    public VNXeCommandResult attachSnapSync(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_ATTACH);
        _url = urlBuilder.toString();
        return postRequestSync(null);
    }

    /**
     * detach snap
     * 
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob detachSnap(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_DETACH);
        _url = urlBuilder.toString();
        return postRequestAsync(null);
    }

    /**
     * detach snap synchronously
     * 
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandResult detachSnapSync(String snapId) throws VNXeException {
        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_DETACH);
        _url = urlBuilder.toString();
        return postRequestSync(null);
    }

    /**
     * Get a Lun's snaps by its storageResource id.
     * 
     * @param resourceId lun Id
     * @return list of VNXeLunSnap
     */
    public List<Snap> getLunSnaps(String resourceId) {
        setFilter(VNXeConstants.LUN_FILTER + "\"" + resourceId + "\"");
        return getDataForObjects(Snap.class);
    }

    /**
     * Get snapshot details by its name
     * 
     * @param name
     * @return
     */
    public Snap getSnapByName(String name) {
        String filter = VNXeConstants.NAME_FILTER + "\"" + name + "\"";
        setFilter(filter);

        Snap result = null;
        List<Snap> snapList = getDataForObjects(Snap.class);
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
    public Snap getSnap(String snapId) {
        _url = URL_INSTANCE + snapId;
        return getDataForOneObject(Snap.class);
    }
    
    public List<Snap> getSnapsBySnapGroupId(String snapGroupId) {
        String filter = VNXeConstants.SNAP_GROUP_FILTER + "\"" + snapGroupId + "\"";
        setFilter(filter);
        return getDataForObjects(Snap.class);
    }
}
