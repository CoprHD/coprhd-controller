/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe;

public class VNXeConstants {
    public static final String CONTENT = "content";
    public static final String ENTRIES = "entries";
    public static final String TIMEOUT = "timeout";
    public static final String FILTER = "filter";
    public static final String NAME_FILTER = "name eq ";
    public static final String STORAGE_RESOURCE_FILTER = "storageResource.id eq ";
    public static final String NASSERVER_FILTER = "nasServer.id eq ";
    public static final String FILE_SYSTEM_FILTER = "parentFilesystem.id eq ";
    public static final String FILE_SYSTEM_FILTER_V31 = "filesystem.id eq ";
    public static final String SNAP_FILTER = "parentFilesystemSnap.id eq ";
    public static final String SNAP_FILTER_V31 = "snap.id eq ";
    public static final String ISCSINODE_FILTER = "iscsiNode.id eq ";
    public static final String IPADDRESS_FILTER = "address eq ";
    public static final String AND = " and ";
    public static final String INITIATORID_FILTER = "initiatorId eq ";
    public static final String PARENT_HOST_FILTER = "parentHost eq ";
    public static final String LUN_FILTER = "lun.id eq ";
    public static final String LUN_SNAP_FILTER = "lunSnap.id eq ";
    public static final String PORTWWN_FILTER = "portWWN eq ";
    public static final int REDIRECT_MAX = 100;
    public static final int MAX_NAME_LENGTH = 63;
    public static final String VNXE_BASE_SOFT_VER = "3.0.1";
    public static final String FIELDS = "fields";
    public static final String POOL_FILTER = "pool.id eq ";
    public static final String PATH_FILTER = "path eq";
    public static final String SNAP_GROUP_FILTER = "snapGroup.id eq ";
    public static final String ID_FILTER = "id eq ";
    public static final String FASTVP_FEATURE="FAST_VP";
    public static final String INITIATOR_EXISITNG = "The specified host initiator already exists";

}
