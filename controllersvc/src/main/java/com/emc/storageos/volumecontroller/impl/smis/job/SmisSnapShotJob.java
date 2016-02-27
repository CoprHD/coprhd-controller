/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.List;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class SmisSnapShotJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisSnapShotJob.class);

    public SmisSnapShotJob(CIMObjectPath cimJob, URI storageSystem,
            TaskCompleter taskCompleter, String jobName) {
        super(cimJob, storageSystem, taskCompleter, jobName);
    }

    /**
     * This method updates provisioned capacity and allocated capacity for snapshots.
     * It also set settingsInstance for VMAX V3 snapshot.
     * 
     * @param snapShot A reference to the snapshot to be updated.
     * @param syncVolume A reference to the CIM instance representing the snapshot target volume.
     * @param client A reference to a WBEM client.
     * @param storage A reference to the storage system.
     * @param sourceElementId String of source volume (or source group) ID
     * @param elementName String used as ElementName when creating ReplicationSettingData during single snapshot creation,
     *            or RelationshipName used in CreateGroupReplica for group snapshot. Note elementName should be target device's DeviceID
     *            or target group ID.
     * @param createSession true if a BlockSnapshotSession should be created to represent the settings instance.
     * @param dbClient A reference to a database client.
     */
    protected void commonSnapshotUpdate(
            BlockSnapshot snapShot, CIMInstance syncVolume, WBEMClient client, StorageSystem storage, String sourceElementId,
            String elementName, boolean createSession, DbClient dbClient) {
        try {
            CIMProperty consumableBlocks = syncVolume
                    .getProperty(SmisConstants.CP_CONSUMABLE_BLOCKS);
            CIMProperty blockSize = syncVolume.getProperty(SmisConstants.CP_BLOCK_SIZE);
            // calculate provisionedCapacity = consumableBlocks * block size
            Long provisionedCapacity = Long.valueOf(consumableBlocks.getValue()
                    .toString()) * Long.valueOf(blockSize.getValue().toString());
            snapShot.setProvisionedCapacity(provisionedCapacity);

            // set Allocated Capacity
            CloseableIterator<CIMInstance> iterator = null;
            iterator = client.referenceInstances(syncVolume.getObjectPath(),
                    SmisConstants.CIM_ALLOCATED_FROM_STORAGEPOOL, null, false,
                    SmisConstants.PS_SPACE_CONSUMED);
            if (iterator.hasNext()) {
                CIMInstance allocatedFromStoragePoolPath = iterator.next();
                CIMProperty spaceConsumed = allocatedFromStoragePoolPath
                        .getProperty(SmisConstants.CP_SPACE_CONSUMED);
                if (null != spaceConsumed) {
                    snapShot.setAllocatedCapacity(Long.valueOf(spaceConsumed.getValue()
                            .toString()));
                }
            }

            // set settingsInstance for VMAX V3 only
            setSettingsInstance(storage, snapShot, sourceElementId, elementName, createSession, dbClient);
        } catch (Exception e) {
            // Don't want to fail the snapshot creation, if capacity retrieval fails, as auto discovery cycle
            // will take care of updating capacity informations later.
            _log.error(
                    "Caught an exception while trying to update Capacity and SettingsInstance for Snapshots",
                    e);
        }
    }

    /**
     * Set settings instance for VMAX V3 only. If the flag so indicates, this function
     * will also create a snapshot session to represent this settings instance, which is
     * the CIM instance ID for a synchronization aspect. The session needs to be created
     * for legacy code that created VMAX3 BlockSnapshots w/o representing the snapshot session.
     * 
     * @param storage storage A reference to the storage system.
     * @param snapshot BlockSnapshot to be updated
     * @param sourceElementId String of source volume (or source group) ID
     * @param elementName String used as ElementName when creating ReplicationSettingData during single snapshot creation,
     *            or RelationshipName used in CreateGroupReplica for group snapshot. Note elementName should be target device's DeviceID
     *            or target group ID.
     * @param createSession true if a BlockSnapshotSession should be created to represent the settings instance.
     * @param dbClient A reference to a database client.
     *
     */
    private void setSettingsInstance(StorageSystem storage, BlockSnapshot snapshot, String sourceElementId, String elementName,
            boolean createSession, DbClient dbClient) {
        if ((storage.checkIfVmax3()) && (createSession)) {
            // SYMMETRIX-+-000196700567-+-<sourceElementId>-+-<elementName>-+-0
            StringBuilder sb = new StringBuilder("SYMMETRIX");
            sb.append(Constants.SMIS80_DELIMITER)
                    .append(storage.getSerialNumber())
                    .append(Constants.SMIS80_DELIMITER).append(sourceElementId)
                    .append(Constants.SMIS80_DELIMITER).append(elementName)
                    .append(Constants.SMIS80_DELIMITER).append("0");
            snapshot.setSettingsInstance(sb.toString());

            // If the flag so indicates create a BlockSnapshotSession instance to represent this
            // settings instance.

            BlockSnapshotSession snapSession = getSnapshotSession(snapshot, dbClient);

            if (snapSession.getId() == null) {
                snapSession.setId(URIUtil.createId(BlockSnapshotSession.class));
                snapSession.setLabel(snapshot.getLabel());
                snapSession.setSessionLabel(snapshot.getSnapsetLabel());
                snapSession.setSessionInstance(snapshot.getSettingsInstance());
                snapSession.setProject(snapshot.getProject());

                setParentOrConsistencyGroupAssociation(snapSession, snapshot);
            }

            addSnapshotAsLinkedTarget(snapSession, snapshot);
            createOrUpdateSession(snapSession, dbClient);
        }
    }

    private BlockSnapshotSession getSnapshotSession(BlockSnapshot snapshot, DbClient dbClient) {
        BlockSnapshotSession result = null;
        if (snapshot.hasConsistencyGroup()) {
            List<BlockSnapshotSession> groupSnapSessions = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                    BlockSnapshotSession.class,
                    ContainmentConstraint.Factory.getBlockSnapshotSessionByConsistencyGroup(snapshot.getConsistencyGroup()));
            if (!groupSnapSessions.isEmpty()) {
                for (BlockSnapshotSession groupSnapSession : groupSnapSessions) {
                    // When creating a group snapshot with multiple targets, we might
                    // have already created the BlockSnapshotSession instance to represent
                    // the group session. So, if we find a snapshot session for the
                    // group that has the same session instance paths as the passed snapshot,
                    // then we just return that session as we already created it when another
                    // snapshot in the group was processed.
                    String sessionInstance = groupSnapSession.getSessionInstance();
                    if (sessionInstance.equals(snapshot.getSettingsInstance())) {
                        result = groupSnapSession;
                        break;
                    }
                }
            }
        }

        if (result == null) {
            result = new BlockSnapshotSession();
        }

        return result;
    }

    private void setParentOrConsistencyGroupAssociation(BlockSnapshotSession session, BlockSnapshot snapshot) {
        if (snapshot.hasConsistencyGroup()) {
            session.setConsistencyGroup(snapshot.getConsistencyGroup());
        } else {
            session.setParent(snapshot.getParent());
        }
    }

    private void addSnapshotAsLinkedTarget(BlockSnapshotSession session, BlockSnapshot snapshot) {
        if (session.getLinkedTargets() == null) {
            session.setLinkedTargets(new StringSet());
        }
        session.getLinkedTargets().add(snapshot.getId().toString());
    }

    private void createOrUpdateSession(BlockSnapshotSession session, DbClient dbClient) {
        if (session.getCreationTime() == null) {
            dbClient.createObject(session);
        } else {
            dbClient.updateObject(session);
        }
    }
}
