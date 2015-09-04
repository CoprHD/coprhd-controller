/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMObjectPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

public class StorageVolumeCapabilitiesProcessor extends StorageProcessor {
    private static final String TIER_POLICY_RULE = "TierPolicyRule";
    private static final String DEVICE_MASKING_GROUP = "DeviceMaskingGroup";
    private Logger _logger = LoggerFactory
            .getLogger(StorageVolumeCapabilitiesProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CIMObjectPath storageVolumePath = getObjectPathfromCIMArgument(_args);
            String nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(storageVolumePath);
            UnManagedVolume preExistingVolume = checkUnManagedVolumeExistsInDB(nativeGuid,
                    _dbClient);
            if (null == preExistingVolume) {
                _logger.debug("Volume Info Object not found :" + nativeGuid);
                return;
            }

            boolean changed = false;
            // get VolumeInfo Object and inject Fast Policy Name.
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            while (it.hasNext()) {
                CIMObjectPath capabilitiesPath = it.next();
                if (capabilitiesPath.toString().contains(TIER_POLICY_RULE)) {

                    injectIntoVolumeInformationContainer(preExistingVolume,
                            POLICYRULENAME, capabilitiesPath);
                    preExistingVolume.putVolumeCharacterstics(
                            SupportedVolumeCharacterstics.IS_AUTO_TIERING_ENABLED
                                    .toString(), "true");
                    changed = true;
                    // inject into Volume Info.
                } else if (capabilitiesPath.toString().contains(DEVICE_MASKING_GROUP)) {
                    addPath(keyMap, Constants.MASKING_GROUPS, capabilitiesPath);
                    @SuppressWarnings("unchecked")
                    Map<String, CIMObjectPath> volumeToStorageGroupMapping = (Map<String, CIMObjectPath>) keyMap
                            .get(Constants.VOLUME_STORAGE_GROUP_MAPPING);
                    volumeToStorageGroupMapping.put(
                            capabilitiesPath.getKey(Constants.INSTANCEID).getValue()
                                    .toString(), storageVolumePath);
                }
            }
            if (changed)
                _dbClient.persistObject(preExistingVolume);
        } catch (Exception e) {
            _logger.error("Processsing Pre Existing volume Capabilities failed", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
