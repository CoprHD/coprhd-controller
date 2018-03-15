/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;

/**
 * Abstract factory interface containing methods for building validators across various storage systems.
 */
public interface StorageSystemValidatorFactory {

    /**
     * Create an {@link Validator} instance for validating an export mask delete operation.
     *
     * @param ctx   ExportMaskValidationContext
     * @return      Validator
     */
    Validator exportMaskDelete(ExportMaskValidationContext ctx);

    /**
     * Create an {@link Validator} instance for validating removal of a volume from an
     * export group.
     * 
     * @param ctx ExportMaskValidationContext
     * @return
     */
    Validator removeVolumes(ExportMaskValidationContext ctx);

    /**
     * Create an {@link Validator} instance for validating addition of initiators to an
     * export group.
     *
     * @param storage
     * @param exportMaskURI
     * @param initiators
     * @return
     */
    Validator addVolumes(StorageSystem storage, URI exportMaskURI,
            Collection<Initiator> initiators);

    /**
     * Create an {@link Validator} instance for validating an export mask remove initiators operation
     *
     * @param ctx   ExportMaskValidationContext
     * @return      Validator
     */
    Validator removeInitiators(ExportMaskValidationContext ctx);

    /**
     * Create an {@link Validator} instance for validating an export mask add initiators operation
     *
     * @param storage
     * @param exportMask
     * @param volumeURIList
     * @return
     */
    Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList);

    /**
     * Create an {@link Validator} instance for validating a delete volumes operation.
     *
     * @param storage
     * @param volumes
     * @return
     */
    Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes);

    /**
     * Validates the volumes for a single storage system.
     *
     * @param storageSystem
     *            -- Storage System object
     * @param volumes
     *            -- list of Volume objects belonging to that StorageSystem
     * @param delete
     *            -- if true we are deleting, don't flag errors where entity is missing
     * @param remediate
     *            -- if true, attempt remediation
     * @param checks
     *            -- checks to be performed
     * @return -- list of any Volumes that were remediated
     */
    List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate, ValCk[] checks);

    /**
     * Create an {@link Validator} instance for validating an expand volume operation.
     *
     * @param storageSystem
     * @param volume
     * @return
     */
    Validator expandVolumes(StorageSystem storageSystem, Volume volume);

    /**
     * Create an {@link Validator} instance for validating a create snapshot operation.
     *
     * @param storage
     * @param snapshot
     * @param volume
     * @return
     */
    Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume);
}
