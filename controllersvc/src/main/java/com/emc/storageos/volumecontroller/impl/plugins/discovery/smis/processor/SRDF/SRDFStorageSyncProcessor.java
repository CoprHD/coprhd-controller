/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.SRDF;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SRDFStorageSyncProcessor extends StorageProcessor {
    private static final Logger _log = LoggerFactory.getLogger(SRDFStorageSyncProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        CloseableIterator<CIMObjectPath> synchronizedInstancePaths = null;
        EnumerateResponse<CIMObjectPath> synchronizedInstancePathChunks = null;
        WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);

        try {
            synchronizedInstancePathChunks = (EnumerateResponse<CIMObjectPath>) resultObj;
            synchronizedInstancePaths = synchronizedInstancePathChunks.getResponses();

            processStorageSynchronizedPaths(operation, synchronizedInstancePaths, resultObj, keyMap);

            while (!synchronizedInstancePathChunks.isEnd()) {
                synchronizedInstancePathChunks = client.getInstancePaths(Constants.SYNC_PATH,
                        synchronizedInstancePathChunks.getContext(), Constants.SYNC_BATCH_SIZE);
                processStorageSynchronizedPaths(operation, synchronizedInstancePathChunks.getResponses(), resultObj,
                        keyMap);
            }
        } //
        catch (Exception e) {
            _log.error("Processing Storage Synchronized Realtions for SRDF failed : ", e);
        } finally {
            if (null != synchronizedInstancePaths) {
                synchronizedInstancePaths.close();
            }
            if (null != synchronizedInstancePathChunks) {
                try {
                    client.closeEnumeration(Constants.SYNC_PATH, synchronizedInstancePathChunks.getContext());
                } catch (Exception e) {
                    _log.warn("Exception occurred while closing enumeration", e);
                }
            }
        }

        resultObj = null;
    }

    private void processStorageSynchronizedPaths(Operation operation, Iterator<CIMObjectPath> it, Object resultObj,
            Map<String, Object> keyMap) {
        while (it.hasNext()) {
            try {
                DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
                CIMObjectPath volumePath = it.next();

                CIMObjectPath sourcePath = (CIMObjectPath) volumePath.getKey(Constants._SystemElement).getValue();
                CIMObjectPath destPath = (CIMObjectPath) volumePath.getKey(Constants._SyncedElement).getValue();
                CIMProperty<?> prop = sourcePath.getKey(Constants._SystemName);
                String[] serialNumber_split = prop.getValue().toString().split(Constants.PATH_DELIMITER_REGEX);
                // skip other than the requested Storage System

                if (serialNumber_split[1].equalsIgnoreCase((String) keyMap.get(Constants._serialID))) {
                    // If mirror
                    String sourceVolumeNativeGuid = getVolumeNativeGuid(sourcePath);
                    Volume sourceVolume = checkStorageVolumeExistsInDB(sourceVolumeNativeGuid, dbClient);
                    if (null == sourceVolume || !isSRDFProtectedVolume(sourceVolume)) {
                        continue;
                    }
                    String targetVolumeNativeGuid = getVolumeNativeGuid(destPath);
                    Volume targetVolume = checkStorageVolumeExistsInDB(targetVolumeNativeGuid, dbClient);
                    if (null == targetVolume || !isSRDFProtectedVolume(targetVolume)) {
                        continue;
                    }
                    if (PersonalityTypes.SOURCE.toString().equalsIgnoreCase(sourceVolume.getPersonality())) {

                        if (null == sourceVolume.getSrdfTargets()
                                || !sourceVolume.getSrdfTargets().contains(targetVolume.getId().toString())) {
                            _log.info("target volume {} is not part of ViPR managed targtes for given source {}",
                                    targetVolumeNativeGuid, sourceVolumeNativeGuid);
                            continue;
                        }
                    } else if (PersonalityTypes.SOURCE.toString().equalsIgnoreCase(targetVolume.getPersonality())) {

                        if (null == targetVolume.getSrdfTargets()
                                || !targetVolume.getSrdfTargets().contains(sourceVolume.getId().toString())) {
                            _log.info("target volume {} is not part of ViPR managed targtes for given source {}",
                                    sourceVolumeNativeGuid, targetVolumeNativeGuid);
                            continue;
                        }
                    }
                    addPath(keyMap, operation.getResult(), volumePath);
                }
            } catch (Exception e) {
                _log.error("Prerequiste Step for getting srdf storage synchronized relations failed :", e);
            }
        }
    }

    private boolean isSRDFProtectedVolume(Volume volume) {
        return (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent()) || volume.getSrdfTargets() != null);
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
    }
}
