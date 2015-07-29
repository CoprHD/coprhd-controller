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
import java.nio.ByteOrder;

/**
 * Class that creates Path table entries for ISO image.
 */
public class PathTable {

    public byte directoryIdentifierLength;
    public byte extendedAttrLength = 0;
    public int extentLocation;
    public short dirNumOfParentDirectory; // an index into the path table
    public int sectorSize;

    // L-table LSB format
    public void addToBufferLittleEndian(ByteBuffer byteBuffer) {
        byteBuffer.put(directoryIdentifierLength);
        byteBuffer.put(extendedAttrLength);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).putInt(extentLocation);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).putShort(dirNumOfParentDirectory);
        ISOUtil.padWithReserved(byteBuffer, sectorSize - 8);
    }

    // M-table MSB format
    public void addToBufferBigEndian(ByteBuffer byteBuffer) {
        byteBuffer.put(directoryIdentifierLength);
        byteBuffer.put(extendedAttrLength);
        byteBuffer.order(ByteOrder.BIG_ENDIAN).putInt(extentLocation);
        byteBuffer.order(ByteOrder.BIG_ENDIAN).putShort(dirNumOfParentDirectory);
        ISOUtil.padWithReserved(byteBuffer, sectorSize - 8);
    }
}
