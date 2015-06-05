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

import com.emc.storageos.vnxe.models.VNXeEthernetPort;

public class EthernetPortRequests extends KHRequests<VNXeEthernetPort>{
    private static final String URL = "/api/instances/ethernetPort/";
    
    public EthernetPortRequests(KHClient client) {
        super(client);
    }
    
    public VNXeEthernetPort get(String id) {
        _url = URL + id;
        return getDataForOneObject(VNXeEthernetPort.class);
    }

}
