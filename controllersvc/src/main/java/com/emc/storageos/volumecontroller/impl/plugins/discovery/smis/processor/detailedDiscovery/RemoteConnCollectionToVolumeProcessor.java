/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;

public class RemoteConnCollectionToVolumeProcessor extends StorageProcessor {
    private List<Object> args;
    private Logger _log = LoggerFactory.getLogger(RemoteConnCollectionToVolumeProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            @SuppressWarnings("unchecked")
            Map<String, RemoteMirrorObject> volumeToRAGroupMap = (Map<String, RemoteMirrorObject>) keyMap
                    .get(Constants.UN_VOLUME_RAGROUP_MAP);
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CIMObjectPath raGroupPath = getObjectPathfromCIMArgument(args);
            String ragGroupId = NativeGUIDGenerator.generateRAGroupNativeGuid(raGroupPath);
            _log.debug("RA Group Id :" + ragGroupId);
            RemoteDirectorGroup remoteGroup = getRAGroupUriFromDB(_dbClient, ragGroupId);
            if (null == remoteGroup) {
                _log.warn("RA Group {} not found", ragGroupId);
                return;
            }
            boolean is80Provider = keyMap.containsKey(Constants.IS_NEW_SMIS_PROVIDER)
                    && Boolean.valueOf(keyMap.get(Constants.IS_NEW_SMIS_PROVIDER).toString());
            RemoteDirectorGroup targetRemoteGroup = SRDFUtils.getAssociatedTargetRemoteDirectorGroup(_dbClient,
                    is80Provider, ragGroupId);
            while (it.hasNext()) {
                CIMObjectPath volumePath = it.next();
                RemoteMirrorObject rmObj = new RemoteMirrorObject();
                addPath(keyMap, operation.getResult(), volumePath);
                String unManagedVolumeNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
                // Changes to copy mode in RemoteDirectorGroup is updated in StorageSynchronizedProcessor
                rmObj.setCopyMode(remoteGroup.getSupportedCopyMode());
                rmObj.setSourceRaGroupUri(remoteGroup.getId());
                if (targetRemoteGroup != null) {
                    rmObj.setTargetRaGroupUri(targetRemoteGroup.getId());
                }
                if (!volumeToRAGroupMap.containsKey(unManagedVolumeNativeGuid)) {
                    volumeToRAGroupMap.put(unManagedVolumeNativeGuid, rmObj);
                }
            }
        } catch (Exception e) {
            _log.error("Updating Copy Mode for UnManaged Volumes failed", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
