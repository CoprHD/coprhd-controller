/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.iso;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

/**
 * Class that creates primary volume descriptor entry for ISO image.
 */
public class PrimaryVolumeDescriptor {

    // offset, length
    private byte volumeDescriptorType = 1; // 1, 1
    private String standardIdentifier = "CD001"; // 2- 6, 5
    private byte volumeDescriptorVersion = 1; // 7, 1
    // Unused fields null 8, 1
    public String systemIdentifier; // 9 - 40, 32
    public String volumeIdentifier; // 41 - 72, 32
    // Unused fields null 73 - 80, 8
    public int volumeSpaceSize; // Both Endian format 81-88, 8
    // Unused fields null 89 - 120, 32
    public short volumeSetSize;// Both Endian format 121 - 124, 4
    // no.of this desk in this set Both Endian format 125 - 128, 4
    public short volumeSequenceNumber;
    public short logicalBlockSize; // Both Endian format 129 - 132, 4
    public int pathTableSize;// Both Endian format 133 - 140, 8
    public int locationOfLittleEndianPathTable; // Little Endian Format 141 -144, 4
    public int locationOfBigEndianPathTable; // Big Endian Format 149 -152, 4
    public DirectoryRecord rootDirectoryRecord;// 157-190, 34 bytes
    public byte fileStructureVersion = 1; // 882, 1
    public byte reserved = 0;// 883 , 1
    public byte terminator = (byte) 255;
    private Date creationDate;

    public PrimaryVolumeDescriptor(Date date){
        creationDate = date;
    }

    public void addToBuffer(ByteBuffer byteBuffer){

        byteBuffer.put(volumeDescriptorType);
        byteBuffer.put(standardIdentifier.getBytes());
        byteBuffer.put(volumeDescriptorVersion);
        ISOUtil.padWithReserved(byteBuffer, 1); //unused

        byteBuffer.put(systemIdentifier.getBytes());
        ISOUtil.padWithSpaces(byteBuffer,32 - systemIdentifier.length());

        byteBuffer.put(volumeIdentifier.getBytes());
        ISOUtil.padWithSpaces(byteBuffer,32 - volumeIdentifier.length());

        ISOUtil.padWithReserved(byteBuffer, 8); //unused
        ISOUtil.putIntLSBMSB(byteBuffer, volumeSpaceSize);
        ISOUtil.padWithReserved(byteBuffer, 32); //unused

        ISOUtil.putShortLSBMSB(byteBuffer, volumeSetSize);
        ISOUtil.putShortLSBMSB(byteBuffer, volumeSequenceNumber);
        ISOUtil.putShortLSBMSB(byteBuffer, logicalBlockSize);
        ISOUtil.putIntLSBMSB(byteBuffer, pathTableSize);


        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).putInt(locationOfLittleEndianPathTable);
        ISOUtil.padWithReserved(byteBuffer, 4); //locationOfOptionalLittleEndianPathTable
        byteBuffer.order(ByteOrder.BIG_ENDIAN).putInt(locationOfBigEndianPathTable);
        ISOUtil.padWithReserved(byteBuffer, 4); //locationOfOptionalBigEndianPathTable

        rootDirectoryRecord.addToBuffer(byteBuffer);

        ISOUtil.padWithSpaces(byteBuffer,623); //some identifiers
        ISOUtil.formatDateAsStr(byteBuffer, creationDate); //volume creation date
        ISOUtil.padWithZeros(byteBuffer, 16); //last modified date
        byteBuffer.put((byte)0);
        ISOUtil.padWithZeros(byteBuffer, 16); //expiry date
        byteBuffer.put((byte)0);
        ISOUtil.padWithZeros(byteBuffer, 16); //effective date
        byteBuffer.put((byte)0);
        byteBuffer.put(fileStructureVersion);
        int reserve = logicalBlockSize - byteBuffer.position()% logicalBlockSize;
        ISOUtil.padWithReserved(byteBuffer, reserve); // reserving till end of this sector

        // TERMINATOR
        //Descriptor terminator
        byteBuffer.put(terminator); //ff
        byteBuffer.put(standardIdentifier.getBytes());
        byteBuffer.put(volumeDescriptorVersion);
        ISOUtil.padWithReserved(byteBuffer, logicalBlockSize - (2 + standardIdentifier
                .length()));

    }


}
