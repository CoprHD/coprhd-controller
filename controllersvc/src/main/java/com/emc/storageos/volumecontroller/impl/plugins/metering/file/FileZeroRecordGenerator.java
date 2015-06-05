/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.file;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;

/**
 * FileCacheSyncher is responsible to do File specific operations.
 *
 */
public class FileZeroRecordGenerator extends ZeroRecordGenerator {
    private Logger _logger = LoggerFactory.getLogger(FileZeroRecordGenerator.class);

 

    /**
     * Inject FileShareURI of the given nativeGuid.
     * 
     * @param dbClient: dbClient.
     * @param nativeGuid: nativeGuid of the volume.
     * 
     */
    public List<URI> injectResourceURI(final DbClient dbClient, final String nativeGuid) {
        List<URI> fileshareURIs = null;
        try {
            // Get VolumeUUID
            fileshareURIs = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileShareNativeIdConstraint(nativeGuid));
        } catch (Exception e) {
            // Even if one volume fails, no need to throw exception instead
            // continue processing other volumes
            _logger.error(
                    "Cassandra Database Error while querying FileshareUUId: {}--> ",
                    nativeGuid, e);
        }
        return fileshareURIs;
    }


    @Override
    public void generateZeroRecord(Stat zeroStatRecord,
            Map<String, Object> keyMap) {
        // TimeInMillis is also the plugin collection time as
        // we get two different times when we query fileshare usage & volume bwIn & bwOut.
        zeroStatRecord.setTimeInMillis((Long) keyMap
                .get(Constants._TimeCollected));
        zeroStatRecord.setTimeCollected((Long) keyMap
                .get(Constants._TimeCollected));
        zeroStatRecord.setServiceType(VNXFileConstants.FILE);
        zeroStatRecord.setAllocatedCapacity(0);
        zeroStatRecord.setProvisionedCapacity(0);
        zeroStatRecord.setBandwidthIn(0);
        zeroStatRecord.setBandwidthOut(0);
        zeroStatRecord.setSnapshotCapacity(0);
        zeroStatRecord.setSnapshotCount(0);
    }


    @Override
    protected Stat getStatObject(URI resourceURI, DbClient dbClient) {
        if(URIUtil.isType(resourceURI, FileShare.class)) {
            FileShare fs = dbClient.queryObject(FileShare.class, resourceURI);
            if (!fs.checkInternalFlags(Flag.NO_METERING)) {
                return new Stat();                
            }
        }
        return null;
    }
}
