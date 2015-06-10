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
import com.emc.storageos.vnxe.models.NfsShareCreateForSnapParam;
import com.emc.storageos.vnxe.models.NfsShareModifyForShareParam;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeNfsShare;

public class NfsShareRequests extends KHRequests<VNXeNfsShare> {
	private static final Logger _logger = LoggerFactory.getLogger(FileSystemListRequest.class);
    private static final String URL = "/api/types/nfsShare/instances";
    private static final String URL_NFS = "/api/instances/nfsShare/";
    private static final String URL_MODIFY = "/action/modify";
    
    public NfsShareRequests(KHClient client) {
    	super(client);
        _url = URL;
    }
    
    /**
     * get list of VNXeNfsShare in the array
     *
     */
    public List<VNXeNfsShare> get(){
    	_queryParams = null;
        return getDataForObjects(VNXeNfsShare.class);

    }
    
    /**
     * find nfsShare using fileSystem id and share name
     * @param fsId fileSystem Id
     * @param shareName 
     * @return
     */
    public VNXeNfsShare findNfsShare (String fsId, String shareName) {
    	
    	StringBuilder queryFilter = new StringBuilder(VNXeConstants.NAME_FILTER);
    	queryFilter.append(shareName);
    	queryFilter.append(VNXeConstants.AND);
    	queryFilter.append(VNXeConstants.FILE_SYSTEM_FILTER);
    	queryFilter.append(fsId);
    	setFilter(queryFilter.toString());

    	VNXeNfsShare result = null;
    	List<VNXeNfsShare> shareList = getDataForObjects(VNXeNfsShare.class);
    	//it should just return 1
    	if (shareList!= null && shareList.size()>0) {
    		result =shareList.get(0);
    	} else {
    		_logger.info("No file system found using the fs id: {}, nfsShare name: {} ", fsId, shareName);
    	}
    	return result;
    }
    
    public VNXeNfsShare findSnapNfsShare (String snapId, String shareName) {

        StringBuilder queryFilter = new StringBuilder(VNXeConstants.SNAP_FILTER);
        queryFilter.append(snapId);
        queryFilter.append(VNXeConstants.AND);
        queryFilter.append(VNXeConstants.NAME_FILTER);
        queryFilter.append(shareName);
        setFilter(queryFilter.toString());

        VNXeNfsShare result = null;
        List<VNXeNfsShare> shareList = getDataForObjects(VNXeNfsShare.class);
        //it should just return 1
        if (shareList!= null && shareList.size()>0) {
            result =shareList.get(0);
        } else {
            _logger.info("No file system found using the fs id: {}, nfsShare name: {} ", snapId, shareName);
        }
        return result;
    }
    
    /**
     * Create NfsShare for snapshot
     * @param createParam create nfs param
     * @return
     */
    public VNXeCommandJob createShareForSnapshot(NfsShareCreateForSnapParam createParam) {
        return postRequestAsync(createParam);
    }
    
    /**
     * Modify nfsShare for  snapshot
     * @param shareId
     * @param param
     * @return
     */
    public VNXeCommandJob modifyShareForSnapshot(String shareId, NfsShareModifyForShareParam param) {
        StringBuilder urlBuilder = new StringBuilder(URL_NFS);
        urlBuilder.append(shareId);
        urlBuilder.append(URL_MODIFY);
        _url = urlBuilder.toString();
        return postRequestAsync(param);
        
    }
    
    /**
     * Delete NfsShare for snapshot
     * @param shareId
     * @return
     */
    public VNXeCommandJob deleteShareForSnapshot(String shareId) {
        _url = URL_NFS + shareId;
        return deleteRequestAsync(null);
        
    }
    
    /**
     * Get the specific NFS share's details
     * @return
     */
    public VNXeNfsShare getShareById(String shareId) throws VNXeException{
    	_url = URL_NFS + shareId;
        setQueryParameters(null);
        return getDataForOneObject(VNXeNfsShare.class);

    }
}
