/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.SRDF;

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
import com.emc.storageos.db.client.model.RemoteDirectorGroup.CopyStates;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

public class VolumeToSynchronizedRefProcessor extends StorageProcessor {
    private static final String MODE = "Mode";
    private static final String EMCCopyState = "EMCCopyState";
    private static final String CONSISTENT_ID = "6004";
    private List<Object> args;
    private Logger _log = LoggerFactory.getLogger(VolumeToSynchronizedRefProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            CIMObjectPath volumePath = getObjectPathfromCIMArgument(args);
            String volumeNativeGuid = getVolumeNativeGuid(volumePath);
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            @SuppressWarnings("unchecked")
            Map<String, URI> volumeToRAGroupMap = (Map<String, URI>) keyMap.get(Constants.RAGROUP);
            URI remoteRAGroupUri = volumeToRAGroupMap.get(volumeNativeGuid);
            _log.debug("Remote RA Group URI {}", remoteRAGroupUri);
            RemoteDirectorGroup remoteGroup = _dbClient.queryObject(RemoteDirectorGroup.class, remoteRAGroupUri);
            if (null == remoteGroup) {
                _log.info("Remote Group not found {}", remoteRAGroupUri);
                return;
            }
            String copyMode = null;
            int numberOfTargets = 0;

            if (it != null) {
                while (it.hasNext()) {
                    CIMInstance storageSynchronized = it.next();
                    CIMObjectPath storageSynchronizedPath = storageSynchronized.getObjectPath();
                    CIMObjectPath sourcePath = (CIMObjectPath) storageSynchronizedPath.getKey(
                            Constants._SystemElement).getValue();
                    CIMObjectPath destPath = (CIMObjectPath) storageSynchronizedPath.getKey(
                            Constants._SyncedElement).getValue();
                    String sourceNativeGuid = createKeyfromPath(sourcePath);
                    String targetNativeGuid = createKeyfromPath(destPath);
                    _log.info("Source : {} , target : {}", sourceNativeGuid, targetNativeGuid);
                    if (!findVolumesArefromSameArray(sourceNativeGuid, targetNativeGuid)) {
                        numberOfTargets++;
                        copyMode = storageSynchronized.getPropertyValue(MODE).toString();
                        _log.info("RDF Group {} detected with Copy Mode {}", remoteGroup.getNativeGuid(), copyMode);
                    }
                }
            }

            if (numberOfTargets > 1) {
                _log.info("RA Group {} is associated with Cascaded SRDF configuration, hence copyMode will not be updated.",
                        remoteGroup.getNativeGuid());
                remoteGroup.setSupported(false);
			} else {
				// set copy Mode on Remote Group.
				// get Volume-->RA Group Mapping
				// Changing the copy mode if its a supported change or if it was Sync to Async or
				// other way round change that would have been done at the array. This usecase
				// comes into play when customer changes the mode to interchanges the Sync and
				// Async mode on the basis of various parameter one being bandwidth.
				remoteGroup.setSupported(true);
				if (checkForCopyModeUpdate(remoteGroup.getSupportedCopyMode(),
						SupportedCopyModes.getCopyMode(copyMode))) {
					// in general, this property value can't be null, but in customer case we are
					// seeing this, hence added this check
					_log.info("RDF Group {} detected with set Copy Mode : {}; Copy Mode from array: {}",
							remoteGroup.getLabel(), remoteGroup.getSupportedCopyMode(),
							SupportedCopyModes.getCopyMode(copyMode));
					if (null == copyMode) {
						remoteGroup.setSupportedCopyMode(SupportedCopyModes.UNKNOWN.toString());
					} else {
						remoteGroup.setSupportedCopyMode(SupportedCopyModes.getCopyMode(copyMode));
					}
				}
				_log.debug("Remote Group Copy Mode: {}", remoteGroup.getSupportedCopyMode());
			}
            _dbClient.persistObject(remoteGroup);
        } catch (Exception e) {
            _log.error("Copy Mode Discovery failed for remote Groups ", e);
        }
    }

    private String getCopyState(String copyState) {
        if (CONSISTENT_ID.equalsIgnoreCase(copyState)) {
            return CopyStates.CONSISTENT.toString();
        }
        return CopyStates.IN_CONSISTENT.toString();
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
