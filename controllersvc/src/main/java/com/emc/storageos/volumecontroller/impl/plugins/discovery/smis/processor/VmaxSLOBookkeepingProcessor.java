/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.google.common.base.Strings;

/**
 * This processor will be run directly and should be called after the StoragePoolSettingProcessor. The processing here is to look
 * at all the SLO Names that were collected by the StoragePoolSettingProcessor. This should be a complete list of the SLOs that
 * are known to the array. We can then evaluate whether any of them no longer exist by comparing to what is currently in the
 * database.
 */
public class VmaxSLOBookkeepingProcessor extends Processor {
    private Logger log = LoggerFactory.getLogger(VmaxSLOBookkeepingProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
        Set<String> policyNames = (Set<String>) keyMap.get(Constants.SLO_NAMES);
        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, profile.getSystemId());
        boolean isVmax3 = storageSystem.checkIfVmax3();
        if (isVmax3 && policyNames != null && !policyNames.isEmpty()) {
            try {
                performPolicyBookKeeping(dbClient, policyNames, storageSystem);
            } catch (IOException e) {
                log.error("Exception caught while trying to run bookkeeping on VMAX3 SLO AutoTieringPolicies", e);
            }
        }
    }

    /**
     * if the policy had been removed from the Array, the rediscovery cycle should set the fast Policy to inactive.
     *
     * @param dbClient [in] - Database client
     * @param policyNames [in] - List SLO nativeGUIDs
     * @param storageSystem [in] - StorageSystem object representing the array being discovered
     * @throws java.io.IOException
     */
    private void performPolicyBookKeeping(DbClient dbClient, Set<String> policyNames, StorageSystem storageSystem) throws IOException {
        log.debug(String.format("SLO policyNames found by discovery for array %s:%n%s", storageSystem.getNativeGuid(),
                Joiner.on(',').join(policyNames)));
        List<AutoTieringPolicy> policiesToUpdate = new ArrayList<>();
        URIQueryResultList policiesInDB = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceFASTPolicyConstraint(storageSystem.getId()), policiesInDB);
        for (URI policy : policiesInDB) {
            AutoTieringPolicy policyObject = dbClient.queryObject(AutoTieringPolicy.class, policy);
            // Process only SLO based AutoTieringPolicies here.
            if (policyObject == null || Strings.isNullOrEmpty(policyObject.getVmaxSLO())) {
                continue;
            }
            String policyName = policyObject.getPolicyName();
            if (!policyNames.contains(policyName)) {
                log.info(String.format("SLO %s no longer exists on array %s, marking associated AutoTieringPolicy %s inactive", policyName,
                        storageSystem.getNativeGuid(), policy));
                policyObject.setPolicyEnabled(false);
                policyObject.getPools().clear();
                policyObject.setInactive(true);
                policiesToUpdate.add(policyObject);
            }
        }
        if (!policiesToUpdate.isEmpty()) {
            dbClient.updateAndReindexObject(policiesToUpdate);
        }
    }
}
