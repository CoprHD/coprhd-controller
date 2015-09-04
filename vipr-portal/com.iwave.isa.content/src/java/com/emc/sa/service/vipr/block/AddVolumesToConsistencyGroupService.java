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
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
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

        // add source volume mirror's to the CG
        Map<URI, List<URI>> sourceMirrors = addSourceVolumeMirrors();

        // start continuous copies on the r1 mirrors
        startContinuousCopy(sourceMirrors);

        // add target mirrors to the CG
        Map<URI, List<URI>> targetMirrors = addTargetVolumeMirrors();

        // start continuous copies on the r2 mirrors
        startContinuousCopy(targetMirrors);
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
