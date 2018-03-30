/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.google.common.base.Strings;

/**
 * This processor will be run directly and should be called after the StoragePoolSettingProcessor. The processing here is to look
 * at all the SLO Names that were collected by the StoragePoolSettingProcessor. This should be a complete list of the SLOs that
 * are known to the array. We can then evaluate whether any of them no longer exist by comparing to what is currently in the
 * database.
 */
public class VmaxSLOBookkeepingProcessor extends Processor {
    private Logger log = LoggerFactory.getLogger(VmaxSLOBookkeepingProcessor.class);
    private static String DIAMONDSLO = "Diamond";
    private static Long MAX_BATCH_SIZE = 100L;
    
    
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
        // Get all policies existing on storage system!!
        List<AutoTieringPolicy> systemDbPolicies = DiscoveryUtils.getAllVMAXSloPolicies(dbClient, storageSystem);
        // Identify policies to be deleted from ViPR!!
        List<AutoTieringPolicy> policiesToUpdate = getPoliciesToBeDeleted(dbClient, policyNames, storageSystem);
        
        if (!policiesToUpdate.isEmpty()) {
            for (AutoTieringPolicy policy : policiesToUpdate) {
                // VMAX3 AFA Policies have WL along with SLO "Diamond"
                // VMAX3 Elm Policies have no WL
                // All Diamond + WL in Cypress are equal to Diamond in Elm
                // When array gets upgraded from Cypress to Elm, 
                // old policies with Diamond + WL will be deleted from ViPR.
                // Existing resources (volumes and mirrors) are with old SLO policy must be 
                // mapped to corresponding new policy in Elm
                if(storageSystem.isV3ElmCodeOrMore()) {
                    log.info(String.format("SLO %s no longer exists on array %s, finding corresponding policy...", policy.getPolicyName(),
                            storageSystem.getNativeGuid()));
                    // Find Elm policy w.r.t old policy
                    AutoTieringPolicy elmPolicy = getElmPolicyForOldPolicy(systemDbPolicies, policy);
                    // Find any Volume or Mirror having reference to old policy
                    if(elmPolicy != null) {
                        // Update the Volumes which are referenced to old policy with new policy
                        modifyVolumePolicyReference(dbClient, policy, elmPolicy);
                        // Update the BlockMirrors which are referenced to old policy with new policy
                        modifyBlockMirrorPolicyReference(dbClient, policy, elmPolicy);

                    }
                }

                log.info(String.format("SLO %s no longer exists on array %s, marking associated AutoTieringPolicy %s inactive", policy.getPolicyName(),
                        storageSystem.getNativeGuid(), policy));
                policy.setPolicyEnabled(false);
                policy.getPools().clear();
                policy.setInactive(true);
                dbClient.updateObject(policy);
            }
        }
    }
    
    /**
     * This function gets the all existing AutoTiering policies from ViPR and
     * compares with Policies which are discovered in current discovery cycle
     * 
     * Return list of policies which are not present on array between last discovery cycles.
     * 
     * @param dbClient
     * @param policyNames
     * @param storageSystem
     * @return
     * @throws IOException
     */
    private List<AutoTieringPolicy> getPoliciesToBeDeleted(DbClient dbClient, Set<String> policyNames, StorageSystem storageSystem) throws IOException {
        
        List<AutoTieringPolicy> policiesToDelete = new ArrayList<AutoTieringPolicy>();
        List<AutoTieringPolicy> policies = DiscoveryUtils.getAllVMAXSloPolicies(dbClient, storageSystem); 
        for (AutoTieringPolicy policyObject : policies) {
            String policyName = policyObject.getPolicyName();
            if (!policyNames.contains(policyName)) {
                log.info(String.format("SLO %s no longer exists on array %s", policyName, storageSystem.getNativeGuid()));
                policiesToDelete.add(policyObject);
            } 
        }
        return policiesToDelete; 
    }
    /**
     * Gets corresponding new Elm policy for a given Cypress Diamond SLO policy
     * 
     * @param systemDbPolicies
     * @param oldPolicy
     * @return
     */
    private AutoTieringPolicy getElmPolicyForOldPolicy(List<AutoTieringPolicy> systemDbPolicies, AutoTieringPolicy oldPolicy) {
        for (AutoTieringPolicy policy: systemDbPolicies) {
            // This getting removed also present in DB, 
            // hence make sure it does not return old policy
            if(oldPolicy.getId().equals(policy.getId())){
                continue;
            }
            // Get the new policy only for Diamond SLO
            if(oldPolicy.getVmaxSLO() == null || !oldPolicy.getVmaxSLO().equalsIgnoreCase(DIAMONDSLO)){
                continue;
            }
            
            // Get the new policy only for Diamond SLO
            if(policy.getVmaxSLO() == null || !policy.getVmaxSLO().equalsIgnoreCase(DIAMONDSLO)){
                continue;
            }
            // New SLO policy should be enabled
            if (!policy.getPolicyEnabled()) {
                continue;
            }
            
            // The new policy should have all pools of existing policy!!
            if(policy.getPools() != null && policy.getPools().containsAll(oldPolicy.getPools())) {
                log.info(String.format("SLO policy %s to be replaced with new SLO policy %s", 
                        oldPolicy.getPolicyName(), policy.getPolicyName()));
                return policy;
            }
        }
        return null;
    }
    
    /**
     * Existing Volumes with old SLO policy must be mapped to corresponding new policy
     * 
     * @param dbClient
     * @param oldPolicy
     * @param newPolicy
     */
    private void modifyVolumePolicyReference(DbClient dbClient, AutoTieringPolicy oldPolicy, AutoTieringPolicy newPolicy) {
        // Look for volumes through pools as there is no relation index between Policy and Volume
        if (oldPolicy.getPools() != null) {
            for (String pool : oldPolicy.getPools()) {
                URIQueryResultList volumeList = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getStoragePoolVolumeConstraint(URI.create(pool)), volumeList);
                Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                        volumeList, true);
                List<Volume> modifiedVolumes = new ArrayList<Volume>();
                while (volumeIterator.hasNext()) {
                    Volume vol = volumeIterator.next();
                    if(vol != null && !vol.getInactive() ){
                        if(vol.getAutoTieringPolicyUri() != null &&  vol.getAutoTieringPolicyUri().equals(oldPolicy.getId())){
                            log.debug(String.format("Changing volume %s auto tiering policy from %s to %s", vol.getLabel(), 
                                    vol.getAutoTieringPolicyUri(), newPolicy.getId())); 
                            vol.setAutoTieringPolicyUri(newPolicy.getId());
                            modifiedVolumes.add(vol);
                        }
                        modifiedVolumes.add(vol);
                    }
                }
                if(!modifiedVolumes.isEmpty()) {
                    dbClient.updateObject(modifiedVolumes);
                }
            }
        }
    }
    
    /**
     * Existing BlockMirrors with old SLO policy must be mapped to corresponding new policy
     * 
     * @param dbClient
     * @param oldPolicy
     * @param newPolicy
     */
    private void modifyBlockMirrorPolicyReference(DbClient dbClient, AutoTieringPolicy oldPolicy, AutoTieringPolicy newPolicy) {
        // Look for volumes through pools as there is no relation index between Policy and Volume
        if (oldPolicy.getPools() != null) {
            for (String pool : oldPolicy.getPools()) {
                URIQueryResultList mirrorsList = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getStoragePoolBlockMirrorConstraint(URI.create(pool)), mirrorsList);
                Iterator<BlockMirror> mirrorIterator = dbClient.queryIterativeObjects(BlockMirror.class,
                        mirrorsList, true);
                List<BlockMirror> modifiedMirrors = new ArrayList<BlockMirror>();
                while (mirrorIterator.hasNext()) {
                    BlockMirror mirror = mirrorIterator.next();
                    if(mirror != null && !mirror.getInactive()){
                        if(mirror.getAutoTieringPolicyUri() != null &&  mirror.getAutoTieringPolicyUri().equals(oldPolicy.getId())){
                            log.debug(String.format("Changing BlockMirror %s auto tiering policy from %s to %s", mirror.getLabel(), 
                                    mirror.getAutoTieringPolicyUri(), newPolicy.getId())); 
                            mirror.setAutoTieringPolicyUri(newPolicy.getId());
                            modifiedMirrors.add(mirror);
                        }
                    }
                }
                if(!modifiedMirrors.isEmpty()) {
                    dbClient.updateObject(modifiedMirrors);
                }
            }
        }
    }
    
}
