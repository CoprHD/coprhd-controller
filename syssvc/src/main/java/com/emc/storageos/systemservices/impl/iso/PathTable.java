/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
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
