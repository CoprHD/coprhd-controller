/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnx.xmlapi;

public class VNXBaseClass {

    public static final String FILESYSTEMNODE = "FileSystem";
    public static final String FILESYSTEMNAME = "name";
    public static final String FILESYSTEMID = "fileSystem";
    public static final String DATAMOVERNAME = "name";
    public static final String DATAMOVERNODE = "Mover";
    public static final String SNAPSHOTNODE = "Checkpoint";
    public static final String SNAPSHOTNAMENODE = "name";
    public static final String SNAPSHOTID = "checkpoint";
    public static final String SNAPSHOTFSID = "checkpointOf";
    public static final String RWHOSTS = "RwHosts";
    public static final String ROHOSTS = "RoHosts";
    public static final String MOVERID = "mover";

    public static final String FILE_SYSTEM_TYPE_ATTRIBUTE = "FileSystemType";
    public static final String THIN_PROVISIONED_ATTRIBUTE = "ThinProvisioned";
    public static final String WORM_ATTRIBUTE = "WORM";
    public static final String AUTO_EXTEND_ENABLED_ATTRIBUTE = "AutoExtendEnabled";
    public static final String AUTO_EXTEND_HWM_ATTRIBUTE = "AutoExtendHWM";
    public static final String AUTO_EXTEND_MAX_SIZE_ATTRIBUTE = "AutoExtendMaxSize";
    public static final String WORM_DEF = "off";
    public static final String FILE_SYSTEM_TYPE_DEF = "uxfs";
    public static final String AUTO_EXTEND_ENABLED_DEF = "false";
    public static final String THIN_PROVISIONED_DEF = "false";
    public static final String AUTO_EXTEND_HWM_DEF = "90";

    protected static final int timeout = 3000;

    protected static final String requestHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<RequestPacket xmlns=\"http://www.emc.com/schemas/celerra/xml_api\" >\n" +
            "<Request>\n";

    public static final String requestFooter = "</Request>\n" +
            "</RequestPacket>";

    public VNXBaseClass() {

    }

}
