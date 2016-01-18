/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSetMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotSessionUnlinkTargetCompleter;

/**
 * ViPR Job created when an underlying CIM job is created to unlink
 * a target volume from an array snapshot.
 */
@SuppressWarnings("serial")
public class SmisBlockSnapshotSessionUnlinkTargetJob extends SmisJob {

    // The unique job name.
    private static final String JOB_NAME = "SmisBlockSnapshotSessionUnlinkTargetJob";

    // Reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockSnapshotSessionUnlinkTargetJob.class);

    /**
     * Constructor.
     * 
     * @param cimJob The CIM object path of the underlying CIM Job.
     * @param systemURI The URI of the storage system.
     * @param taskCompleter A reference to the task completer.
     */
    public SmisBlockSnapshotSessionUnlinkTargetJob(CIMObjectPath cimJob, URI systemURI, TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        BlockSnapshotSessionUnlinkTargetCompleter completer = (BlockSnapshotSessionUnlinkTargetCompleter) getTaskCompleter();
        boolean deleteTarget = completer.getDeleteTarget();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            if (jobStatus == JobStatus.SUCCESS) {
                // If we successfully unlinked the target from the array
                // snapshot, but we are not deleting the target, we need
                // to convert the BlockSnapshot instance to a Volume instance
                // as the device is no longer a snapshot target and so should
                // not be represented by a BlockSnapshot instance in ViPR.
                if (!deleteTarget) {
                    List<BlockSnapshot> allSnapshots = completer.getAllSnapshots(dbClient);
                    promoteSnapshotsToVolume(allSnapshots, dbClient);
                } else {
                    // TBD - Update capacity of storage pools when deleted?
                    // SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);
                }

                s_logger.info("Post-processing successful for unlink snapshot session target for task ", getTaskCompleter().getOpId());
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to unlink snapshot session target for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in unlink snapshot session target job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in unlink snapshot session target job status processing", e);
        } finally {
            if (!deleteTarget) {
                // We only want to invoke the completer if we are
                // not deleting the target after we unlink the target
                // from the array snapshot.
                super.updateStatus(jobContext);
            }
        }
    }

    /**
     * Promotes a list of BlockSnapshot instances to their Volume equivalents.  For BlockSnapshot
     * instances that are part of a target ReplicationGroup, a BlockConsistencyGroup shall be created
     * as part of the promotion.
     *
     * @param snapshots List of BlockSnapshot instances.
     * @param dbClient  Database client.
     */
    private void promoteSnapshotsToVolume(List<BlockSnapshot> snapshots, DbClient dbClient) {
        Map<String, BlockConsistencyGroup> groupCache = new HashMap<>();

        for (BlockSnapshot snapshot : snapshots) {
            URI cgId = getBlockConsistencyGroupForPromotedSnapshot(snapshot, groupCache, dbClient);

            // We check to make sure there is not already a volume with the
            // native GUID of the snapshot. This could be the case if we are
            // unlinking a target after restoring a source volume from a
            // linked target volume. In that case, a linked target was created
            // to represent the source volume and now we are unlinking that
            // linked target. The volume in this case already exists.
            List<Volume> volumesWithNativeId = CustomQueryUtility.getActiveVolumeByNativeGuid(dbClient, snapshot.getNativeGuid());
            if (volumesWithNativeId.isEmpty()) {
                URI sourceObjURI = snapshot.getParent().getURI();
                if (URIUtil.isType(sourceObjURI, Volume.class)) {
                    Volume sourceVolume = dbClient.queryObject(Volume.class, sourceObjURI);

                    // Create a new volume to represent the former snapshot target.
                    // We get what we can from the snapshot and for what is not
                    // available in the snapshot, we get from the source.
                    Volume volume = new Volume();
                    volume.setId(URIUtil.createId(Volume.class));
                    volume.setCreationTime(snapshot.getCreationTime());
                    volume.setWWN(snapshot.getWWN());
                    volume.setNativeGuid(snapshot.getNativeGuid());
                    volume.setNativeId(snapshot.getNativeId());
                    volume.setLabel(snapshot.getLabel());
                    volume.setDeviceLabel(snapshot.getDeviceLabel());
                    volume.setAlternateName(snapshot.getAlternateName());
                    volume.setSyncActive(true);
                    volume.setAccessState(sourceVolume.getAccessState());
                    volume.setCapacity(sourceVolume.getCapacity());
                    volume.setProvisionedCapacity(snapshot.getProvisionedCapacity());
                    volume.setAllocatedCapacity(snapshot.getAllocatedCapacity());
                    volume.setThinlyProvisioned(sourceVolume.getThinlyProvisioned());
                    volume.setVirtualPool(sourceVolume.getVirtualPool()); // It is understood that this is not necessary true.
                    volume.setVirtualArray(snapshot.getVirtualArray());
                    volume.setProject(snapshot.getProject());
                    volume.setTenant(sourceVolume.getTenant());
                    volume.setStorageController(snapshot.getStorageController());
                    volume.setPool(sourceVolume.getPool()); // It is understood that this is not necessarily true.
                    StringSet protocols = new StringSet();
                    protocols.addAll(snapshot.getProtocol());
                    volume.setProtocol(protocols);
                    volume.setOpStatus(new OpStatusMap());
                    volume.setConsistencyGroup(cgId);
                    dbClient.createObject(volume);
                }
            }
        }
    }

    /**
     * Given a CG snapshot that is to be promoted, create a new BlockConsistencyGroup based on its
     * ReplicationGroup.  A group cache parameter is accepted and serves to cache BlockConsistencyGroup
     * instances that have already been created.
     *
     * @param snapshot      BlockSnapshot being promoted.
     * @param groupCache    Cache mapping of ReplicationGroup name to BlockConsistencyGroup instances.
     * @param dbClient      Database client.
     * @return              BlockConsistencyGroup URI or null.
     */
    private URI getBlockConsistencyGroupForPromotedSnapshot(BlockSnapshot snapshot, Map<String, BlockConsistencyGroup> groupCache, DbClient dbClient) {
        if (!snapshot.hasConsistencyGroup()) {
            return null;
        }

        // Create new BlockConsistencyGroup instances to track the existing target groups.
        String groupInstance = snapshot.getReplicationGroupInstance();
        BlockConsistencyGroup cg = null;

        if (groupCache.containsKey(groupInstance)) {
            cg = groupCache.get(groupInstance);
        } else {
            cg = new BlockConsistencyGroup();
            cg.setId(URIUtil.createId(BlockConsistencyGroup.class));

            Project project = dbClient.queryObject(Project.class, snapshot.getProject().getURI());
            cg.setProject(new NamedURI(project.getId(), project.getLabel()));
            cg.setTenant(new NamedURI(project.getTenantOrg().getURI(), project.getTenantOrg().getName()));

            String repGrpName = groupInstance.substring(groupInstance.indexOf("+") + 1, groupInstance.length());
            cg.setLabel(repGrpName);

            StringSetMap map = new StringSetMap();
            map.put(snapshot.getStorageController().toString(), new StringSet(Arrays.asList(repGrpName)));
            cg.setSystemConsistencyGroups(map);

            s_logger.info("Creating new BlockConsistencyGroup for ReplicationGroup {} with ID: {}",
                    groupInstance, cg.getId());
            dbClient.createObject(cg);
            groupCache.put(groupInstance, cg);
        }

        return cg.getId();
    }
}
