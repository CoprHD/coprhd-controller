/*
 * Copyright (c) 2016 Intel Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyUtils;
import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionUtils;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/*
 * Default implementation of the Migration Service Api.
 */
public class DefaultMigrationServiceApiImpl extends AbstractMigrationServiceApiImpl {
    private static final Logger s_logger = LoggerFactory
            .getLogger(MigrationBlockServiceApiImpl.class);

    /**
     * {@inheritDoc}
     */
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException {

        // The volume cannot be snapshotted
        if (BlockSnapshotSessionUtils.volumeHasSnapshotSession(srcVolume, _dbClient)) {
            s_logger.info("The volume has snapshots.");
            throw APIException.badRequests.changesNotSupportedFor("VirtualArray", "snapshotted volumes");
        }

        // The volume cannot be exported
        if (volume.isVolumeExported(_dbClient)) {
            s_logger.info("The volume is exported.");
            throw APIException.badRequests.changesNotSupportedFor("VirtualArray", "exported volumes");
        }

        // The volume cannot be mirrored
        StringSet mirrorURIs = volume.getMirrors();
        if (mirrorURIs != null && !mirrorURIs.isEmpty()) {
            List<VplexMirror> mirrors = _dbClient.queryObject(VplexMirror.class,
                    StringSetUtil.stringSetToUriList(mirrorURIs));
            if (mirrors != null && !mirrors.isEmpty()) {
                throw APIException.badRequests.changesNotSupportedFor("VirtualArray", "mirrored volumes");
            }
        }

        // If the vpool has assigned varrays, the vpool must be assigned
        // to the new varray.
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        StringSet vpoolVarrayIds = vpool.getVirtualArrays();
        if ((vpoolVarrayIds == null) || (!vpoolVarrayIds.contains(newVarray.getId().toString()))) {
            throw APIException.badRequests.vpoolNotAssignedToVarrayForVarrayChange(
                    vpool.getLabel(), volume.getLabel());
        }

        // The volume must be detached from all full copies.
        if (BlockFullCopyUtils.volumeHasFullCopySession(volume, _dbClient)) {
            throw APIException.badRequests.volumeForVarrayChangeHasFullCopies(volume.getLabel());
        }

    }

}
