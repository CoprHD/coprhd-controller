/**
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
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.AutoTieringPolicyProcessorHelper.getAutoTieringPolicyByNameFromDB;

/**
 * This processor responsibility is to insert policy Objects into DB.
 * 
 */
public class PersistFASTPolicyProcessor extends AbstractFASTPolicyProcessor {
    private DbClient _dbClient;
    private Logger _logger = LoggerFactory.getLogger(PersistFASTPolicyProcessor.class);

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            List<AutoTieringPolicy> fastPolicies = new ArrayList<AutoTieringPolicy>();
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> mapping = (Map<String, Set<String>>) keyMap
                    .get(Constants.POLICY_TO_POOLS_MAPPING);
            for (String key : mapping.keySet()) {
                AutoTieringPolicy policy = getAutoTieringPolicyByNameFromDB(key, _dbClient);
                Set<String> pools = mapping.get(key);
                policy.addPools(pools);
                fastPolicies.add(policy);
            }
            _dbClient.persistObject(fastPolicies);
        } catch (Exception e) {
            _logger.error("Adding Pools to POlicy Objects and Persisting failed :", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
    }
}
