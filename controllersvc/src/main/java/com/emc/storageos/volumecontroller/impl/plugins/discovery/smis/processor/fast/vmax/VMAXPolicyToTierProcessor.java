/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vmax;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.AbstractFASTPolicyProcessor;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.AutoTieringPolicyProcessorHelper.getAutoTieringPolicyByNameFromDB;

/**
 * refer FASTPolicyProcessor comments before looking into below
 * Goal:
 * For each Discovered VMax fast Policy, run associatorNames on
 * EMC_StorageTier, which returns the list of Storage Tiers available for
 * VMax associated with this Policy.
 * 
 * Using the returned Tier list,construct mapping between Tier-->Fast Policy
 * which will be used later to associate Policy--->Pools
 * 
 * The reason behind this mapping is, for VMax, we get pools through
 * Policy-->tiers-->Pools, as we don't have a TierModel right now, we need this.
 * 
 */
public class VMAXPolicyToTierProcessor extends AbstractFASTPolicyProcessor {
    private Logger _logger = LoggerFactory.getLogger(VMAXPolicyToTierProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;
    private List<StorageTier> _updateTierList;
    private List<StorageTier> _newTierList;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            _updateTierList = new ArrayList<StorageTier>();
            _newTierList = new ArrayList<StorageTier>();
            // value will be set already always
            Object[] arguments = (Object[]) _args.get(0);
            Set<String> tierNativeGuidsfromProvider = new HashSet<String>();
            CIMObjectPath vmaxFastPolicyRule = (CIMObjectPath) arguments[0];
            String vmaxPolicyId = getFASTPolicyID(vmaxFastPolicyRule);
            AutoTieringPolicy vmaxFastPolicy = getAutoTieringPolicyByNameFromDB(vmaxPolicyId,
                    _dbClient);
            // construct a Map (TierID--->PolicyRule), this is needed to construct
            // the relationship between Policy--->Pools
            while (it.hasNext()) {
                CIMInstance vmaxTierInstance = it.next();
                CIMObjectPath tierPath = vmaxTierInstance.getObjectPath();
                String tierID = tierPath.getKey(Constants.INSTANCEID).getValue()
                        .toString();
                //For 8.x -+- becomes +, internal DB format uses + only; for 4.6 remains as it is
                tierID = tierID.replaceAll(Constants.SMIS_80_STYLE, Constants.SMIS_PLUS_REGEX);
                if (keyMap.containsKey(tierID)) {
                    List<CIMObjectPath> policyPaths =  (List<CIMObjectPath>) keyMap.get(tierID);
                    policyPaths.add(vmaxFastPolicyRule);
                    
                } else {
                    addPath(keyMap, Constants.STORAGETIERS, tierPath);
                    List<CIMObjectPath> policyPaths = new ArrayList<CIMObjectPath>();
                    policyPaths.add(vmaxFastPolicyRule);
                    keyMap.put(tierID, policyPaths);
                }
                String tierNativeGuid = getTierNativeGuidForVMax(tierID);
                tierNativeGuidsfromProvider.add(tierNativeGuid);
                StorageTier tierObject = checkStorageTierExistsInDB(tierNativeGuid, _dbClient);
                String driveTechnologyIdentifier = vmaxTierInstance.getPropertyValue(
                        Constants.TECHNOLOGY).toString();
                String driveType = StorageTier.SupportedTiers.getTier(driveTechnologyIdentifier);
                createStorageTier(vmaxTierInstance, tierObject, tierNativeGuid,
                        vmaxFastPolicy.getId(), _newTierList, _updateTierList, driveType);
            }
            _dbClient.createObject(_newTierList);
            _dbClient.persistObject(_updateTierList);
            performStorageTierBookKeeping(tierNativeGuidsfromProvider,
                    vmaxFastPolicy.getId());
        } catch (Exception e) {
            _logger.error("Policy to Tier Processing failed :", e);
        }
    }
    /**
     * remove Tiers which had been deleted from Array
     * @param tierNativeGuidsfromProvider
     * @param id
     * @throws IOException
     */
    private void performStorageTierBookKeeping(
            Set<String> tierNativeGuidsfromProvider, URI id) throws IOException {
        List<URI> tierUrisAssociatedWithPolicy = _dbClient
                .queryByConstraint(AlternateIdConstraint.Factory
                        .getStorageTierFASTPolicyConstraint(id.toString()));
        _logger.info("Tiers {} associated with Policy {}",Joiner.on("\t").join(tierUrisAssociatedWithPolicy),id);
        List<StorageTier> existingTierObjectsInDB = _dbClient.queryObject(
                StorageTier.class, tierUrisAssociatedWithPolicy);
        for (StorageTier tier : existingTierObjectsInDB) {
            if (!tierNativeGuidsfromProvider.contains(tier.getNativeGuid())) {
                if (null != tier.getAutoTieringPolicies()) {
                     tier.getAutoTieringPolicies().clear();
                }
                _dbClient.updateAndReindexObject(tier);
            }
        }
    }
    

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
