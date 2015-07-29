/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

public class StorageSynchronizedProcessor extends StorageProcessor {

    private Logger _log = LoggerFactory.getLogger(StorageSynchronizedProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final Iterator<?> it = (Iterator<?>) resultObj;
        @SuppressWarnings("unchecked")
        Map<String, RemoteMirrorObject> volumeToRAGroupMap = (Map<String, RemoteMirrorObject>) keyMap.get(Constants.UN_VOLUME_RAGROUP_MAP);
        while (it.hasNext()) {
            try {
                final CIMInstance instance = (CIMInstance) it.next();
                CIMObjectPath volumePath = instance.getObjectPath();
                CIMObjectPath sourcePath = (CIMObjectPath) volumePath.getKey(
                        Constants._SystemElement).getValue();
                CIMObjectPath destPath = (CIMObjectPath) volumePath.getKey(
                        Constants._SyncedElement).getValue();
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
                }
            } catch (Exception e) {
                _log.error("Finding out Parent of a target Volume failed", e);
            }
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

}
