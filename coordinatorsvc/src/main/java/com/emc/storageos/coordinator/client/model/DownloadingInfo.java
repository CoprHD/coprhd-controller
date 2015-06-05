/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.coordinator.client.model;

import java.util.ArrayList;
import java.util.Arrays;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.vipr.model.sys.NodeProgress.DownloadStatus;

/**
 * To comply with other similar classes, we gave it a dummy id and kind.
 *      "global", "upgradedowloadinginfo"
 */
public class DownloadingInfo implements CoordinatorSerializable {
    private static final String ENCODING_SEPERATOR = ",";
    public String _version;
    public long _size; // size of the image file
    public long downloadedBytes;
    public ArrayList<Integer> _errorCounter;
    public DownloadStatus _status;
    
    public DownloadingInfo(){}
    
    public DownloadingInfo(String version, long size, long downloaded, DownloadStatus status, ArrayList<Integer> errorCounter) {
        _version = version;
        _size = size;
        downloadedBytes = downloaded;
        _status = status;
        _errorCounter = errorCounter;
    }
    
    /** A constructor for a new download process
     * @param version
     * @param size
     */
    public DownloadingInfo(String version, long size) {
        _version = version;
        _size = size;
        downloadedBytes = 0;
        _status = DownloadStatus.NORMAL;
        _errorCounter = new ArrayList<Integer>(Arrays.asList(0,0));
    }
    
    /** Generate a new DownloadingInfo object for the cancel status
     * @return a DownloadinInfo with downloadedBytes=0 and status=CANCELLED, the rest of the field remain the same
     */
    public DownloadingInfo cancel() {
        return new DownloadingInfo(this._version,this._size,0,DownloadStatus.CANCELLED,this._errorCounter);
    }
    
    @Override
    public String encodeAsString() {
        StringBuilder b  = new StringBuilder();
        b.append(_version).append(ENCODING_SEPERATOR).append(_size).append(ENCODING_SEPERATOR).append(downloadedBytes).append(ENCODING_SEPERATOR).append(_status.name()).append(ENCODING_SEPERATOR).append(_errorCounter.get(0)).append(ENCODING_SEPERATOR).append(_errorCounter.get(1));
        return b.toString();
    }

    @Override
    public DownloadingInfo decodeFromString(String infoStr)
            throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        } 
        String[] array = infoStr.split(ENCODING_SEPERATOR);
        ArrayList<Integer> tmpList = new ArrayList<Integer>(Arrays.asList(Integer.valueOf(array[4]),Integer.valueOf(array[5])));
        return new DownloadingInfo(array[0], Long.valueOf(array[1]), Long.valueOf(array[2]), DownloadStatus.valueOf(array[3]),tmpList);
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo("global", "upgradedowloadinginfo", "downloadinginfo");
    }

}
