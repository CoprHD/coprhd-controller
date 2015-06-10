/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * Responsible for removing Tiers which had been deleted from Array
 * Pool--> Tiers and policy-->tiers had been updated already in previous operations.
 * Hence, we can blindly delete StorageTiers.
 */
public class TierBookKeepingProcessor extends AbstractFASTPolicyProcessor {
    private Logger _logger = LoggerFactory.getLogger(TierBookKeepingProcessor.class);
    private DbClient _dbClient;
    Set<String> _tierNativeGuids = new HashSet<String>();

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            String serialID = (String) keyMap.get(Constants._serialID);
            while (it.hasNext()) {
                CIMInstance tierInstance = it.next();
                if (tierInstance.getObjectPath().toString().contains(serialID))
                    _tierNativeGuids.add(getTierNativeGuidFromTierInstance(tierInstance));
            }
            performStorageTierBookKeeping(profile.getSystemId());
        } catch (Exception e) {
            _logger.info("BookKeeping Storage Tiers failed", e);
        }
    }

    /**
     * perform Storage Tier BookKeeping
     * 
     * @throws IOException
     */
    private void performStorageTierBookKeeping(URI storageSystemUri) throws IOException {
        List<URI> storageTierUris = _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStorageTierConstraint(storageSystemUri));
        List<StorageTier> storageTiers = _dbClient.queryObject(StorageTier.class,
                storageTierUris);
        for (StorageTier tier : storageTiers) {
            if (!_tierNativeGuids.contains(tier.getNativeGuid())) {
                tier.setInactive(true);
                _dbClient.persistObject(tier);
            }
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
