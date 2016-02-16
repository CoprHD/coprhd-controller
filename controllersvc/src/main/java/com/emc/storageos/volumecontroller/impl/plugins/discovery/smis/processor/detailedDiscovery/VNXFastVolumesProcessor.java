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
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

/**
 * This processor gets invoked only for VNX unManaged volume discoveries.
 * It populates the fast policy information for volumes.
 * Both Auto_tier and Start_High_then_Auto are represented as Auto_Tier in Provider.
 * Hence, it stores the objectPaths of Auto_tier alone. These object paths will be used to get
 * VolumeSettings (next SMI-S operation), from which the exact policy is being found.
 */
public class VNXFastVolumesProcessor extends StorageProcessor {

    List<UnManagedVolume> _unManagedVolumesUpdate = null;
    private Logger _logger = LoggerFactory.getLogger(VNXFastVolumesProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;

    private PartitionManager _partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        CloseableIterator<CIMObjectPath> volumeInstances = null;
        try {
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            _unManagedVolumesUpdate = new ArrayList<UnManagedVolume>();
            @SuppressWarnings("unchecked")
            EnumerateResponse<CIMObjectPath> volumeInstanceChunks = (EnumerateResponse<CIMObjectPath>) resultObj;
            volumeInstances = volumeInstanceChunks.getResponses();
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CIMObjectPath tierPolicypath = getObjectPathfromCIMArgument(_args);
            processVolumes(volumeInstances, tierPolicypath, keyMap, operation);
            while (!volumeInstanceChunks.isEnd()) {
                _logger.info("Processing Next Volume Chunk of size {}", BATCH_SIZE);
                volumeInstanceChunks = client.getInstancePaths(tierPolicypath,
                        volumeInstanceChunks.getContext(), new UnsignedInteger32(BATCH_SIZE));
                processVolumes(volumeInstanceChunks.getResponses(), tierPolicypath, keyMap, operation);
            }
            if (!_unManagedVolumesUpdate.isEmpty()) {
                _partitionManager.updateInBatches(_unManagedVolumesUpdate,
                        getPartitionSize(keyMap), _dbClient, "VOLUME");
                _unManagedVolumesUpdate.clear();
            }
        } catch (Exception e) {
            _logger.error("Discovering Tier Policies for vnx volumes failed", e);
        } finally {
            volumeInstances.close();
        }

    }

    private void processVolumes(Iterator<CIMObjectPath> it, CIMObjectPath tierPolicyPath,
            Map<String, Object> keyMap, Operation operation) {

        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());

        while (it.hasNext()) {
            CIMObjectPath volumePath = null;
            try {
                volumePath = it.next();
                if (tierPolicyPath.toString().contains(AutoTieringPolicy.VnxFastPolicy.DEFAULT_AUTOTIER.toString())) {
                    _logger.debug("Adding Auto Tier Policy Rule ");
                    addPath(keyMap, operation.getResult(),
                            volumePath);
                    continue;
                }
                String volumeNativeGuid = getVolumeNativeGuid(volumePath);
                Volume volume = checkStorageVolumeExistsInDB(volumeNativeGuid, _dbClient);
                if (null != volume) {
                    _logger.debug("Skipping discovery, as this Volume {} is already being managed by ViPR.",
                            volumeNativeGuid);
                    continue;
                }

                String unManagedVolumeNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
                UnManagedVolume unManagedVolume = checkUnManagedVolumeExistsInDB(
                        unManagedVolumeNativeGuid, _dbClient);
                if (null != unManagedVolume) {
                    String policyName = getCIMPropertyValue(tierPolicyPath, Constants.POLICYRULENAME);
                    _logger.info("Adding {} Policy Rule to UnManaged Volume {}", policyName, unManagedVolumeNativeGuid);
                    injectIntoVolumeInformationContainer(unManagedVolume,
                            Constants.POLICYRULENAME, tierPolicyPath);
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
                _logger.error("Processing UnManaged Storage Volume {} ",
                        volumePath, ex);
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        _args = inputArgs;
    }

}
