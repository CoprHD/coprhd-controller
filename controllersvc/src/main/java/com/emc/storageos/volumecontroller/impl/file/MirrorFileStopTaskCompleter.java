/*
 * Copyright (c) 2015-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MirrorFileStopTaskCompleter extends MirrorFileTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(MirrorFileStopTaskCompleter.class);
    private Collection<FileShare> srcfileshares;
    private Collection<FileShare> tgtfileshares;

    public MirrorFileStopTaskCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
    }

    public MirrorFileStopTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public MirrorFileStopTaskCompleter(URI sourceURI, URI targetURI, String opId) {
        super(sourceURI, targetURI, opId);
    }

    public void setFileShares(Collection<FileShare> srcfileshares, Collection<FileShare> tgtfileshares) {
        this.srcfileshares = srcfileshares;
        this.tgtfileshares = tgtfileshares;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            setDbClient(dbClient);

            recordMirrorOperation(dbClient, OperationTypeEnum.STOP_FILE_MIRROR, status, getSourceFileShare().getId().toString(),
                    getTargetFileShare().getId().toString());

        } catch (Exception e) {
            _log.error("Failed updating status. MirrorSessionStop {}, for task " + getOpId(), getId(), e);
        } finally {
            setStatus(dbClient, status, coded);
            updateFileSystemStatus(dbClient, status);
        }
    }

    @Override
    protected void updateFileSystemStatus(DbClient dbClient, Operation.Status status) {
        try {
            if (Operation.Status.ready.equals(status)) {
                List<FileShare> fileshares = dbClient.queryObject(FileShare.class, getIds());
                for (FileShare fileshare : fileshares) {
                    fileshare.setMirrorStatus(NullColumnValueGetter.getNullStr());
                    fileshare.setAccessState(NullColumnValueGetter.getNullStr());
                    if (fileshare.getMirrorfsTargets() != null
                            && fileshare.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.toString())) {
                        StringSet targets = fileshare.getMirrorfsTargets();
                        if (targets != null && !targets.isEmpty()) {
                            targets.clear();
                        }
                        fileshare.setMirrorfsTargets(targets);
                        fileshare.setPersonality(NullColumnValueGetter.getNullStr());
                        dbClient.updateObject(fileshare);

                    } else if (fileshare.getParentFileShare() != null
                            && fileshare.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.toString())) {
                        fileshare.setPersonality(NullColumnValueGetter.getNullStr());
                        fileshare.setParentFileShare(NullColumnValueGetter.getNullNamedURI());
                        dbClient.updateObject(fileshare);
                    }
                }
                _log.info("Removed the replication information for fileshares: {}", getIds());
            }
        } catch (Exception e) {
            _log.info("Not updating fileshare mirror link status for fileshares: {}", getIds(), e);
        }
    }
}