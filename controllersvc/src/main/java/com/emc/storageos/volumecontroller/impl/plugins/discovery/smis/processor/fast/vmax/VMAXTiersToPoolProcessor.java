/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vmax;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.AbstractFASTPolicyProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.AutoTieringPolicyProcessorHelper.getAutoTieringPolicyByNameFromDB;

/** refer VMAXPolicyToTiersProcessor before looking into this comments
 * Goal:
 * Get VMAX Storage Pools associated with each Tier.
 * Mapping had been already constructed between TierID --> Policy in VMAXPolicyToTiersProcessor
 * For each Tier discovered, get its list of Pools, and using the mapping information above,
 * build the relationship Policy--->Pools.
 * Get Fast Policy from DB, and update the Pools.
 */
public class VMAXTiersToPoolProcessor extends AbstractFASTPolicyProcessor {
    private Logger _logger = LoggerFactory.getLogger(VMAXTiersToPoolProcessor.class);
    
    private DbClient _dbClient;
    List<Object> _args;
    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            // value set at runtime already
            Object[] arguments = (Object[]) _args.get(0);
            CIMObjectPath tierPath = (CIMObjectPath) arguments[0];
            String tierID = (String) tierPath.getKey(
                    Constants.INSTANCEID).getValue();
            // Mapping had been already constructed between TierID --> Policy 
            List<CIMObjectPath> policyPaths = (List<CIMObjectPath>) keyMap.get(tierID);
            //add Policy to Tier
            addFastPolicyToTier(policyPaths,tierID);
            //add Pools to Policy
            addStoragePoolstoPolicy(policyPaths, it, _dbClient, keyMap, tierID);
        } catch (Exception e) {
            _logger.error("Tiers to Pool Processing failed:", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void addFastPolicyToTier(List<CIMObjectPath> policyPaths, String tierID)
            throws IOException {
        Set<String> policyUris = new StringSet();
        //getting policy uris from DB
        for (CIMObjectPath path : policyPaths) {
            String policyID = getFASTPolicyID(path);
            AutoTieringPolicy policy = getAutoTieringPolicyByNameFromDB(policyID, _dbClient);
            if (null != policy) {
                policyUris.add(policy.getId().toString());
            }
        }
        StorageTier tierObject = checkStorageTierExistsInDB(getTierNativeGuidForVMax(tierID),
                _dbClient);
        if (null != tierObject) {
            if (null == tierObject.getAutoTieringPolicies()) {
                tierObject.setAutoTieringPolicies(new StringSet());
            }
            tierObject.getAutoTieringPolicies().replace(policyUris);
            _dbClient.updateAndReindexObject(tierObject);
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
