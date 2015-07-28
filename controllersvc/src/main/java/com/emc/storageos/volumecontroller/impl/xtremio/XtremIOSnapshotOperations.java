package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;

public class XtremIOSnapshotOperations extends XtremIOOperations implements SnapshotOperations {
	
	private static final Logger _log = LoggerFactory.getLogger(XtremIOSnapshotOperations.class);

    private NameGenerator nameGenerator;
    
    public void setNameGenerator(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }
    
	@Override
	public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
			Boolean createInactive, TaskCompleter taskCompleter)
			throws DeviceControllerException {

        XtremIOClient client = getXtremIOClient(storage);
        BlockSnapshot snap = dbClient.queryObject(BlockSnapshot.class, snapshot);
        
		if(client.isVersion2()) {
			
		} else {
			createV1Snapshot(client, storage, snap, taskCompleter);
		}

	}
	
	private void createV1Snapshot(XtremIOClient client, StorageSystem storage, BlockSnapshot snap, TaskCompleter taskCompleter) 
			throws DeviceControllerException {
		try {
        	Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
            URI projectUri = snap.getProject().getURI();
            String snapFolderName = client.createFoldersForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage))
            		.get(XtremIOConstants.SNAPSHOT_KEY);
            String generatedLabel = nameGenerator.generate("", snap.getLabel(), "",
                    '_', XtremIOConstants.XTREMIO_MAX_VOL_LENGTH);
            snap.setLabel(generatedLabel);
            client.createSnapshot(parentVolume.getLabel(), generatedLabel, snapFolderName);
            XtremIOVolume createdSnap = client.getSnapShotDetails(snap.getLabel());
            snap.setNativeId(createdSnap.getVolInfo().get(0));
            snap.setWWN(createdSnap.getVolInfo().get(0));
            //if created nsap wwn is not empty then update the wwn
            if (!createdSnap.getWwn().isEmpty()) {
                snap.setWWN(createdSnap.getWwn());
            } 

            String nativeGuid = NativeGUIDGenerator.getNativeGuidforSnapshot(storage, storage.getSerialNumber(), snap.getNativeId());
            snap.setNativeGuid(nativeGuid);
            snap.setIsSyncActive(true);
            dbClient.persistObject(snap);
        }catch(Exception e) {
            _log.error("Snapshot creation failed",e);
            snap.setInactive(true);
        }
	}

	private void createV2Snapshot(XtremIOClient client, StorageSystem storage, BlockSnapshot snap, TaskCompleter taskCompleter) 
			throws DeviceControllerException {
		try {
			Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
	        URI projectUri = snap.getProject().getURI();
	        String snapFolderName = client.createTagsForVolumeAndSnaps(client, getVolumeFolderName(projectUri, storage)).get(XtremIOConstants.SNAPSHOT_KEY);
		} catch(Exception e) {
            _log.error("Snapshot creation failed",e);
            snap.setInactive(true);
        }
	}
	
	@Override
	public void createGroupSnapshots(StorageSystem storage,
			List<URI> snapshotList, Boolean createInactive,
			TaskCompleter taskCompleter) throws DeviceControllerException {
		

	}

	@Override
	public void activateSingleVolumeSnapshot(StorageSystem storage,
			URI snapshot, TaskCompleter taskCompleter)
			throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void activateGroupSnapshots(StorageSystem storage, URI snapshot,
			TaskCompleter taskCompleter) throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
			TaskCompleter taskCompleter) throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void deleteGroupSnapshots(StorageSystem storage, URI snapshot,
			TaskCompleter taskCompleter) throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume,
			URI snapshot, TaskCompleter taskCompleter)
			throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void restoreGroupSnapshots(StorageSystem storage, URI volume,
			URI snapshot, TaskCompleter taskCompleter)
			throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void copySnapshotToTarget(StorageSystem storage, URI snapshot,
			TaskCompleter taskCompleter) throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void copyGroupSnapshotsToTarget(StorageSystem storage,
			List<URI> snapshotList, TaskCompleter taskCompleter)
			throws DeviceControllerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void terminateAnyRestoreSessions(StorageSystem storage,
			BlockObject from, URI volume, TaskCompleter taskCompleter)
			throws Exception {
		// TODO Auto-generated method stub

	}
	
	private String getVolumeFolderName(URI projectURI, StorageSystem storage) {
        String volumeGroupFolderName = "";
        Project project = dbClient.queryObject(Project.class, projectURI);
        DataSource dataSource = dataSourceFactory.createXtremIOVolumeFolderNameDataSource(project, storage);
        volumeGroupFolderName = customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.XTREMIO_VOLUME_FOLDER_NAME, storage.getSystemType(), dataSource);
        
        return volumeGroupFolderName;
    }


}
