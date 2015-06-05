/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

/**
 * Map of FSExport (used in FileShare)
 */
public class FSExportMap extends AbstractChangeTrackingMap<FileExport> {
    @Override
    public FileExport valFromByte(byte[] value) {
        FileExport fileExport = new FileExport();
        fileExport.loadBytes(value);
        return fileExport;
    }

    @Override
    public byte[] valToByte(FileExport value) {
        return value.toBytes();
    }
}
