/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.SRDF;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.LinkStatus;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

public class SRDFLinkProcessor extends StorageProcessor {
    private static final Logger _log = LoggerFactory.getLogger(SRDFLinkProcessor.class);
    private static final String REMOTE_MIRROR = "6";

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            final CIMInstance instance = (CIMInstance) resultObj;
            CIMObjectPath volumePath = instance.getObjectPath();
            CIMObjectPath sourcePath = (CIMObjectPath) volumePath.getKey(
                    Constants._SystemElement).getValue();
            CIMObjectPath destPath = (CIMObjectPath) volumePath.getKey(
                    Constants._SyncedElement).getValue();
            String syncType = instance.getProperty(Constants._SyncType).getValue()
                    .toString();
            String copyState = instance.getProperty(Constants.COPY_STATE).getValue()
                    .toString();

            if (syncType.equalsIgnoreCase(REMOTE_MIRROR)) {
                String newStatus = instance.getProperty(Constants.COPY_STATE_DESC).getValue()
                        .toString();
                ;
                String sourceVolumeNativeGuid = getVolumeNativeGuid(sourcePath);
                Volume expectedSourceVolume = checkStorageVolumeExistsInDB(sourceVolumeNativeGuid, dbClient);
                String targetVolumeNativeGuid = getVolumeNativeGuid(destPath);
                Volume expectedTargetVolume = checkStorageVolumeExistsInDB(targetVolumeNativeGuid, dbClient);

                if (PersonalityTypes.TARGET.toString().equalsIgnoreCase(expectedSourceVolume.getPersonality()) &&
                        PersonalityTypes.SOURCE.toString().equalsIgnoreCase(expectedTargetVolume.getPersonality()) &&
                        !LinkStatus.SWAPPED.toString().equalsIgnoreCase(expectedSourceVolume.getLinkStatus())) {
                    // expected target Volume is acting as a source in ViPr and viceversa
                    StringSet srdfTargets = new StringSet();
                    srdfTargets.addAll(expectedTargetVolume.getSrdfTargets());
                    URI raGroupUri = expectedSourceVolume.getSrdfGroup();
                    String copyMode = expectedSourceVolume.getSrdfCopyMode();
                    NamedURI parent = expectedSourceVolume.getSrdfParent();

                    // targetVolumefromprovider is acting as a source in ViPr, change it back to target
                    expectedTargetVolume.setPersonality(PersonalityTypes.TARGET.toString());
                    expectedTargetVolume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
                    expectedTargetVolume.setSrdfParent(new NamedURI(expectedSourceVolume.getId(), parent.getName()));
                    expectedTargetVolume.setSrdfCopyMode(copyMode);
                    expectedTargetVolume.setSrdfGroup(raGroupUri);
                    expectedTargetVolume.getSrdfTargets().replace(new StringSet());
                    // SourceVolumefromprovider is acting as a target in ViPr, change it back to source
                    expectedSourceVolume.setPersonality(PersonalityTypes.SOURCE.toString());
                    expectedSourceVolume
                            .setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
                    expectedSourceVolume.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                    expectedSourceVolume.setSrdfGroup(NullColumnValueGetter.getNullURI());
                    expectedSourceVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                    srdfTargets.add(expectedTargetVolume.getId().toString());
                    srdfTargets.remove(expectedSourceVolume.getId().toString());
                    if (null == expectedSourceVolume.getSrdfTargets()) {
                        expectedSourceVolume.setSrdfTargets(new StringSet());
                    }
                    expectedSourceVolume.getSrdfTargets().replace(srdfTargets);
                }
                expectedSourceVolume.setLinkStatus(newStatus);
                expectedTargetVolume.setLinkStatus(newStatus);
                dbClient.persistObject(expectedSourceVolume);
                dbClient.persistObject(expectedTargetVolume);
                createOrUpdateRemoteReplicationPair(expectedSourceVolume, expectedTargetVolume, dbClient);
            }
        } //
        catch (Exception e) {
            _log.error("Validating SRDF Source and Target characteristics failed :", e);
        }
    }

    /**
     * @param expectedSourceVolume
     * @param expectedTargetVolume
     * @param dbClient
     */
    private void createOrUpdateRemoteReplicationPair(Volume expectedSourceVolume, Volume expectedTargetVolume, DbClient dbClient) {
        List<RemoteReplicationPair> rrSrcPairs = RemoteReplicationUtils.getRemoteReplicationPairsForSourceElement(expectedSourceVolume.getId(), dbClient);
        if (rrSrcPairs == null || rrSrcPairs.isEmpty()) {
            // check for the expected target as the source
            List<RemoteReplicationPair> rrTgtPairs = RemoteReplicationUtils.getRemoteReplicationPairsForSourceElement(expectedTargetVolume.getId(), dbClient);
            if (rrTgtPairs == null || rrTgtPairs.isEmpty()) {
                // no pair exists for source and target, create a new one
                RemoteReplicationUtils.createRemoteReplicationPairForSrdfPair(expectedSourceVolume.getId(), expectedTargetVolume.getId(), dbClient);
            } else {
                // TODO SBSDK we have a remote replication pair with source and target switched; need to modify this.
            }
        } else {
            // TODO SBSDK we have an existing remote replication pair; need to update it with current information
            //RemoteReplicationUtils.updateRemoteReplicationPairForSrdfPair(expectedSourceVolume.getId(), expectedTargetVolume.getId(), dbClient);
        }
        
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
    }
}
