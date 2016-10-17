/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.VNXeEthernetPort;

public class EthernetPortRequests extends KHRequests<VNXeEthernetPort> {
    private static final String URL = "/api/instances/ethernetPort/";
    private static final String FIELDS = "health,speed,supportedSpeeds,portNumber,mtu,bond,macAddress,requestedMtu,connectorType,requestedSpeed,supportedMtus,name,storageProcessor";

    public EthernetPortRequests(KHClient client) {
        super(client);
	_fields = FIELDS;
    }

    public VNXeEthernetPort get(String id) {
        _url = URL + id;
        return getDataForOneObject(VNXeEthernetPort.class);
    }

}
