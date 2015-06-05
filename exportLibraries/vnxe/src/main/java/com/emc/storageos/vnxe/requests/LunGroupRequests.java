/**
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

import com.emc.storageos.vnxe.models.LunGroupCreateParam;
import com.emc.storageos.vnxe.models.LunGroupModifyParam;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;

public class LunGroupRequests extends KHRequests<StorageResource>{
    private static String URL_CREATE = "/api/types/storageResource/action/createLunGroup";
    private static String URL_MODIFY1 = "/api/instances/storageResource/";
    private static String URL_MODIFY2 = "/action/modifyLunGroup";
    public LunGroupRequests(KHClient client) {
        super(client);
    }
    
    public VNXeCommandResult createLunGroup(LunGroupCreateParam createParam) {
        _url = URL_CREATE;
        return postRequestSync(createParam);
    }
    
    public VNXeCommandJob modifyLunGroupAsync(String id, LunGroupModifyParam param) {
        StringBuilder urlBld = new StringBuilder(URL_MODIFY1);
        urlBld.append(id);
        urlBld.append(URL_MODIFY2);
        _url = urlBld.toString();
        
        return postRequestAsync(param);
        
    }
    
    public VNXeCommandResult modifyLunGroupSync(String id, LunGroupModifyParam param) {
        StringBuilder urlBld = new StringBuilder(URL_MODIFY1);
        urlBld.append(id);
        urlBld.append(URL_MODIFY2);
        _url = urlBld.toString();
        
        return postRequestSync(param);
        
    }
}
