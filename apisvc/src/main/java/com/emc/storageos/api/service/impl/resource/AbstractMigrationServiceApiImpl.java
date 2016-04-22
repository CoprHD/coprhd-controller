/*
 * Copyright (c) 2016 Intel Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.host.InitiatorList;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public abstract class AbstractMigrationServiceApiImpl<T> implements
        MigrationServiceApi {

    protected T _scheduler;

    protected DbClient _dbClient;

    // Db client getter/setter

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    // StorageScheduler getter/setter

    public void setBlockScheduler(T scheduler) {
        _scheduler = scheduler;
    }

    public T getBlockScheduler() {
        return _scheduler;
    }

    /**
     * Get the snapshots for the passed volume.
     *
     * @param volume A reference to a volume.
     *
     * @return The snapshots for the passed volume.
     */
    @Override
    public List<BlockSnapshot> getSnapshots(Volume volume) {
        URIQueryResultList snapshotURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                volume.getId()), snapshotURIs);
        List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotURIs);
        return snapshots;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Volume> getActiveCGVolumes(BlockConsistencyGroup cg) {
        List<Volume> volumeList = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        _dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId()),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = _dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (!volume.getInactive()) {
                volumeList.add(volume);
            }
        }
        return volumeList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException {
        throw APIException.badRequests.changesNotSupportedFor("VirtualArray",
                String.format("volume %s", volume.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void migrateVolumesVirtualArray(List<Volume> volumes,
            BlockConsistencyGroup cg, List<Volume> cgVolumes, VirtualArray tgtVarray,
            boolean isHostMigration, InitiatorList initiatorList, String taskId) throws InternalException {
        throw APIException.methodNotAllowed.notSupported();
    }

}
