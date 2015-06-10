/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public abstract class StorageEndPointProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(StorageEndPointProcessor.class);
    
    /**
     * return a StorageSystem object for a given systemId.
     * @param dbClient
     * @param systemId
     * @return
     * @throws IOException
     */
    protected StorageSystem getStorageSystem(DbClient dbClient, URI systemId) throws IOException {
        return dbClient.queryObject(StorageSystem.class, systemId);
    }
    /**
     * find if Storage Port already present in DB, via SCSI Address
     * 
     * @param endPointInstance
     * @return
     */
    protected StoragePort checkEthernetStoragePortExistsInDB(
            String scsiAddress, DbClient _dbClient, StorageSystem device) {
        StoragePort port = null;
        try {
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePortByNativeGuidConstraint(NativeGUIDGenerator
                            .generateNativeGuid(device, scsiAddress,
                                    NativeGUIDGenerator.PORT)), result);
            if (result.iterator().hasNext()) {
                port = _dbClient.queryObject(StoragePort.class, result
                        .iterator().next());
            }
        } catch (Exception e) {
            _logger.warn("StoragePort not available {}", scsiAddress);
        }
        return port;
    }
    
    
}
