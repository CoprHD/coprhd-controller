/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedProvisioningTypes;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.UnsignedInteger16;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class StorageConfigurationCapabilitiesProcessor extends Processor {
    private static final String SUPPORTED_ELEMENT_TYPES = "SupportedStorageElementTypes";
    private static final String EMC_UNRESTRICTED_THIN_STORAGE_VOLUME = "32777";
    private static final String EMC_UNRESTRICTED_THICK_STORAGE_VOLUME = "32778";
    private static final String THINLY_PROVISIONED_STORAGE_VOLUME = "5";
    private Logger _logger = LoggerFactory
            .getLogger(StorageConfigurationCapabilitiesProcessor.class);
    private DbClient _dbClient;
    private AccessProfile _profile = null;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        @SuppressWarnings("unchecked")
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
        _profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        try {
            StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                    _profile.getSystemId());
            Boolean usingSMIS80 = (Boolean) keyMap.get(Constants.USING_SMIS80_DELIMITERS);
            boolean isSMIS80 = (usingSMIS80 != null && usingSMIS80);
            while (it.hasNext()) {
                CIMInstance storageConfigurationInstance = it.next();
                if (Type.vnxblock.toString().equalsIgnoreCase(system.getSystemType())) {
                    updateStorageSystemCapabilityOnVolumeForVNX(storageConfigurationInstance, system);
                } else {
                    updateStorageSystemCapabilityOnVolume(storageConfigurationInstance, system);
                }
                system.setUsingSmis80(isSMIS80);
            }
            _dbClient.persistObject(system);
        } catch (Exception e) {
            _logger.error(
                    "Finding out Storage System Capability on Volume Creation failed :",
                    e);
        }
    }

    /**
     * 
     * @param storageConfigurationInstance
     * @param system
     * @throws Exception
     */
    private void updateStorageSystemCapabilityOnVolume(
            CIMInstance storageConfigurationInstance, StorageSystem system)
            throws Exception {
        UnsignedInteger16[] supportedElementTypeArr = (UnsignedInteger16[]) storageConfigurationInstance
                .getPropertyValue(SUPPORTED_ELEMENT_TYPES);
        String supportedElementTypes = Arrays.toString(supportedElementTypeArr);
        _logger.debug("Capability : {}", supportedElementTypes);
        // This logic looks very simple to me , compared to looping thro' unsignedint[] Array
        if (supportedElementTypes.contains(EMC_UNRESTRICTED_THIN_STORAGE_VOLUME)
                && supportedElementTypes.contains(EMC_UNRESTRICTED_THICK_STORAGE_VOLUME)) {
            system.setSupportedProvisioningType(SupportedProvisioningTypes.THIN_AND_THICK.toString());
        } else if (supportedElementTypes.contains(EMC_UNRESTRICTED_THIN_STORAGE_VOLUME)) {
            system.setSupportedProvisioningType(SupportedProvisioningTypes.THIN.toString());
        } else if (supportedElementTypes.contains(EMC_UNRESTRICTED_THICK_STORAGE_VOLUME)) {
            system.setSupportedProvisioningType(SupportedProvisioningTypes.THICK.toString());
        } else if (system.checkIfVmax3() && supportedElementTypes.contains(THINLY_PROVISIONED_STORAGE_VOLUME)) {
            system.setSupportedProvisioningType(SupportedProvisioningTypes.THIN.toString());
        } else {
            system.setSupportedProvisioningType(SupportedProvisioningTypes.NONE.toString());
        }
    }
    
    /**
     * Set supported provisioning type for storage system based on
     * SupportedStorageElementTypes for VNX
     * 
     * @param storageConfigurationInstance
     * @param system
     * @throws Exception
     */
    private void updateStorageSystemCapabilityOnVolumeForVNX(
            CIMInstance storageConfigurationInstance, StorageSystem system)
            throws Exception {
        UnsignedInteger16[] supportedElementTypeArr = (UnsignedInteger16[]) storageConfigurationInstance
                .getPropertyValue(SUPPORTED_ELEMENT_TYPES);
        String supportedElementTypes = Arrays.toString(supportedElementTypeArr);
        _logger.debug("Capability : {}" , supportedElementTypes);
        if (supportedElementTypes.contains(THINLY_PROVISIONED_STORAGE_VOLUME)) {
            system.setSupportedProvisioningType(SupportedProvisioningTypes.THIN_AND_THICK
                    .toString());
        } else {
            // Thick volume is supported by default on VNX
            system.setSupportedProvisioningType(SupportedProvisioningTypes.THICK
                    .toString());
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
