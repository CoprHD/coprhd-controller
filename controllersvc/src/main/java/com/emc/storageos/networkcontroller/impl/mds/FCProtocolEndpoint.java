/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.HashMap;
import java.util.Map;

public class FCProtocolEndpoint {
    
    public static Map<String, FCProtocolEndpoint> wwpnToFCEndpoint = new HashMap<String, FCProtocolEndpoint>();
    
    String wwpn;
    String wwnn;
    Interface iface;            // interface implementing this endpoint
    Object cimPath;
    Map<String, FCProtocolEndpoint> connections = new HashMap<String, FCProtocolEndpoint>(); // key is wwpn
}
