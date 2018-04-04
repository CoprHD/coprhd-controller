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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;

/**
 * Top-level factory class for building {@link Validator} instances.
 */
public class ValidatorFactory implements StorageSystemValidatorFactory {

    private DbClient dbClient;
    private Map<String, StorageSystemValidatorFactory> systemFactories;
    private static final Logger log = LoggerFactory.getLogger(ValidatorFactory.class);

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
            // In many cases this method is called with ViPR volumes that have been
            // pre-created for a request. These volumes don't yet exist on the
            // storage system. Upon successful execution of the request, these volumes
            // are updated with the native ids, wwns, etc, of the now existing volumes.
            // We do not want to validate these volume as they do not yet exist and
            // will surely fail validation.
            String nativeGuid = volume.getNativeGuid();
            if (!NullColumnValueGetter.isNullValue(nativeGuid)) {
                if (!systemUriToVolumeList.containsKey(volume.getStorageController())) {
                    systemUriToVolumeList.put(volume.getStorageController(), new ArrayList<Volume>());
                }
                systemUriToVolumeList.get(volume.getStorageController()).add(volume);
            } else {
                log.info("Skipping validation of volume {}:{} which does not have a native guid",
                        volume.getId(), volume.getLabel());
            }
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

    public StorageSystemValidatorFactory vnxe() {
        return systemFactories.get("vnxe");
    }

    public StorageSystemValidatorFactory unity() {
        return systemFactories.get("unity");
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
    public Validator exportMaskDelete(ExportMaskValidationContext ctx) {
        return getSystemValidator(ctx.getStorage()).exportMaskDelete(ctx);
    }

    @Override
    public Validator removeVolumes(ExportMaskValidationContext ctx) {
        return getSystemValidator(ctx.getStorage()).removeVolumes(ctx);
    }

    @Override
    public Validator removeInitiators(ExportMaskValidationContext ctx) {
        return getSystemValidator(ctx.getStorage()).removeInitiators(ctx);
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

    @Override
    public Validator changePortGroupAddPaths(ExportMaskValidationContext ctx) {
        return getSystemValidator(ctx.getStorage()).changePortGroupAddPaths(ctx);
    }

    @Override
    public Validator exportPathAdjustment(ExportMaskValidationContext ctx) {
        return getSystemValidator(ctx.getStorage()).exportPathAdjustment(ctx);
    }
}
