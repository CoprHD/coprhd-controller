package com.emc.storageos.validation;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;

public class ValidatorImpl implements Validator {
	DbClient dbClient;

	@Override
	public List<URI> volumeURIs(List<URI> uris, boolean delete,
			boolean remediate, StringBuilder msgs, ValCk... checks) {
		List<URI> remediatedURIs = new ArrayList<URI>();
		List<Volume> volumes = dbClient.queryObject(Volume.class, uris);
		List<Volume> remediatedVolumes = volumes(volumes, delete, remediate, msgs, checks);
		for (Volume volume : remediatedVolumes) {
			remediatedURIs.add(volume.getId());
		}
		return remediatedURIs;
	}

	@Override
	public List<Volume> volumes(List<Volume> volumes, boolean delete,
			boolean remediate, StringBuilder msgs, ValCk... checks) {
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
			StorageSystemValidator validator = SystemValidatorFactory.getValidator(system, dbClient);
			if (validator != null) {
				validator.volumes(system, entry.getValue(), delete, remediate, msgs, checks);
			}
		}
		return null;
	}

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
		
}
