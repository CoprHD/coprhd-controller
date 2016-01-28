/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.collect.Collections2;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class FileMirrorRollbackCompleter extends FileWorkflowCompleter{

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded serviceCoded) {
        List<FileShare> sourceFileShareList = dbClient.queryObject(FileShare.class, getIds());
        if(sourceFileShareList != null && !sourceFileShareList.isEmpty()) {
            List<FileShare> fileSharesToUpdate = new ArrayList<FileShare>();
            for (FileShare fileShare: sourceFileShareList) {
                if(fileShare.getMirrorfsTargets() != null && !fileShare.getMirrorfsTargets().isEmpty()) {
                    
                }
                
            }
        }
        super.complete(dbClient, status, serviceCoded);
    }

    public FileMirrorRollbackCompleter(List<URI> sourceURIs, String opId) {
        super(sourceURIs, opId);
    }

}
