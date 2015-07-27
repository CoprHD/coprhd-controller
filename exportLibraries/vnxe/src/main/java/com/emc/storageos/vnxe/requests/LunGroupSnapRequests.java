/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.LunSnapCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLunGroupSnap;
import com.emc.storageos.vnxe.models.VNXeSnapRestoreParam;

public class LunGroupSnapRequests extends KHRequests<VNXeLunGroupSnap> {

	private static final Logger _logger = LoggerFactory.getLogger(LunGroupSnapRequests.class);
    private static final String URL = "/api/types/lunGroupSnap/instances";
    private static final String URL_INSTANCE = "/api/instances/lunGroupSnap/";
    private static final String URL_RESTORE="/action/restore";
    private static final String URL_ATTACH="action/attach";
    private static final String URL_DETACH="action/detach";
	
	public LunGroupSnapRequests(KHClient client) {
		super(client);
		_url = URL;
	}
	
	/**
     * create lun group snap in async mode
     * @param param: LunSnapCreateParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob createLunGroupSnap(LunSnapCreateParam param) throws VNXeException {
        
        return postRequestAsync(param);
    }
    
    /**
     * Delete lun group snap
     * @param snapId
     * @return
     * @throws VNXeException
     */
    public VNXeCommandJob deleteLunGroupSnap(String snapId) throws VNXeException {
        _url = URL_INSTANCE + snapId;
        setQueryParameters(null);
        if (getDataForOneObject(VNXeLunGroupSnap.class) != null) {
            return deleteRequestAsync (null);
        } else {
            throw VNXeException.exceptions.vnxeCommandFailed(String.format("No lun group snap %s found",
                    snapId));
        }
        
        
    }
    
    /**
     * Restore lun group snapshot
     * @param snapId snapshot VNXe Id
     * @param restoreParam
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob restoreLunGroupSnap(String snapId, VNXeSnapRestoreParam restoreParam)
            throws VNXeException {

        StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_RESTORE);
        _url = urlBuilder.toString();

        return postRequestAsync (restoreParam);

    
    }
    
    /**
     * attach lun group snap
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob attachLunGroupSnap(String snapId) throws VNXeException{
    	StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_ATTACH);
        _url = urlBuilder.toString();
        return  postRequestAsync(null);
    }
    
    /**
     * detach lun group snap
     * @param snapId
     * @return VNXeCommandJob
     * @throws VNXeException
     */
    public VNXeCommandJob detachLunGroupSnap(String snapId) throws VNXeException{
    	StringBuilder urlBuilder = new StringBuilder(URL_INSTANCE);
        urlBuilder.append(snapId);
        urlBuilder.append(URL_DETACH);
        _url = urlBuilder.toString();
        return  postRequestAsync(null);
    }
    
    /**
     * Get a lun group's snaps by its storageResource id.
     * @param resourceId lun group Id
     * @return list of VNXeLunSnap
     */
    public List<VNXeLunGroupSnap> getLunGroupSnaps(String resourceId) {

        setFilter(VNXeConstants.STORAGE_RESOURCE_FILTER+resourceId);

        return getDataForObjects(VNXeLunGroupSnap.class);
    }
    
    /**
     * Get snapshot details by its name
     * @param name
     * @return
     */
    public VNXeLunGroupSnap getLunGroupSnapByName(String name) {
        
        setFilter(VNXeConstants.NAME_FILTER+name);
        
        VNXeLunGroupSnap result = null;
        List<VNXeLunGroupSnap> snapList = getDataForObjects(VNXeLunGroupSnap.class);
        //it should just return 1
        if (snapList!= null && !snapList.isEmpty()) {
            result =snapList.get(0);
        } else {
            _logger.info("No lun group snapshot found using the name: " +name);
        }
        return result;
    }
    
    /**
     * Get lun group snapshot details by its id
     * @param id
     * @return
     */
    public VNXeLunGroupSnap getLunGroupSnap(String snapId) {
    	_url = URL_INSTANCE + snapId;
        return getDataForOneObject(VNXeLunGroupSnap.class);
    }


}
