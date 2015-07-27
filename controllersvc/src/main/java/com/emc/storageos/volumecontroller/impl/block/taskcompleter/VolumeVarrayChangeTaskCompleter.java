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
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class VolumeVarrayChangeTaskCompleter extends VolumeWorkflowCompleter {
    
    List<URI> _migrationURIs = new ArrayList<URI>();
    
    public VolumeVarrayChangeTaskCompleter(List<URI> volUris, List<URI> migrationURIs, String task) {
        super(volUris, task);
        _migrationURIs.addAll(migrationURIs);
    }

    public VolumeVarrayChangeTaskCompleter(URI volUri, URI migrationURI, String task) {
        super(volUri, task);
        _migrationURIs.add(migrationURI);
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        switch (status) {
            case error:
                for (URI migrationURI : _migrationURIs) {
                    dbClient.error(Migration.class, migrationURI, getOpId(), serviceCoded);
                }
                break;
            case ready:
                for (URI migrationURI : _migrationURIs) {
                    dbClient.ready(Migration.class, migrationURI, getOpId());
                }
                break;
            default:
        }
        super.complete(dbClient, status, serviceCoded);
    }
}
