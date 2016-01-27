package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MirrorFileStopTaskCompleter extends MirrorFileTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(MirrorFileStopTaskCompleter.class);
    public MirrorFileStopTaskCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
    }

    public MirrorFileStopTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public MirrorFileStopTaskCompleter(URI sourceURI, URI targetURI, String opId) {
        super(sourceURI, targetURI, opId);
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
            super.complete(dbClient, status, coded);
        }
    }

}