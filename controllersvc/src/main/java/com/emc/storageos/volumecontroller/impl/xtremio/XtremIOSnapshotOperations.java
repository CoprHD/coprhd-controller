package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
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
			Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
			throws DeviceControllerException {
		
		BlockSnapshot snap = dbClient.queryObject(BlockSnapshot.class, snapshot);
		try {
	        XtremIOClient client = getXtremIOClient(storage);
	        String generatedLabel = nameGenerator.generate("", snap.getLabel(), "",
	                '_', XtremIOConstants.XTREMIO_MAX_VOL_LENGTH);
	        snap.setLabel(generatedLabel);
	        XtremIOVolume createdSnap;
			if(client.isVersion2()) {
				createdSnap = createV2Snapshot(client, storage, snap, generatedLabel, readOnly, taskCompleter);
			} else {
				createdSnap = createV1Snapshot(client, storage, snap, generatedLabel, taskCompleter);
			}
			if(createdSnap != null) {
				snap.setWWN(createdSnap.getVolInfo().get(0));
		        //if created snap wwn is not empty then update the wwn
		        if (!createdSnap.getWwn().isEmpty()) {
		            snap.setWWN(createdSnap.getWwn());
		        } 
	
		        snap.setNativeId(createdSnap.getVolInfo().get(0));
		        String nativeGuid = NativeGUIDGenerator.getNativeGuidforSnapshot(storage, storage.getSerialNumber(), snap.getNativeId());
		        snap.setNativeGuid(nativeGuid);
		        snap.setIsSyncActive(true);
			}
			
	        dbClient.persistObject(snap);
		} catch(Exception e) {
            _log.error("Snapshot creation failed",e);
            snap.setInactive(true);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

	}
	
	private XtremIOVolume createV1Snapshot(XtremIOClient client, StorageSystem storage, BlockSnapshot snap, String snapLabel, 
			TaskCompleter taskCompleter) throws Exception {
		Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
        URI projectUri = snap.getProject().getURI();
        String snapFolderName = client.createFoldersForVolumeAndSnaps(getVolumeFolderName(projectUri, storage))
        		.get(XtremIOConstants.SNAPSHOT_KEY);
        
        client.createSnapshot(parentVolume.getLabel(), snapLabel, snapFolderName);
        XtremIOVolume createdSnap = client.getSnapShotDetails(snap.getLabel());
        
        return createdSnap;
	}

	private XtremIOVolume createV2Snapshot(XtremIOClient client, StorageSystem storage, BlockSnapshot snap, String snapLabel, 
			Boolean readOnly, TaskCompleter taskCompleter) throws Exception {
		Volume parentVolume = dbClient.queryObject(Volume.class, snap.getParent().getURI());
        URI projectUri = snap.getProject().getURI();
        String snapTagName = client.createTagsForVolumeAndSnaps(getVolumeFolderName(projectUri, storage)).get(XtremIOConstants.SNAPSHOT_KEY);
        List<String> snapshotTags = new ArrayList<String>();
        List<String> volumeList = new ArrayList<String>();
        snapshotTags.add(snapTagName);
        volumeList.add(parentVolume.getLabel());
	    String snapshotType = readOnly ? XtremIOConstants.XTREMIO_READ_ONLY_TYPE : XtremIOConstants.XTREMIO_REGULAR_TYPE;
	    client.createV2Snapshot(null, null, null, "", snapLabel, snapshotType, snapshotTags, volumeList);
	    XtremIOVolume createdSnap = client.getSnapShotDetails(snapLabel);
	    
	    return createdSnap;
		
	}
	
	@Override
	public void createGroupSnapshots(StorageSystem storage,
			List<URI> snapshotList, Boolean createInactive,
			Boolean readOnly, TaskCompleter taskCompleter) throws DeviceControllerException {
	
		try {
			URI snapshot = snapshotList.get(0);
	        BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapshot);
			URI cgId = snapshotObj.getConsistencyGroup();
	        if (cgId != null) {
	            BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, cgId);
	        }
		} catch(Exception e) {
            _log.error("Snapshot creation failed",e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }

	}

	@Override
	public void activateSingleVolumeSnapshot(StorageSystem storage,
			URI snapshot, TaskCompleter taskCompleter)
			throws DeviceControllerException {
		throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
	}

	@Override
	public void activateGroupSnapshots(StorageSystem storage, URI snapshot,
			TaskCompleter taskCompleter) throws DeviceControllerException {
		throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
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
		throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
	}

	@Override
	public void copyGroupSnapshotsToTarget(StorageSystem storage,
			List<URI> snapshotList, TaskCompleter taskCompleter)
			throws DeviceControllerException {
		throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
	}

	@Override
	public void terminateAnyRestoreSessions(StorageSystem storage,
			BlockObject from, URI volume, TaskCompleter taskCompleter)
			throws Exception {
		throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
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
