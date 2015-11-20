/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast;

import static com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.AutoTieringPolicyProcessorHelper.getAutoTieringPolicyByNameFromDB;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.google.common.base.Strings;

/**
 * Processor used in retrieving FAST Policies for both
 * VMAX and VNX.
 * Goal:
 * For a Discovered Storage System, get its associated FastPolicies
 * Only UserDefined Policies are considered for VMAX
 * Right now, due to limitations in SMI-S Model, using the predefined Global
 * Policies in VMAX to filter.
 * In case of VNX, only 4 default Policies are available.
 * 
 * VmaxfastPolciies will be added to vmaxPolicyList & vnxfastPolicies added to vnxPolicyList
 * This list is being used later to get Tiers and corresponding Pools for vmax
 * and to get Volumes and Pools for vnx.
 * 
 * Each policy will be associated with a default Bourne Created Device Group Name based on
 * Provisioning Type. All volumes created using this fast Policy Name ends up added into this
 * Bourne created Storage Group.
 * 
 * Once the policy objects are created, we generate the expected device Group Name for the
 * policy based on Provisioning Type.If Provisioning Type is thickly provisioned, then
 * Bourne Created Device Group would be PolicyName-thickDeviceGroup, if thin, then
 * Policy-thinDeviceGroup, if ProvisioningType is All, then PolicyName-thinandthickDeviceGroup
 * 
 * The above list of expectedDeviceGroupNames would be used later to identify whether there is a need
 * to create Bourne Created Storage Group for this Policy.
 */
public class FASTPolicyProcessor extends AbstractFASTPolicyProcessor {
    private Logger _logger = LoggerFactory.getLogger(FASTPolicyProcessor.class);

    private List<AutoTieringPolicy> _newFastPolicies;
    private List<AutoTieringPolicy> _updateFastPolicies;
    private DbClient _dbClient;

    public enum GlobalVMAXPolicies {
        GlobalThickDataMovementPolicy,
        GlobalThinDataMovementPolicy,
        GlobalWorkloadStatisticsCollectionPolicy;

        public static boolean contains(String policy) {
            try {
                GlobalVMAXPolicies.valueOf(policy);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _newFastPolicies = new ArrayList<AutoTieringPolicy>();
            _updateFastPolicies = new ArrayList<AutoTieringPolicy>();
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            URI storageSystemURI = profile.getSystemId();
            Set<String> policyNames = new HashSet<String>();

            boolean vnxStartHighThenAutoTierPolicyCreated = false;
            while (it.hasNext()) {
                CIMInstance policyObjectInstance = it.next();
                CIMObjectPath policyObjectPath = policyObjectInstance.getObjectPath();
                String systemName = policyObjectPath.getKey(Constants.SYSTEMNAME).getValue().toString();

                if (!systemName.contains((String) keyMap.get(Constants._serialID))) {
                    continue;
                }
                String[] array = systemName.split(Constants.PATH_DELIMITER_REGEX);
                String policyID = getFASTPolicyID(policyObjectPath);
                // Trim the policyID from "-+-" to "+" if necessary
                Boolean usingSMIS80 = (Boolean) keyMap.get(Constants.USING_SMIS80_DELIMITERS);
                if ((null != usingSMIS80) && (true == usingSMIS80)) {
                    policyID = policyID.replaceAll(Constants.SMIS_80_STYLE, Constants.SMIS_PLUS_REGEX);
                }
                AutoTieringPolicy policy = getAutoTieringPolicyByNameFromDB(policyID, _dbClient);
                String policyRuleName = policyObjectPath.getKey(Constants.POLICYRULENAME)
                        .getValue().toString();
                policyNames.add(policyRuleName);
                String policyEnabled = policyObjectInstance.getPropertyValue(
                        Constants.ENABLED).toString();
                String provisioningType = AutoTieringPolicy.ProvisioningType
                        .getType(policyObjectInstance.getPropertyValue(
                                Constants.PROVISIONING_TYPE).toString());

                /**
                 * Only user Defined Policies are considered for VMAX
                 * For VNX, only default policies are present, there is no concept of userDefined
                 */
                if (!Constants.SYMMETRIX.equalsIgnoreCase(array[0])
                        && !Constants.CLARIION.equalsIgnoreCase(array[0])) {
                    _logger.info("Unsupported FAST Policy :{}", policyID);
                    return;
                }
                String fastPolicyServiceConstant = getFASTPolicyServiceConstant(array[0], policyRuleName);

                if (null != fastPolicyServiceConstant) {
                    createFASTPolicy(policyID, policy, policyRuleName, storageSystemURI,
                            policyEnabled, provisioningType);
                    addPath(keyMap, fastPolicyServiceConstant, policyObjectPath);
                    keyMap.put(policyRuleName, policyObjectPath);
                    if (fastPolicyServiceConstant.equals(Constants.VMAXFASTPOLICIES)) {

                        addDeviceGroupNamesToSetUsedInVerifyingExistence(policyRuleName,
                                keyMap, provisioningType);
                        addDeviceGroupNamesToSetUsedInVerifyingFASTPolicyRelationShipExistence(policyRuleName, keyMap, provisioningType);

                    } else if (fastPolicyServiceConstant.equals(Constants.VNXFASTPOLICIES) && !vnxStartHighThenAutoTierPolicyCreated) {
                        /**
                         * NOTE: start_high_then_auto_tier policy will not be discovered, thus must
                         * create it for VNX in ViPR if not created already.
                         */
                        String startHighThenAutoTierPolicyName = Constants.START_HIGH_THEN_AUTO_TIER_POLICY_NAME;

                        policyNames.add(startHighThenAutoTierPolicyName);
                        String startHighThenAutTierPolicyId = getFASTPolicyID(systemName, startHighThenAutoTierPolicyName);

                        AutoTieringPolicy startHighThenAutTierPolicy = getAutoTieringPolicyByNameFromDB(startHighThenAutTierPolicyId,
                                _dbClient);
                        createFASTPolicy(
                                startHighThenAutTierPolicyId, startHighThenAutTierPolicy, startHighThenAutoTierPolicyName,
                                storageSystemURI, "1",
                                AutoTieringPolicy.ProvisioningType.All.name());

                        vnxStartHighThenAutoTierPolicyCreated = true;
                    }
                }

            }
            _dbClient.createObject(_newFastPolicies);
            _dbClient.persistObject(_updateFastPolicies);
            performPolicyBookKeeping(policyNames, storageSystemURI);
        } catch (Exception e) {
            _logger.error("FAST Policy Processing failed", e);
        }
    }

