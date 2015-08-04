/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.iso;

import java.nio.ByteBuffer;
import java.util.Date;

/**
 * Class that creates Directory/File record entry for ISO image.
 */
public class DirectoryRecord {

    public byte lengthOfDirRecord;
    public byte extendedAttrLength = 0;
    public int extentLocation; // index of logical block containing file data.
    public int directoryEntryLength;
    public byte directoryFlagBit; // value = 2 for directory. = 0 for file.
    public byte fileUnitSize = 0;
    public byte fileGapSize = 0;
    public short volumeSequenceNumber;
    public byte directoryIdentifierLength = 1; // 1 for root directory
    public byte paddingByte;
    public String dirName;
    public Date creationDate;

    public DirectoryRecord(Date date) {
        creationDate = date;
    }

    // root directory
    public void addToBuffer(ByteBuffer byteBuffer) {
        byteBuffer.put(lengthOfDirRecord);
        byteBuffer.put(extendedAttrLength);
        ISOUtil.putIntLSBMSB(byteBuffer, extentLocation);
        ISOUtil.putIntLSBMSB(byteBuffer, directoryEntryLength);
        ISOUtil.formatDate(byteBuffer, creationDate);
        byteBuffer.put(directoryFlagBit);
        byteBuffer.put(fileUnitSize);
        byteBuffer.put(fileGapSize);
        ISOUtil.putShortLSBMSB(byteBuffer, volumeSequenceNumber);
        byteBuffer.put(directoryIdentifierLength);// length of directory identifier/following field
        if (dirName != null) {
            byteBuffer.put(dirName.getBytes());
        }
        else {
            byteBuffer.put(paddingByte);
        }
    }
}
