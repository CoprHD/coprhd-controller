/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class StorageSynchronizedProcessor extends StorageProcessor {

    private Logger _log = LoggerFactory.getLogger(StorageSynchronizedProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final Iterator<?> it = (Iterator<?>) resultObj;
        @SuppressWarnings("unchecked")
        Map<String, RemoteMirrorObject> volumeToRAGroupMap = (Map<String, RemoteMirrorObject>) keyMap.get(Constants.UN_VOLUME_RAGROUP_MAP);
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        while (it.hasNext()) {
            try {
                final CIMInstance instance = (CIMInstance) it.next();
                CIMObjectPath volumePath = instance.getObjectPath();
                CIMObjectPath sourcePath = (CIMObjectPath) volumePath.getKey(
                        Constants._SystemElement).getValue();
                CIMObjectPath destPath = (CIMObjectPath) volumePath.getKey(
                        Constants._SyncedElement).getValue();
                String mode = instance.getPropertyValue(SmisConstants.CP_MODE).toString();
                String copyMode = null;
                if (mode != null) {
                    copyMode = SupportedCopyModes.getCopyMode(mode);
                }
                String sourceNativeGuid = createKeyfromPath(sourcePath);
                sourceNativeGuid = sourceNativeGuid.replace("VOLUME", "UNMANAGEDVOLUME");
                _log.debug("Source Native Guid {}", sourceNativeGuid);
                String targetNativeGuid = createKeyfromPath(destPath);
                targetNativeGuid = targetNativeGuid.replace("VOLUME", "UNMANAGEDVOLUME");
                _log.debug("Target Native Guid {}", targetNativeGuid);
                // if child
                if (volumeToRAGroupMap.containsKey(targetNativeGuid)) {
                    // set Parent
                    // copyMode and raGroup Uri are already part of RemoteMirrorObject
                    _log.debug("Found Target Native Guid {}", targetNativeGuid);
                    RemoteMirrorObject rmObj = volumeToRAGroupMap.get(targetNativeGuid);
                    _log.debug("Found Target Remote Object {}", rmObj);
                    rmObj.setSourceVolumeNativeGuid(sourceNativeGuid);
                    rmObj.setType(RemoteMirrorObject.Types.TARGET.toString());

					if (checkForCopyModeUpdate(rmObj.getCopyMode(), copyMode)) {
						_log.info(
								"Target Volume with native GUID {} detected with set Copy Mode : {}; Copy Mode from array: {}",
								targetNativeGuid, rmObj.getCopyMode(), copyMode);
						rmObj.setCopyMode(copyMode);
						updateCopyModeInRAGroupObjectIfRequired(copyMode, rmObj);
					}
                }

                if (volumeToRAGroupMap.containsKey(sourceNativeGuid)) {
                    _log.debug("Found Source Native Guid {}", sourceNativeGuid);
                    RemoteMirrorObject rmObj = volumeToRAGroupMap.get(sourceNativeGuid);
                    _log.debug("Found Source Remote Object {}", rmObj);
                    if (null == rmObj.getTargetVolumenativeGuids()) {
                        rmObj.setTargetVolumenativeGuids(new StringSet());
                    }

                    if (!findVolumesArefromSameArray(sourceNativeGuid,
                            targetNativeGuid)) {
                        rmObj.getTargetVolumenativeGuids().add(targetNativeGuid);
                        // Set this only for the volumes have remote replication
                        rmObj.setType(RemoteMirrorObject.Types.SOURCE.toString());
                        _log.debug("Updated Target Volumes", rmObj);
					}
                    
					// If while iterating the source volume is encountered and if there is any
					// deviation we will have to update it else there will be database
					// inconsistencies.
					if (checkForCopyModeUpdate(rmObj.getCopyMode(), copyMode)) {
						_log.info(
								"Source Volume with native GUID {} detected with set Copy Mode : {}; Copy Mode from array: {}",
								sourceNativeGuid, rmObj.getCopyMode(), copyMode);
						rmObj.setCopyMode(copyMode);
						updateCopyModeInRAGroupObjectIfRequired(copyMode, rmObj);
					}
                }
            } catch (Exception e) {
                _log.error("Finding out Parent of a target Volume failed", e);
            }
        }

    }

    /**
     * Update copy mode in RA group objects in DB if they don't reflect the latest.
     *
     * @param copyMode the copy mode from StorageSynchornized
     * @param rmObj the RemoteMirrorObject
     */
    private void updateCopyModeInRAGroupObjectIfRequired(String copyMode, RemoteMirrorObject rmObj) {
        // get source array RA group
        URI raGroupURI = rmObj.getTargetRaGroupUri();
        RemoteDirectorGroup raGroup = _dbClient.queryObject(RemoteDirectorGroup.class, raGroupURI);
        // If pairs got added to RA group outside, after the storage systems are registered, the
        // supported copy mode will still be ALL.
        // update the latest copy mode in RA group object in DB
		if (checkForCopyModeUpdate(raGroup.getSupportedCopyMode(), copyMode)) {
			_log.info("RDF Group {} detected with set Copy Mode : {}; Copy Mode from array: {}", raGroup.getLabel(),
					raGroup.getSupportedCopyMode(), copyMode);
			raGroup.setSupportedCopyMode(copyMode);
			_dbClient.updateObject(raGroup);
		}

        // get target array RA group
        URI targetRaGroupURI = rmObj.getSourceRaGroupUri();
        RemoteDirectorGroup targetRaGroup = _dbClient.queryObject(RemoteDirectorGroup.class, targetRaGroupURI);
		if (checkForCopyModeUpdate(targetRaGroup.getSupportedCopyMode(), copyMode)) {
			_log.info("RDF Group {} detected with set Copy Mode : {}; Copy Mode from array: {}",
					targetRaGroup.getLabel(), targetRaGroup.getSupportedCopyMode(), copyMode);
			targetRaGroup.setSupportedCopyMode(copyMode);
			_dbClient.updateObject(targetRaGroup);
		}
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

}