    /**
     * if the policy had been deleted from the Array, the rediscovery cycle should set the fast Policy to inactive.
     * 
     * @param policyNames
     * @param storageSystemURI
     * @throws IOException
     */
    private void performPolicyBookKeeping(Set<String> policyNames, URI storageSystemURI)
            throws IOException {
        List<URI> policiesInDB = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceFASTPolicyConstraint(storageSystemURI));
        for (URI policy : policiesInDB) {
            AutoTieringPolicy policyObject = _dbClient.queryObject(
                    AutoTieringPolicy.class, policy);

            String policyName = policyObject.getPolicyName();
            if (null == policyObject || Constants.START_HIGH_THEN_AUTO_TIER_POLICY_NAME.equals(policyName) ||
                    // If a VMAX3 SLO AutoTierPolicy, do not process here
                    !Strings.isNullOrEmpty(policyObject.getVmaxSLO())) {
                continue;
            }
            /**
             * Since START_HIGH_THEN_AUTO_TIER_POLICY is ViPR created policy, no need to clean up
             */
            if (!policyNames.contains(policyName)) {
                policyObject.setPolicyEnabled(false);
                if (policyObject.getPools() != null) {
                    policyObject.getPools().clear();
                } else {
                    _logger.info("Policy {} does not have pools", policyObject.getId());
                }
                policyObject.setInactive(true);
                _dbClient.updateAndReindexObject(policyObject);
            }

        }
    }

    private String getFASTPolicyServiceConstant(String arrayType, String policyRuleName) {
        // if arrayType is symmetrix
        if (Constants.CLARIION.equalsIgnoreCase(arrayType)) {
            return Constants.VNXFASTPOLICIES;
        }
        if (Constants.SYMMETRIX.equalsIgnoreCase(arrayType)
                && !GlobalVMAXPolicies.contains(policyRuleName)) {
            return Constants.VMAXFASTPOLICIES;
        }
        return null;

    }

