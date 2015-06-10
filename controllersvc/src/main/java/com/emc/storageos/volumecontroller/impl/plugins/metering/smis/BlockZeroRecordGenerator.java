/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;

/**
 * BlockCacheSyncher is responsible to do Block specific operations.
 *
 */
public class BlockZeroRecordGenerator extends ZeroRecordGenerator {
    private Logger _logger = LoggerFactory.getLogger(BlockZeroRecordGenerator.class);

    /**
     * 
     * Inject VolumeURI of the given nativeGuid.
     * 
     * @param dbClient: dbClient.
     * @param nativeGuid: nativeGuid of the volume.
     * 
     */
    public List<URI> injectResourceURI(final DbClient dbClient, final String nativeGuid) {
        List<URI> volumeURIs = null;
      
        try {
            // Get VolumeUUID
            volumeURIs = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVolumeNativeGuidConstraint(nativeGuid));
            if (volumeURIs == null || volumeURIs.isEmpty()) {
                    //look for snap, we never know whether the returned volume is a snap
                    volumeURIs = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getBlockSnapshotsByNativeGuid(nativeGuid));
                   
            }
        } catch (Exception e) {
            // Even if one volume fails, no need to throw exception instead
            // continue processing other volumes
            _logger.warn(
                    "Volume could not be found using NativeGuid : {}",
                    nativeGuid);
            
        }
        
        return volumeURIs;
    }
    
    @Override
    public void generateZeroRecord(Stat zeroStatRecord,
            Map<String, Object> keyMap) {
        zeroStatRecord.setTimeInMillis((Long) keyMap
                .get(Constants._TimeCollected));
        zeroStatRecord.setTimeCollected((Long) keyMap
                .get(Constants._TimeCollected));
        zeroStatRecord.setServiceType(Constants._Block);
        zeroStatRecord.setAllocatedCapacity(0);
        zeroStatRecord.setProvisionedCapacity(0);
        zeroStatRecord.setBandwidthIn(0);
        zeroStatRecord.setBandwidthOut(0);
        zeroStatRecord.setIoTimeCounter(0);
        zeroStatRecord.setSnapshotCapacity(0);
        zeroStatRecord.setSnapshotCount(0);
        zeroStatRecord.setTotalIOs(0);
        zeroStatRecord.setWriteIOs(0);
        zeroStatRecord.setIdleTimeCounter(0);
        zeroStatRecord.setQueueLength(0);
        zeroStatRecord.setReadIOs(0);
        zeroStatRecord.setKbytesTransferred(0);
    }

    @Override
    protected Stat getStatObject(URI resourceURI, DbClient dbClient) {
        if (URIUtil.isType(resourceURI, Volume.class)) {
            Volume volume = dbClient.queryObject(Volume.class, resourceURI);
            if (!volume.checkInternalFlags(Flag.NO_METERING)) {
                return new Stat();                
            }
        }
        return null;
    }
}
