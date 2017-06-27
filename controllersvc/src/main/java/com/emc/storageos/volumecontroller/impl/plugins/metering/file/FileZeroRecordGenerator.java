/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
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
    @Override
    public List<URI> injectResourceURI(final DbClient dbClient, final String nativeGuid) {
        URIQueryResultList results = new URIQueryResultList();
        ArrayList<URI> resultsList = new ArrayList<>();
        try {
            // Get File systems with given native id!!
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getFileShareNativeIdConstraint(nativeGuid),
                    results);
            Iterator<URI> it = results.iterator();
            while (it.hasNext()) {
                resultsList.add(it.next());
            }

        } catch (Exception e) {
            // Even if one volume fails, no need to throw exception instead
            // continue processing other volumes
            _logger.error(
                    "Cassandra Database Error while querying FileshareUUId: {}--> ",
                    nativeGuid, e);
        }
        return resultsList;
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
        if (URIUtil.isType(resourceURI, FileShare.class)) {
            FileShare fs = dbClient.queryObject(FileShare.class, resourceURI);
            if (!fs.checkInternalFlags(Flag.NO_METERING)) {
                return new Stat();
            }
        }
        return null;
    }
}
