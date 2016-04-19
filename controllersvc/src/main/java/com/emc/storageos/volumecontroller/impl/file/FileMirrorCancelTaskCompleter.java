/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class FileMirrorCancelTaskCompleter extends FileWorkflowCompleter{

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded serviceCoded) {
        super.complete(dbClient, status, serviceCoded);
    }

    public FileMirrorCancelTaskCompleter(List<URI> sourceURIs, String opId) {
        super(sourceURIs, opId);
    }

}
