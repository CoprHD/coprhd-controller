/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.api.service.impl.resource.utils;

import java.util.HashMap;

public class StorageSystemTypeServiceUtils {

    private static final String ISILON = "isilon";
    private static final String VNX_BLOCK = "vnxblock";
    private static final String VNXe = "vnxe";
    private static final String VNX_FILE = "vnxfile";
    private static final String VMAX = "smis";
    private static final String NETAPP = "netapp";
    private static final String NETAPPC = "netappc";
    private static final String HITACHI = "hds";
    private static final String IBMXIV = "ibmxiv";
    private static final String VPLEX = "vplex";
    private static final String OPENSTACK = "openstack";
    private static final String SCALEIO = "scaleio";
    private static final String SCALEIOAPI = "scaleioapi";
    private static final String XTREMIO = "xtremio";
    private static final String DATA_DOMAIN = "datadomain";
    private static final String ECS = "ecs";

    public static void initializeDisplayName(HashMap<String, String> nameDisplayNameMap) {
        nameDisplayNameMap.put(VNX_FILE, "EMC VNX File");
        nameDisplayNameMap.put(ISILON, "EMC Isilon");
        nameDisplayNameMap.put(NETAPP, "NetApp 7-mode");
        nameDisplayNameMap.put(NETAPPC, "NetApp Cluster-mode");
        nameDisplayNameMap.put(SCALEIOAPI, "ScaleIO Gateway");
        nameDisplayNameMap.put(VNX_BLOCK, "EMC VNX Block");
        nameDisplayNameMap.put(VNXe, "EMC VNXe");
        nameDisplayNameMap.put(VMAX, "Storage Provider for EMC VMAX or VNX Block");
        nameDisplayNameMap.put(HITACHI, "Storage Provider for Hitachi storage systems");
        nameDisplayNameMap.put(VPLEX, "Storage Provider for EMC VPLEX");
        nameDisplayNameMap.put(OPENSTACK, "Storage Provider for Third-party block storage systems");
        nameDisplayNameMap.put(SCALEIO, "Block Storage Powered by ScaleIO");
        nameDisplayNameMap.put(DATA_DOMAIN, "Storage Provider for Data Domain Management Center");
        nameDisplayNameMap.put(IBMXIV, "Storage Provider for IBM XIV");
        nameDisplayNameMap.put(XTREMIO, "Storage Provider for EMC XtremIO");
        nameDisplayNameMap.put(ECS, "EMC Elastic Cloud Storage");
    }
}
