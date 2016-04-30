package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

@SuppressWarnings("serial")
public class hostMigrationExportOrchestrationCompleter extends TaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(hostMigrationExportOrchestrationCompleter.class);

    public hostMigrationExportOrchestrationCompleter(List<URI> uris, String task) {
        super(Volume.class, uris, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            // Tell the workflow we're done.
            updateWorkflowStatus(status, coded);
            _log.info("host migration export orchestration completer: done");
            _log.info(String.format("Done hostMigrationExportOrchestration - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            // Tell the individual objects we're done.
            for (URI id : getIds()) {
                switch (status) {
                    case error:
                        dbClient.error(Volume.class, id, getOpId(), coded);
                        break;
                    default:
                        dbClient.ready(Volume.class, id, getOpId());
                }
            }
        } catch (DatabaseException e) {
            _log.error(String.format("Failed updating status for RP Export Orchestration - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);

        }
    }
}
