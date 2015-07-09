/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class to create a byte buffer with the contents of an ISO image
 *
 */
public class ISOBuffer {
    
    private ByteBuffer byteBuffer; 
    private Date creationDate;
    private int currentExtent;
    private List<DirectoryRecord> directoryRecords;
    private List<DirectoryRecord> fileRecords;
    private List<byte[]> fileDataList; 
    private PrimaryVolumeDescriptor vd;
    private PathTable pt;
    private DirectoryRecord rootDR;
    private static final int START_EXTENT = 20;
    private int dataStartPosition;
    private int dataEndPosition;
    public ISOBuffer() {
        creationDate = new Date();
        // Start adding use data from the 20th extent
        currentExtent = START_EXTENT;
        directoryRecords = new ArrayList<>();
        fileRecords = new ArrayList<>();
        fileDataList = new ArrayList<>();
        dataStartPosition = 0;
        dataEndPosition = 0;
    }

    private void initializeISO(String identifier) {
        byteBuffer = ByteBuffer.allocate(ISOConstants.BUFFER_SIZE);
        //Reserving first 16 sectors for bootable disks.
        ISOUtil.padWithReserved(byteBuffer, 16 * ISOConstants.SECTOR_SIZE);

        
        // Create a record for the root directory
        createRootDirectory();
        //Primary volume descriptor
        createVolumeDescriptor(identifier);
        //Path table
        createPathTable();
        
        vd.addToBuffer(byteBuffer);
        pt.addToBufferLittleEndian(byteBuffer);
        pt.addToBufferBigEndian(byteBuffer);
    }
    
    private void createVolumeDescriptor(String identifier){
        
        vd = new PrimaryVolumeDescriptor(creationDate);
        // vd.systemIdentifier = ISOConstants.VOLUME_IDENTIFIER;
        vd.systemIdentifier = identifier;
        // vd.volumeIdentifier = ISOConstants.VOLUME_IDENTIFIER;
        vd.volumeIdentifier = identifier;
        vd.volumeSpaceSize = 23;
        vd.volumeSetSize = 1;
        vd.volumeSequenceNumber = 1;
        vd.logicalBlockSize = ISOConstants.SECTOR_SIZE;
        vd.pathTableSize = 11;
        vd.locationOfLittleEndianPathTable = 18;
        vd.locationOfBigEndianPathTable = 19;
        vd.rootDirectoryRecord = rootDR;
        
    }

    private void createPathTable(){
        pt = new PathTable();
        pt.sectorSize = ISOConstants.SECTOR_SIZE;
        pt.directoryIdentifierLength = 1;
        pt.extentLocation = START_EXTENT;
        pt.dirNumOfParentDirectory = 1;
        
    }

    private void createRootDirectory(){
        rootDR = createDirectory((byte) 0);
        directoryRecords.add(rootDR);
        directoryRecords.add(createDirectory((byte) 1));
    }

    private DirectoryRecord createDirectory(final byte paddingByte){
        DirectoryRecord dr = new DirectoryRecord(creationDate);
        dr.lengthOfDirRecord = 34;
        dr.extentLocation = START_EXTENT;
        dr.directoryEntryLength = 2048;
        dr.directoryFlagBit = 2;
        dr.volumeSequenceNumber = 1;
        dr.directoryIdentifierLength = 1;
        dr.paddingByte = paddingByte;
        return dr;
    }

    public void addFile(final String fileName, byte[] data){
        DirectoryRecord fileEntry = new DirectoryRecord(creationDate);
        fileEntry.lengthOfDirRecord = (byte)(fileName.length() + 33);
        fileEntry.extentLocation=++currentExtent;
        fileEntry.directoryEntryLength = data.length;
        fileEntry.directoryFlagBit = 0;
        fileEntry.volumeSequenceNumber = 1;
        fileEntry.directoryIdentifierLength = (byte) fileName.length();
        fileEntry.dirName = fileName;
        // Add the file record to the list then add the file data
        // the array lit maintains the order of the data so that it is written to the correct extent
        fileRecords.add(fileEntry);
        fileDataList.add(data);
    }
    
    private void writeDirectoryRecords() {
        for(DirectoryRecord directory : directoryRecords) {
            directory.addToBuffer(byteBuffer);
        }
        
    }
    private void writeFileRecords() {
        for(DirectoryRecord fileRecord : fileRecords) {
            fileRecord.addToBuffer(byteBuffer);
        }
    }
    private void writeFileData() {
        for(byte[] fileData : fileDataList) {
            int reserve = ISOConstants.SECTOR_SIZE - byteBuffer.position()%ISOConstants.SECTOR_SIZE;
            ISOUtil.padWithReserved(byteBuffer, reserve);

            if (dataStartPosition == 0) {
                dataStartPosition = byteBuffer.position();
            }

            byteBuffer.put(fileData);
        }
    }
    
    /**
     * Write all of the ISO image contents to a byte buffer
     * @return byte [] representation of the ISO
     */
    public byte[] createISO() {
        return createISO(ISOConstants.VOLUME_IDENTIFIER);
    }

    public byte[] createISO(String identifier) {
        //First initialize the ISO structure
        initializeISO(identifier);
        // write the directories to the buffer
        writeDirectoryRecords();
        // write the file records to the buffer
        writeFileRecords();
        // now write the file contents to the buffer
        writeFileData();

        dataEndPosition = byteBuffer.position();

        return byteBuffer.array();
    }

    public int getDataStartPosition() {
        return dataStartPosition;
    }

    public int getDataEndPosition() {
        return dataEndPosition;
    }
}
