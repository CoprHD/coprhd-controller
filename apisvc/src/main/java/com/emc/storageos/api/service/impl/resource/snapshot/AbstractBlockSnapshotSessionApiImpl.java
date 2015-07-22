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
package com.emc.storageos.api.service.impl.resource.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;

/**
 * 
 */
public class AbstractBlockSnapshotSessionApiImpl implements BlockSnapshotSessionApi {

    // A reference to a database client.
    protected DbClient _dbClient;
    
    // A reference to the coordinator.
    protected CoordinatorClient _coordinator = null;

    @SuppressWarnings("unused")
    private static final Logger s_logger = LoggerFactory.getLogger(AbstractBlockSnapshotSessionApiImpl.class);
    
    /**
     * Private default constructor should not be used outside class.
     */
    @SuppressWarnings("unused")
    private AbstractBlockSnapshotSessionApiImpl() {
    }
    
    /**
     * Constructor.
     * 
     * @param dbClient A reference to a data base client.
     * @param coordinator A reference to the coordinator client.
     */
    protected AbstractBlockSnapshotSessionApiImpl(DbClient dbClient, CoordinatorClient coordinator) {
        _dbClient = dbClient;
        _coordinator = coordinator;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession() {
    }
}
