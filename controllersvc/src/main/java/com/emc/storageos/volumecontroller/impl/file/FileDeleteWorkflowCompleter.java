/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.MirrorStatus;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class FileDeleteWorkflowCompleter extends FileWorkflowCompleter {

    public FileDeleteWorkflowCompleter(URI fsUri, String task) {
        super(fsUri, task);

    }

    @Override
    protected void complete(DbClient dbClient, Status status,
            ServiceCoded serviceCoded) {
        super.complete(dbClient, status, serviceCoded);
        if (status == Operation.Status.ready) {
            // Remove target attributes from souce file system!!
            for (URI id : getIds()) {
                FileShare fileSystem = dbClient.queryObject(FileShare.class, id);
                if (fileSystem != null && !fileSystem.getInactive()) {
                    if (fileSystem.getPersonality() != null &&
                            PersonalityTypes.SOURCE.name().equalsIgnoreCase(fileSystem.getPersonality())) {
                        // Reset the mirror attributes!!
                        StringSet targets = fileSystem.getMirrorfsTargets();
                        if (targets != null && !targets.isEmpty()) {
                            targets.clear();
                        }
                        fileSystem.setMirrorfsTargets(targets);
                        fileSystem.setMirrorStatus(MirrorStatus.DETACHED.name());
                        fileSystem.setPersonality(null);
                        dbClient.updateObject(fileSystem);
                        _log.info("FileDeleteWorkflowCompleter::reset the mirror attribute of source file system {}",
                                fileSystem.getId());
                    }
                }
                dbClient.ready(FileShare.class, id, getOpId());
            }

        }
    }

    public FileDeleteWorkflowCompleter(List<URI> fsUris, String task) {
        super(fsUris, task);

    }
}
