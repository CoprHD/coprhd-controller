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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockExportController;

public abstract class AbstractMigrationServiceApiImpl<T> implements
        MigrationServiceApi {

    // A logger reference.
    private static final Logger s_logger = LoggerFactory
            .getLogger(AbstractBlockServiceApiImpl.class);

    protected T _scheduler;

    protected DbClient _dbClient;

    private CoordinatorClient _coordinator;

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

    // Coordinator getter/setter

    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    public CoordinatorClient getCoordinator() {
        return _coordinator;
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
     * Verifies that the passed volumes correspond to the passed volumes from
     * a consistency group.
     *
     * @param volumes The volumes to verify
     * @param cgVolumes The list of active volumes in a CG.
     */
    protected void verifyVolumesInCG(List<Volume> volumes, List<Volume> cgVolumes) {
        // The volumes counts must match. If the number of volumes
        // is less, then not all volumes in the CG were passed.
        if (volumes.size() < cgVolumes.size()) {
            throw APIException.badRequests.cantChangeVarrayNotAllCGVolumes();
        }

        // Make sure only the CG volumes are selected.
        for (Volume volume : volumes) {
            boolean found = false;
            for (Volume cgVolume : cgVolumes) {
                if (volume.getId().equals(cgVolume.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                s_logger.error("Volume {}:{} not found in CG", volume.getId(), volume.getLabel());
                throw APIException.badRequests.cantChangeVarrayVolumeIsNotInCG();
            }
        }
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
    public void verifyDriverCapabilities(URI sourceStorageSystemURI, URI targetStorageSystemURI)
            throws InternalException {
        throw APIException.methodNotAllowed.notSupported();
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
    public void changeVolumesVirtualArray(List<Volume> volumes,
            BlockConsistencyGroup cg, List<Volume> cgVolumes, VirtualArray tgtVarray,
            boolean isHostMigration, URI migrationHostURI, String taskId) throws InternalException {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @throws InternalException
     */
    @Override
    public void changeVolumesVirtualPool(List<Volume> volumes, VirtualPool vpool,
            VolumeVirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        /**
         * 'Auto-tiering policy change' operation supports multiple volume processing.
         * At present, other operations only support single volume processing.
         */
        if (checkCommonVpoolUpdates(volumes, vpool, taskId)) {
            return;
        }
        throw APIException.methodNotAllowed.notSupported();
    }


    /**
     * Checks for Vpool updates that can be done on any device type.
     * For now, this is just the Export Path Params or Auto-tiering policy change.
     * If the update was processed, return true, else false.
     *
     * @param volumes
     * @param newVirtualPool
     * @param taskId
     * @return true if update processed
     * @throws InternalException
     */
    protected boolean checkCommonVpoolUpdates(List<Volume> volumes, VirtualPool newVirtualPool,
            String taskId) throws InternalException {
        VirtualPool volumeVirtualPool = _dbClient.queryObject(VirtualPool.class, volumes.get(0).getVirtualPool());
        StringBuffer notSuppReasonBuff = new StringBuffer();
        if (VirtualPoolChangeAnalyzer.isSupportedPathParamsChange(volumes.get(0),
                volumeVirtualPool, newVirtualPool, _dbClient, notSuppReasonBuff)) {
            BlockExportController exportController = getController(BlockExportController.class, BlockExportController.EXPORT);
            for (Volume volume : volumes) {
                exportController.updateVolumePathParams(volume.getId(), newVirtualPool.getId(), taskId);
            }
            return true;
        }

        if (VirtualPoolChangeAnalyzer
                .isSupportedAutoTieringPolicyAndLimitsChange(volumes.get(0), volumeVirtualPool,
                        newVirtualPool, _dbClient, notSuppReasonBuff)) {
            /**
             * If it is a Auto-tiering policy change, it is sufficient to check on one volume in the list.
             * Mixed type volumes case has already been taken care in BlockService API.
             */
            BlockExportController exportController = getController(BlockExportController.class, BlockExportController.EXPORT);
            List<URI> volumeURIs = new ArrayList<URI>();
            for (Volume volume : volumes) {
                volumeURIs.add(volume.getId());
            }
            exportController.updatePolicyAndLimits(volumeURIs, newVirtualPool.getId(), taskId);
            return true;
        }

        return false;
    }

    /**
     * Looks up controller dependency for given hardware type.
     * If cannot locate controller for defined hardware type, lookup controller for
     * EXTERNALDEVICE.
     *
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        T controller;
        try {
            controller = _coordinator.locateService(
                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
        } catch (RetryableCoordinatorException rex) {
            controller = _coordinator.locateService(
                    clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, Constants.EXTERNALDEVICE, clazz.getSimpleName());
        }
        return controller;
    }

}
