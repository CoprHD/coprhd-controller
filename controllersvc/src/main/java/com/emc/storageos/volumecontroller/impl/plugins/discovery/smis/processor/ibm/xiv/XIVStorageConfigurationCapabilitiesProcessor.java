/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.ibm.xiv;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.UnsignedInteger16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedProvisioningTypes;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

public class XIVStorageConfigurationCapabilitiesProcessor extends Processor {
    private Logger _logger = LoggerFactory
            .getLogger(XIVStorageConfigurationCapabilitiesProcessor.class);
    private static final String SUPPORTED_ELEMENT_TYPES = "SupportedStorageElementTypes";
    private static final String THIN_VOLUME_SUPPORTED = "5";
    private static final String THICK_VOLUME_SUPPORTED = "2";
    private DbClient _dbClient;
    private AccessProfile _profile = null;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        @SuppressWarnings("unchecked")
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
        _profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        try {
            StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                    _profile.getSystemId());

            while (it.hasNext()) {
                CIMInstance storageConfigurationInstance = it.next();
                UnsignedInteger16[] supportedElementTypeArr = (UnsignedInteger16[]) storageConfigurationInstance
                        .getPropertyValue(SUPPORTED_ELEMENT_TYPES);
                String supportedElementTypes = Arrays
                        .toString(supportedElementTypeArr);
                if (_logger.isDebugEnabled()) {
                    _logger.debug("Capability:" + supportedElementTypes);
                }

                if (supportedElementTypes.contains(THIN_VOLUME_SUPPORTED)
                        && supportedElementTypes
                                .contains(THICK_VOLUME_SUPPORTED)) {
                    system.setSupportedProvisioningType(SupportedProvisioningTypes.THIN_AND_THICK
                            .name());
                } else if (supportedElementTypes
                        .contains(THIN_VOLUME_SUPPORTED)) {
                    system.setSupportedProvisioningType(SupportedProvisioningTypes.THIN
                            .name());
                } else if (supportedElementTypes
                        .contains(THICK_VOLUME_SUPPORTED)) {
                    system.setSupportedProvisioningType(SupportedProvisioningTypes.THICK
                            .name());
                } else {
                    system.setSupportedProvisioningType(SupportedProvisioningTypes.NONE
                            .name());
                }

                break; // should have only one instance
            }

            _dbClient.persistObject(system);
        } catch (Exception e) {
            _logger.error(
                    "Finding out Storage System Capability on Volume Creation failed: ",
                    e);
        }
    }
}
