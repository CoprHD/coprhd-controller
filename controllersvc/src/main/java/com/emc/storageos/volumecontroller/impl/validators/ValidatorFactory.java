package com.emc.storageos.volumecontroller.impl.validators;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A provider of {@link StorageSystemValidatorFactory} instances across the various storage systems.
 */
public class ValidatorFactory {

    private DbClient dbClient;
    private Map<String, StorageSystemValidatorFactory> systemFactories;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setSystemFactories(Map<String, StorageSystemValidatorFactory> systemFactories) {
        this.systemFactories = systemFactories;
    }

    public List<URI> volumeURIs(List<URI> uris, boolean delete, boolean remediate, StringBuilder msgs, ValCk... checks) {
        List<URI> remediatedURIs = new ArrayList<URI>();
        List<Volume> volumes = dbClient.queryObject(Volume.class, uris);
        List<Volume> remediatedVolumes = volumes(volumes, delete, remediate, msgs, checks);
        for (Volume volume : remediatedVolumes) {
            remediatedURIs.add(volume.getId());
        }
        return remediatedURIs;
    }

    public List<Volume> volumes(List<Volume> volumes, boolean delete,
                                boolean remediate, StringBuilder msgs, ValCk... checks) {
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
                validator.volumes(system, entry.getValue(), delete, remediate, msgs, checks);
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

    public StorageSystemValidatorFactory vplex() {
        return systemFactories.get("vplex");
    }

    private StorageSystemValidatorFactory getSystemValidator(StorageSystem system) {
        return systemFactories.get(system.getSystemType());
    }
}
