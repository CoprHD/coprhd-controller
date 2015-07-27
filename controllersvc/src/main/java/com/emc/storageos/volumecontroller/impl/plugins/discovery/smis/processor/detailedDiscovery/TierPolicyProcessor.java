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
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.AbstractFASTPolicyProcessor;

/**
 * Processor used in finding out associated Auto Tiering Policy Name with PreExisting Volume.
 * 
 */
public class TierPolicyProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory.getLogger(TierPolicyProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CIMObjectPath storageGroupPath = getObjectPathfromCIMArgument(_args);
            @SuppressWarnings("unchecked")
            Map<String, CIMObjectPath> volumeToStorageGroupMapping = (Map<String, CIMObjectPath>) keyMap
                    .get(Constants.VOLUME_STORAGE_GROUP_MAPPING);
            CIMObjectPath volumePath = volumeToStorageGroupMapping.get(storageGroupPath
                    .getKey(Constants.INSTANCEID).getValue().toString());
            String nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
            UnManagedVolume preExistingVolume = checkUnManagedVolumeExistsInDB(
                    nativeGuid, _dbClient);
            if (null == preExistingVolume)
                return;
            // get VolumeInfo Object and inject Fast Policy Name.
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            while (it.hasNext()) {
                CIMObjectPath policyPath = it.next();
                injectIntoVolumeInformationContainer(preExistingVolume,
                        Constants.POLICYRULENAME, policyPath);
                preExistingVolume.putVolumeCharacterstics(
                        SupportedVolumeCharacterstics.IS_AUTO_TIERING_ENABLED.toString(),
                        "true");
            }
            _dbClient.persistObject(preExistingVolume);
        } catch (Exception e) {
            _logger.error("Processing Tier Policy in Pre Existing Volume  failed", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
