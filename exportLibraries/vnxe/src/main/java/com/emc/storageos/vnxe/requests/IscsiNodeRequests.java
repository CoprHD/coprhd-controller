/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.VNXeIscsiNode;

public class IscsiNodeRequests extends KHRequests<VNXeIscsiNode> {
    private static final String URL = "/api/instances/iscsiNode/";
    private static final String URL_ALL = "/api/types/iscsiNode/instances";
    private static final String FIELDS = "name,alias,ethernetPort.id,ethernetPort.storageProcessor";

    public IscsiNodeRequests(KHClient client) {
        super(client);
        _fields = FIELDS;
    }

    public VNXeIscsiNode get(String id) {
        _url = URL + id ;
        return getDataForOneObject(VNXeIscsiNode.class);
    }

    public List<VNXeIscsiNode> getAllNodes() {
        _url = URL_ALL;
        return getDataForObjects(VNXeIscsiNode.class);
    }

}