    /**
     * List of Device Group Names is used to verify whether the Storage Group already exists in Provider
     * 
     * @param policyRuleName
     * @param keyMap
     */
    @SuppressWarnings("unchecked")
    private void addDeviceGroupNamesToSetUsedInVerifyingExistence(
            String policyRuleName, Map<String, Object> keyMap, String provisioningType) {
        List<String> deviceNamesExistence = (List<String>) keyMap
                .get(Constants.USED_IN_CHECKING_GROUPNAMES_EXISTENCE);
        if (AutoTieringPolicy.ProvisioningType.ThicklyProvisioned.toString().equalsIgnoreCase(
                provisioningType)) {
            deviceNamesExistence.add(policyRuleName + Constants.HYPHEN + Constants.THICKDEVICEGROUP);
        } else if (AutoTieringPolicy.ProvisioningType.ThinlyProvisioned.toString()
                .equalsIgnoreCase(provisioningType)) {
            deviceNamesExistence.add(policyRuleName + Constants.HYPHEN + Constants.THINDEVICEGROUP);
        } else if (AutoTieringPolicy.ProvisioningType.All.toString()
                .equalsIgnoreCase(provisioningType)) {
            deviceNamesExistence
                    .add(policyRuleName + Constants.HYPHEN + Constants.THINANDTHICKDEVICEGROUP);
        }
    }

    /**
     * List of Device Group Names is used to verify whether the Storage Group is associated with FAST Policy
     * already.
     * 
     * @param policyRuleName
     * @param keyMap
     */
    @SuppressWarnings("unchecked")
    private void addDeviceGroupNamesToSetUsedInVerifyingFASTPolicyRelationShipExistence(
            String policyRuleName, Map<String, Object> keyMap, String provisioningType) {
        List<String> deviceNamesPolicyRelationExistence = (List<String>) keyMap
                .get(Constants.USED_IN_CHECKING_GROUPNAMES_TO_FASTPOLICY);
        if (AutoTieringPolicy.ProvisioningType.ThicklyProvisioned.toString().equalsIgnoreCase(
                provisioningType)) {
            deviceNamesPolicyRelationExistence.add(policyRuleName + Constants.HYPHEN
                    + Constants.THICKDEVICEGROUP);
        } else if (AutoTieringPolicy.ProvisioningType.ThinlyProvisioned.toString()
                .equalsIgnoreCase(provisioningType)) {
            deviceNamesPolicyRelationExistence.add(policyRuleName + Constants.HYPHEN
                    + Constants.THINDEVICEGROUP);
        } else if (AutoTieringPolicy.ProvisioningType.All.toString()
                .equalsIgnoreCase(provisioningType)) {
            deviceNamesPolicyRelationExistence.add(policyRuleName + Constants.HYPHEN
                    + Constants.THINANDTHICKDEVICEGROUP);
        }
    }

    /**
     * create FAST Policy Object
     * s
     * 
     * @param policyName
     * @param policy
     * @throws IOException
     */
    private void createFASTPolicy(
            String policyID, AutoTieringPolicy policy, String policyRuleName, URI storageSystemURI, String policyEnabled,
            String provisioningType) throws IOException {
        boolean newPolicy = false;
        if (null == policy) {
            newPolicy = true;
            policy = new AutoTieringPolicy();
            policy.setId(URIUtil.createId(AutoTieringPolicy.class));
            policy.setStorageSystem(storageSystemURI);
            policy.setNativeGuid(policyID);
            policy.setSystemType(getDeviceType(storageSystemURI));
        }

        policy.setLabel(policyRuleName);
        policy.setPolicyName(policyRuleName);
        policy.setPolicyEnabled(policyEnabled.equalsIgnoreCase("1"));
        policy.setProvisioningType(provisioningType);
        if (newPolicy) {
            _newFastPolicies.add(policy);
        } else {
            _updateFastPolicies.add(policy);
        }
    }

    private String getDeviceType(URI storageSystemURI) throws IOException {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, storageSystemURI);
        if (null != system) {
            return system.getSystemType();
        }
        return null;
    }

    /**
     * Filter out Global Rules in VMAX, as LocalRule alone applies for userDefinedPolicies.
     * Not used right now
     * 
     * @param policyObjectInstance
     * @return boolean
     */
    private boolean isVMAXUserDefinedPolicy(CIMInstance policyObjectInstance) {
        String[] rulediscriminator = (String[]) policyObjectInstance
                .getPropertyValue(Constants.RULEDISCRIMINATOR);
        if (null == rulediscriminator) {
            return false;
        }
        for (String rule : rulediscriminator) {
            if (Constants.LOCALRULE.equalsIgnoreCase(rule)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
