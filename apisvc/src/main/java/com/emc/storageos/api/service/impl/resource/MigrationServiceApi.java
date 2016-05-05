/*
 * Copyright (c) 2016 Intel Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * Interface for migration service calls.
 */
public interface MigrationServiceApi {
    /**
     * Define the default MigrationServiceApi implementation.
     */
    public static final String DEFAULT = "default";

    public static final String CONTROLLER_SVC = "controllersvc";
    public static final String CONTROLLER_SVC_VER = "1";

    /**
     * Get the snapshots for the passed volume.
     *
     * @param volume A reference to a volume.
     *
     * @return The snapshots for the passed volume.
     */
    public List<BlockSnapshot> getSnapshots(Volume volume);

    /**
     * Gets the active volumes for the consistency group.
     *
     * @param cg The consistency group.
     *
     * @return A list of all the active volumes for the passed consistency group.
     */
    public List<Volume> getActiveCGVolumes(BlockConsistencyGroup cg);

    /**
     * Determines whether or not the virtual array for the passed volume can be
     * changed to the passed virtual array. Throws a APIException when the
     * varray change is not supported.
     *
     * @param volume A reference to the volume.
     * @param newVarray A reference to the new varray.
     *
     * @throws APIException
     */
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException;

    /**
     * Get the migration capabilities of the passed storage system's driver.
     *
     * @param sourceStorageSystemURI The source storage system for the migration.
     * @param targetStorageSystemURI The target storage system for the migration.
     *
     * @return A boolean that is true if the passed storage systems support a driver assisted migration.
     */
    public void verifyDriverCapabilities(URI sourceStorageSystemURI, URI targetStorageSystemURI)
            throws InternalException;

    /**
     * Defines the API to change the varray for the passed volumes to the passed
     * varray.
     *
     * @param volume A list of volumes.
     * @param cg A reference to the volume's consistency group, or null.
     * @param cgVolumes List of volumes in the CG when not null.
     * @param tgtVarray A reference to the new varray.
     * @param isHostMigration Boolean describing if the migration will be host or driver based
     * @param migrationHostURI URI of the host for host-based migration
     *
     * @throws InternalException
     */
    public void changeVolumesVirtualArray(List<Volume> volume,
            BlockConsistencyGroup cg, List<Volume> cgVolumes, VirtualArray tgtVarray,
            boolean isHostMigration, URI migrationHostURI, String taskId) throws InternalException;

    /**
     * Defines the API to change the vpool for the passed volumes to the passed
     * vpool.
     *
     * @param volumes List of volumes.
     * @param targetVpool A reference to the new vpool.
     * @param vpoolChangeParam vpool change request
     * @param taskId The task identifier.
     * @throws InternalException the internal exception
     */
    public void changeVolumesVirtualPool(List<Volume> volumes,
            VirtualPool targetVpool, VolumeVirtualPoolChangeParam vpoolChangeParam, String taskId)
            throws InternalException;

}
