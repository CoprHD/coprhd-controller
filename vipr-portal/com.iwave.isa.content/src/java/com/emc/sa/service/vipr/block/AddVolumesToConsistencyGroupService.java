/*
 * Copyright 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.GetActiveContinuousCopiesForVolume;
import com.emc.sa.service.vipr.block.tasks.GetActiveFullCopiesForVolume;
import com.emc.sa.service.vipr.block.tasks.GetActiveSnapshotsForVolume;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.google.common.collect.Lists;

@Service("AddVolumesToConsistencyGroup")
public class AddVolumesToConsistencyGroupService extends ViPRService {

    @Param(PROJECT)
    protected URI project;

    @Param(CONSISTENCY_GROUP)
    protected URI consistencyGroup;
    
    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Override
    public void execute() throws Exception {
        // add volumes to CG
        BlockStorageUtils.addVolumesToConsistencyGroup(consistencyGroup, uris(volumeIds));

        // start continuous copies on one of the volumes
        BlockObjectRestRep blockObject = BlockStorageUtils.getBlockResource(uri(volumeIds.get(0)));
        List<URI> targets = BlockStorageUtils.getSrdfTargetVolumes(blockObject);
        if (!targets.isEmpty()) {
            BlockStorageUtils.createContinuousCopy(uri(volumeIds.get(0)), null, 1, BlockStorageUtils.COPY_SRDF, targets.get(0));
        }


        // Continuous copy
        // add source volume mirror's to the CG
        Map<URI, List<URI>> sourceMirrors = addSourceVolumeMirrors();

        // start continuous copies on the r1 mirrors
        startContinuousCopy(sourceMirrors);

        // add target mirrors to the CG
        Map<URI, List<URI>> targetMirrors = addTargetVolumeMirrors();

        // start continuous copies on the r2 mirrors
        startContinuousCopy(targetMirrors);


        // Snapshot
        // add source volumes' snapshots to the CG
        Map<URI, List<URI>> sourceSnapshots = addSourceVolumeSnapshots();

        // start snapshot on the r1 snapshots
        startSnapshot(sourceSnapshots);

        // add target volumes' snapshots to the CG
        Map<URI, List<URI>> targetSnapshots = addTargetVolumeSnapshots();

        // start snapshot on the r2 snapshots
        startSnapshot(targetSnapshots);


        // Full copy
        // add source volumes' full copies to the CG
        Map<URI, List<URI>> sourceFullCopies = addSourceVolumeFullCopies();

        // start full copies on the r1 full copies
        startFullCopy(sourceFullCopies);

        // add target volumes' full copies to the CG
        Map<URI, List<URI>> targetFullCopies = addTargetVolumeFullCopies();

        // start full copies on the r2 full copies
        startFullCopy(targetFullCopies);
    }

    /**
     * Start continuous copy on one block object that has a mirror
     * 
     * @param mirrors map of block object to mirrors
     */
    public void startContinuousCopy(Map<URI, List<URI>> mirrors) {
        Iterator<URI> it = mirrors.keySet().iterator();
        while (it.hasNext()) {
            URI blockObject = it.next();
            List<URI> copyIds = mirrors.get(blockObject);
            if (!copyIds.isEmpty()) {
                BlockStorageUtils.createContinuousCopy(blockObject, null, 1, BlockStorageUtils.COPY_NATIVE, copyIds.get(0));
                break;
            }
        }
    }

    /**
     * Start full copy on one block object that has a full copy
     * 
     * @param fullCopies map of block object to full copies
     */
    public void startFullCopy(Map<URI, List<URI>> fullCopies) {
        Iterator<URI> it = fullCopies.keySet().iterator();
        while (it.hasNext()) {
            URI blockObject = it.next();
            List<URI> copyIds = fullCopies.get(blockObject);
            if (!copyIds.isEmpty()) {
                BlockStorageUtils.startFullCopy(copyIds.get(0));
                break;
            }
        }
    }

    /**
     * Start snapshot on one block object that has a snapshot
     * 
     * @param snapshots map of block object to snapshots
     */
    public void startSnapshot(Map<URI, List<URI>> snapshots) {
        Iterator<URI> it = snapshots.keySet().iterator();
        while (it.hasNext()) {
            URI blockObject = it.next();
            List<URI> copyIds = snapshots.get(blockObject);
            if (!copyIds.isEmpty()) {
                BlockStorageUtils.startSnapshot(copyIds.get(0));
                break;
            }
        }
    }

    /**
     * Get mirrors for a given volume id
     * 
     * @param volumeId the volume id to use
     * @return list of mirror ids
     */
    public List<URI> getMirrors(URI volumeId) {
        List<URI> blockMirrors = Lists.newArrayList();
        List<BlockMirrorRestRep> blockMirrorRestReps = execute(new GetActiveContinuousCopiesForVolume(volumeId));
        for (BlockMirrorRestRep blockMirrorId : blockMirrorRestReps) {
            blockMirrors.add(blockMirrorId.getId());
        }
        return blockMirrors;
    }

    /**
     * Get full copies for a given volume id
     * 
     * @param volumeId the volume id to use
     * @return list of full copy ids
     */
    public List<URI> getFullCopies(URI volumeId) {
        List<URI> fullCopies = Lists.newArrayList();
        List<VolumeRestRep> fullCopyRestReps = execute(new GetActiveFullCopiesForVolume(volumeId));
        for (VolumeRestRep fullCopyId : fullCopyRestReps) {
            fullCopies.add(fullCopyId.getId());
        }
        return fullCopies;
    }

    /**
     * Get snapshots for a given volume id
     * 
     * @param volumeId the volume id to use
     * @return list of snapshot ids
     */
    public List<URI> getSnapshots(URI volumeId) {
        List<URI> blockSnapshots = Lists.newArrayList();
        List<BlockSnapshotRestRep> blockSnapshotRestReps = execute(new GetActiveSnapshotsForVolume(volumeId));
        for (BlockSnapshotRestRep blockSnapshotId : blockSnapshotRestReps) {
            blockSnapshots.add(blockSnapshotId.getId());
        }
        return blockSnapshots;
    }

    /**
     * Get SRDF targets for a given volume id
     * 
     * @param volumeId the volume id to use
     * @return list of target ids
     */
    public List<URI> getTargets(URI volumeId) {
        BlockObjectRestRep blockObject = BlockStorageUtils.getBlockResource(volumeId);
        return BlockStorageUtils.getSrdfTargetVolumes(blockObject);
    }

    /**
     * Adds all source volume mirrors to the consistency group
     * 
     * @return map of source volume to mirrors
     */
    public Map<URI, List<URI>> addSourceVolumeMirrors() {
        List<URI> blockMirrors = Lists.newArrayList();
        Map<URI, List<URI>> mirrorsMap = new HashMap<>();
        for (URI volumeId : uris(volumeIds)) {
            List<URI> mirrors = getMirrors(volumeId);
            blockMirrors.addAll(mirrors);
            mirrorsMap.put(volumeId, mirrors);
        }
        if (!blockMirrors.isEmpty()) {
            BlockStorageUtils.addVolumesToConsistencyGroup(consistencyGroup, blockMirrors);
        }
        return mirrorsMap;
    }

    /**
     * Adds all target volume mirrors to the target consistency group
     * 
     * @return map of target volume to mirrors
     */
    public Map<URI, List<URI>> addTargetVolumeMirrors() {
        List<URI> blockMirrors = Lists.newArrayList();
        Map<URI, List<URI>> mirrorsMap = new HashMap<>();
        URI targetCG = null;
        for (URI volumeId : uris(volumeIds)) {
            List<URI> targets = getTargets(volumeId);
            for (URI target : targets) {
                if (targetCG == null) {
                    targetCG = getConsistencyGroup(target);
                }
                List<URI> mirrors = getMirrors(target);
                blockMirrors.addAll(mirrors);
                mirrorsMap.put(target, mirrors);
            }
        }
        if (!blockMirrors.isEmpty() && targetCG != null) {
            BlockStorageUtils.addVolumesToConsistencyGroup(targetCG, blockMirrors);
        }
        return mirrorsMap;
    }

    /**
     * Adds all source volumes' full copies to the consistency group
     * 
     * @return map of source volume to full copies
     */
    public Map<URI, List<URI>> addSourceVolumeFullCopies() {
        List<URI> fullCopies = Lists.newArrayList();
        Map<URI, List<URI>> fullCopiesMap = new HashMap<>();
        for (URI volumeId : uris(volumeIds)) {
            List<URI> volumeFullCopies = getFullCopies(volumeId);
            fullCopies.addAll(volumeFullCopies);
            fullCopiesMap.put(volumeId, volumeFullCopies);
        }
        if (!fullCopies.isEmpty()) {
            BlockStorageUtils.addVolumesToConsistencyGroup(consistencyGroup, fullCopies);
        }
        return fullCopiesMap;
    }

    /**
     * Adds all target volumes' full copies to the target consistency group
     * 
     * @return map of target volume to full copies
     */
    public Map<URI, List<URI>> addTargetVolumeFullCopies() {
        List<URI> fullCopies = Lists.newArrayList();
        Map<URI, List<URI>> fullCopiesMap = new HashMap<>();
        URI targetCG = null;
        for (URI volumeId : uris(volumeIds)) {
            List<URI> targets = getTargets(volumeId);
            for (URI target : targets) {
                if (targetCG == null) {
                    targetCG = getConsistencyGroup(target);
                }
                List<URI> volumeFullCopies = getFullCopies(target);
                fullCopies.addAll(volumeFullCopies);
                fullCopiesMap.put(target, volumeFullCopies);
            }
        }
        if (!fullCopies.isEmpty() && targetCG != null) {
            BlockStorageUtils.addVolumesToConsistencyGroup(targetCG, fullCopies);
        }
        return fullCopiesMap;
    }

    /**
     * Adds all source volumes' snapshots to the consistency group
     * 
     * @return map of source volume to snapshots
     */
    public Map<URI, List<URI>> addSourceVolumeSnapshots() {
        List<URI> blockSnapshots = Lists.newArrayList();
        Map<URI, List<URI>> snapshotsMap = new HashMap<>();
        for (URI volumeId : uris(volumeIds)) {
            List<URI> snapshots = getSnapshots(volumeId);
            blockSnapshots.addAll(snapshots);
            snapshotsMap.put(volumeId, snapshots);
        }
        if (!blockSnapshots.isEmpty()) {
            BlockStorageUtils.addVolumesToConsistencyGroup(consistencyGroup, blockSnapshots);
        }
        return snapshotsMap;
    }

    /**
     * Adds all target volumes' snapshots to the target consistency group
     * 
     * @return map of target volume to snapshots
     */
    public Map<URI, List<URI>> addTargetVolumeSnapshots() {
        List<URI> blockSnapshots = Lists.newArrayList();
        Map<URI, List<URI>> snapshotsMap = new HashMap<>();
        URI targetCG = null;
        for (URI volumeId : uris(volumeIds)) {
            List<URI> targets = getTargets(volumeId);
            for (URI target : targets) {
                if (targetCG == null) {
                    targetCG = getConsistencyGroup(target);
                }
                List<URI> snapshots = getSnapshots(target);
                blockSnapshots.addAll(snapshots);
                snapshotsMap.put(target, snapshots);
            }
        }
        if (!blockSnapshots.isEmpty() && targetCG != null) {
            BlockStorageUtils.addVolumesToConsistencyGroup(targetCG, blockSnapshots);
        }
        return snapshotsMap;
    }

    /**
     * Get consistency group id for a given block id
     * 
     * @param blockId the block id
     * @return consistency group URI or null if block volume doesn't belong to a consistency group
     */
    public URI getConsistencyGroup(URI blockId) {
        BlockObjectRestRep blockObject = BlockStorageUtils.getBlockResource(blockId);
        return blockObject.getConsistencyGroup() != null ? blockObject.getConsistencyGroup().getId() : null;
    }
}
