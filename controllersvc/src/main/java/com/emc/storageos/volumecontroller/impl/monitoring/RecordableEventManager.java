/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

// Logger imports

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Event;
import com.emc.storageos.db.client.model.EventTimeSeries;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * RecordableEventManager is used to store system events to the Cassandra database.
 */
public class RecordableEventManager {

    // Enumeration of supported event types that can be inserted
    // to/queried from the database.
    public enum EventType {
        GenericSystem, // A generic system event that a Bourne subsystem would like recorded as an event.
        SystemError, // A system error that a Bourne subsystem would like recorded as an event.
        ArrayGeneric, // Generic array/cluster level events from storage devices
        ZoneCreated,
        ZoneDeleted,
        ZoneChanged,
        VpoolCreated,
        VpoolUpdated,
        VpoolDeleted,
        StorageDeviceAdded,
        StorageDeviceRemoved,
        StoragePoolCreated,
        StoragePoolUpdated,
        StoragePoolDeleted,
        VolumeCreated,
        VolumeCreateFailed,
        VolumeExpanded,
        VolumeExpandFailed,
        VolumeDeleted,
        VolumeDeleteFailed,
        ExportCreated,
        ExportCreateFailed,
        ExportDeleted,
        ExportDeleteFailed,
        ExportVolumeAdded,
        ExportVolumeAddFailed,
        ExportVolumeRemoved,
        ExportVolumeRemoveFailed,
        ExportInitiatorAdded,
        ExportInitiatorAddFailed,
        ExportInitiatorRemoved,
        ExportInitiatorRemoveFailed,
        VolumeRestored,
        VolumeSnapshotCreated,
        VolumeSnapshotActivated,
        VolumeSnapshotDeactivated,
        VolumeSnapshotActivateFailed,
        VolumeSnapshotDeactivateFailed,
        VolumeSnapshotDeleted,
        VolumeSnapshotExported,
        VolumeSnapshotUnexported,
        VolumeSnapshotRestored,
        VolumeSnapshotCreateFailed,
        VolumeSnapshotDeleteFailed,
        VolumeSnapshotExportFailed,
        VolumeSnapshotUnexportFailed,
        VolumeSnapshotRestoreFailed,
        FileSystemSnapshotCreated,
        FileSystemSnapshotDeleted,
        FileSystemSnapshotExported,
        FileSystemSnapshotUnexported,
        FileSystemSnapshotConsistencyGroupChanged,
        FileSystemSnapshotCreateFailed,
        FileSystemSnapshotUnexportFailed,
        FileSystemSnapshotExportFailed,
        FileSystemSnapshotDeleteFailed,
        FileSystemCreated,
        FileSystemCreateFailed,
        FileSystemDeleted,
        FileSystemDeleteFailed,
        FileSystemExported,
        FileSystemExportFailed,
        FileSystemUnexported,
        FileSystemUnexportFailed,
        FileSystemRestored,
        FileSystemRestoreFailed,
        VolumeEventOkStatus,
        VolumeEventNotOkStatus,
        FileSystemEventOkStatus,
        FileSystemEventNotOkStatus,
        BucketCreated,
        BucketDeleted,
        BucketRestored,
        SecretKeyCreated,
        SecretKeyDeleted,
        ProjectCreated,
        ProjectDeleted,
        ProjectUpdated,
        TenantCreated,
        TenantDeleted,
        TenantUpdated,
        FileSystemSnapshotShared,
        FileSystemSnapshotShareFailed,
        FileSystemSnapshotShareDeleted,
        FileSystemSnapshotShareDeleteFailed,
        FileSystemShared,
        FileSystemShareFailed,
        FileSystemShareDeleted,
        FileSystemShareDeleteFailed,
        FileSystemExpanded,
        FileSystemExpandFailed,
        RPCGCreated,
        RPCGCreateFailed,
        RPCGDeleted,
        RPCGDeleteFailed,
        UserCreated,
        UserDeleted,
        VolumeMirrorFractured,
        VolumeMirrorFractureFailed,
        VolumeMirrorCreated,
        VolumeMirrorCreateFailed,
        VolumeMirrorDetached,
        VolumeMirrorDetachmentFailed,
        VolumeMirrorDeleted,
        VolumeMirrorDeleteFailed,
        VolumeMirrorDeactivated,
        VolumeMirrorDeactivateFailed,
        VolumeFullCopyCreated,
        VolumeFullCopyCreateFailed,
        VolumeFullCopyDetached,
        VolumeFullCopyDetachFailed,
        ConsistencyGroupCreated,
        ConsistencyGroupCreateFailed,
        ConsistencyGroupDeleted,
        ConsistencyGroupDeleteFailed,
        ConsistencyGroupUpdated,
        ConsistencyGroupUpdateFailed,
        NamespaceCreated,
        NamespaceDeleted,
        StoragePortGroupCreated, 
        StoragePortGroupCreatedFailed, 
        StoragePortGroupDeleted, 
        StoragePortGroupDeletedFailed,
    };

    // A reference to the database client.
    private DbClient _dbClient;

    // The logger.
    private static Logger s_logger = LoggerFactory.getLogger(RecordableEventManager.class);

    /**
     * Default constructor.
     */
    public RecordableEventManager() {
        super();
    }

    /**
     * Setter for the data base client.
     * 
     * @param dbClient Reference to a database client.
     */
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Called to record events in the database.
     * 
     * @param events references to recordable events.
     * @throws IOException thrown when insert events to database fails
     */
    public void recordEvents(RecordableEvent... events) throws DatabaseException {

        List<Event> dbEventsList = new ArrayList<Event>();

        for (RecordableEvent event : events) {
            Event dbEvent = ControllerUtils.convertToEvent(event);
            // if no db object found after querying using native guid,
            // means the indication triggered from outside world but not
            // from bourne, so we need to ignore as per requirement
            // we need to drop the these indications
            if (event.getResourceId() == null &&
                    !(event instanceof RecordableBourneEvent)) {
                continue;
            }

            dbEventsList.add(dbEvent);
        }
        if (!dbEventsList.isEmpty()) {
            Event[] dbEvents = new Event[dbEventsList.size()];
            dbEventsList.toArray(dbEvents);

            // Now insert the events into the database.
            try {
                String bucketId = _dbClient.insertTimeSeries(EventTimeSeries.class, dbEvents);
                s_logger.debug("Event(s) persisted into Cassandra with bucketId/rowId : {}", bucketId);
            } catch (DatabaseException e) {
                s_logger.error("Error inserting events into the database", e);
                throw e;
            }
        } else {
            s_logger.info("Event list is empty");
        }

    }
}
