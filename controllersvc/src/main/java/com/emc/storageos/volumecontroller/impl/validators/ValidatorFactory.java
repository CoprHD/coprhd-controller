/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

/**
 * Top-level factory class for building {@link Validator} instances.
 */
public class ValidatorFactory implements StorageSystemValidatorFactory {

    private DbClient dbClient;
    private Map<String, StorageSystemValidatorFactory> systemFactories;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setSystemFactories(Map<String, StorageSystemValidatorFactory> systemFactories) {
        this.systemFactories = systemFactories;
    }

    /**
     * Validates a list of Volumes
     *
     * @param uris
     * @param delete
     * @param remediate
     * @param checks A list of validation checks to be made (of type ValCk).
     * @return
     */
    public List<URI> volumeURIs(List<URI> uris, boolean delete, boolean remediate, ValCk... checks) {
        List<URI> remediatedURIs = new ArrayList<URI>();
        List<Volume> volumes = dbClient.queryObject(Volume.class, uris);
        List<Volume> remediatedVolumes = volumes(volumes, delete, remediate, checks);
        for (Volume volume : remediatedVolumes) {
            remediatedURIs.add(volume.getId());
        }
        return remediatedURIs;
    }

    public List<Volume> volumes(List<Volume> volumes, boolean delete, boolean remediate, ValCk... checks) {
        // Collect remediated volumes
        List<Volume> remediatedVolumes = new ArrayList<Volume>();
        // Partition volumes by StorageSystem
        Map<URI, List<Volume>> systemUriToVolumeList = new HashMap<URI, List<Volume>>();
        for (Volume volume : volumes) {
            if (!systemUriToVolumeList.containsKey(volume.getStorageController())) {
                systemUriToVolumeList.put(volume.getStorageController(), new ArrayList<Volume>());
            }
            systemUriToVolumeList.get(volume.getStorageController()).add(volume);
        }
        // For each Storage System, do the validations
        for (Map.Entry<URI, List<Volume>> entry : systemUriToVolumeList.entrySet()) {
            StorageSystem system = dbClient.queryObject(StorageSystem.class, entry.getKey());

            StorageSystemValidatorFactory validator = getSystemValidator(system);
            if (validator != null) {
                validator.volumes(system, entry.getValue(), delete, remediate, checks);
            }
        }
        return remediatedVolumes;
    }

    public StorageSystemValidatorFactory vmax() {
        return systemFactories.get("vmax");
    }

    public StorageSystemValidatorFactory xtremio() {
        return systemFactories.get("xtremio");
    }

    /**
     * Return the Vplex validator factory.
     *
     * @return VPlexSystemValidatorFactory instance
     */
    public StorageSystemValidatorFactory vplex() {
        return systemFactories.get("vplex");
    }

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList,
            Collection<Initiator> initiatorList) {
        return getSystemValidator(storage).exportMaskDelete(storage, exportMask, volumeURIList, initiatorList);
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        return getSystemValidator(storage).removeVolumes(storage, exportMaskURI, initiators);
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators,
                                   Collection<? extends BlockObject> volumes) {
        return getSystemValidator(storage).removeVolumes(storage, exportMaskURI, initiators, volumes);
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        return getSystemValidator(storage).removeInitiators(storage, exportMask, volumeURIList);
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList, Collection<Initiator> initiators) {
        return getSystemValidator(storage).removeInitiators(storage, exportMask, volumeURIList, initiators);
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        return getSystemValidator(storage).deleteVolumes(storage, volumes);
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
            ValCk[] checks) {
        return getSystemValidator(storageSystem).volumes(storageSystem, volumes, delete, remediate, checks);
    }

    @Override
    public Validator expandVolumes(StorageSystem storage, Volume volume) {
        return getSystemValidator(storage).expandVolumes(storage, volume);
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        return getSystemValidator(storage).createSnapshot(storage, snapshot, volume);
    }

    @Override
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        return getSystemValidator(storage).addVolumes(storage, exportMaskURI, initiators);
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        return getSystemValidator(storage).addInitiators(storage, exportMask, volumeURIList);
    }

    /**
     * Returns the appropriate StorageSystemValidatorFactory based on StorageSystem type.
     *
     * @param system -- StorageSystem object
     * @return -- StorageSystemValidatorFactory
     */
    private StorageSystemValidatorFactory getSystemValidator(StorageSystem system) {
        return systemFactories.get(system.getSystemType());
    }
}
