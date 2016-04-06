/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

public class SGsWithFastVolumesProcessor extends StorageProcessor {

    List<UnManagedVolume> _unManagedVolumesUpdate = null;
    private Logger _logger = LoggerFactory.getLogger(SGsWithFastVolumesProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;

    private PartitionManager _partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    /**
     * This processor gets invoked only for VMAX unManaged volume discoveries.
     * Volumes belonging to SG are processed , and using the mapping information Storage Groups-->Fast Policy,
     * we identify the volume's policy.
     */

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {

        CloseableIterator<CIMObjectPath> volumeInstances = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> policyToStorageGroupMapping = (Map<String, String>) keyMap
                    .get(Constants.POLICY_STORAGE_GROUP_MAPPING);
            CIMObjectPath storageGroupPath = getObjectPathfromCIMArgument(_args);
            String groupId = storageGroupPath.getKey(Constants.INSTANCEID).getValue().toString();

            String policyName = policyToStorageGroupMapping.get(groupId);
            _logger.info("Group {}  policy Name {}", groupId, policyName);
            if (null == policyName) {
                return;
            }
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            _unManagedVolumesUpdate = new ArrayList<UnManagedVolume>();
            @SuppressWarnings("unchecked")
            EnumerateResponse<CIMObjectPath> volumeInstanceChunks = (EnumerateResponse<CIMObjectPath>) resultObj;
            volumeInstances = volumeInstanceChunks.getResponses();
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            processVolumes(volumeInstances, policyName, keyMap, operation);
            while (!volumeInstanceChunks.isEnd()) {
                _logger.debug("Processing Next Volume Chunk of size {}", BATCH_SIZE);
                volumeInstanceChunks = client.getInstancePaths(storageGroupPath,
                        volumeInstanceChunks.getContext(), new UnsignedInteger32(BATCH_SIZE));
                processVolumes(volumeInstanceChunks.getResponses(), policyName, keyMap, operation);
            }
            if (!_unManagedVolumesUpdate.isEmpty()) {
                _partitionManager.updateInBatches(_unManagedVolumesUpdate,
                        getPartitionSize(keyMap), _dbClient, "VOLUME");
                _unManagedVolumesUpdate.clear();
            }
        } catch (Exception e) {
            _logger.error("Discovering Tier Policies for vmax volumes failed", e);
        } finally {
            if (volumeInstances != null) {
                volumeInstances.close();
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        _args = inputArgs;

    }

    private void processVolumes(Iterator<CIMObjectPath> it, String policyName,
            Map<String, Object> keyMap, Operation operation) {

        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());

        while (it.hasNext()) {
            CIMObjectPath volumePath = null;
            try {
                volumePath = it.next();

                String volumeNativeGuid = getVolumeNativeGuid(volumePath);

                _logger.debug("VolumeNativeGuid {}", volumeNativeGuid);
                Volume volume = checkStorageVolumeExistsInDB(volumeNativeGuid, _dbClient);
                if (null != volume) {
                    _logger.debug("Skipping discovery, as this Volume {} is already being managed by ViPR.",
                            volumeNativeGuid);
                    continue;
                }

                String unManagedVolumeNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
                _logger.debug("UnManagedVolumeNativeGuid {}", unManagedVolumeNativeGuid);
                UnManagedVolume unManagedVolume = checkUnManagedVolumeExistsInDB(
                        unManagedVolumeNativeGuid, _dbClient);
                if (null != unManagedVolume) {
                    _logger.info("Adding VMAX Policy Rule {}", policyName);
                    unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString(), policyName);
                    unManagedVolume.putVolumeCharacterstics(
                            SupportedVolumeCharacterstics.IS_AUTO_TIERING_ENABLED.toString(),
                            "true");

                    // StorageVolumeInfoProcessor updated supported_vpool_list based on its pool's presence in vPool
                    // Now, filter those vPools based on policy associated
                    DiscoveryUtils.filterSupportedVpoolsBasedOnTieringPolicy(unManagedVolume, policyName, system, _dbClient);

                    _unManagedVolumesUpdate.add(unManagedVolume);
                }

                if (_unManagedVolumesUpdate.size() > BATCH_SIZE) {
                    _partitionManager.updateInBatches(_unManagedVolumesUpdate,
                            getPartitionSize(keyMap), _dbClient, "VOLUME");
                    _unManagedVolumesUpdate.clear();
                }

            } catch (Exception ex) {
                _logger.error("Processing UnManaged Storage Volume {} failed",
                        volumePath, ex);
            }
        }
    }

}
