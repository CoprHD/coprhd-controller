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

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Check if supported firmware version,else throw exception, which would stop the discovery.
 * 
 */
public class FirmwareProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(FirmwareProcessor.class);
    private static final String VERSION = "VersionString";
    private static final String INSTANCEID ="InstanceID";
    private DbClient _dbClient;
    private CoordinatorClient coordinator;

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            String serialNumber = null;
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            coordinator = (CoordinatorClient) keyMap.get(Constants.COORDINATOR_CLIENT);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);

            String delimiter = Constants.PATH_DELIMITER_REGEX;
            if (Type.ibmxiv.name().equals(profile.getSystemType())) {
                delimiter = Pattern.quote(Constants.COLON);
            }
               
            if (it.hasNext()) {
                CIMInstance firmwareInstance = it.next(); // e.g., IBM XIV InstanceID, IBMTSDS:IBM.2810-7825363
               	serialNumber = firmwareInstance.getPropertyValue(INSTANCEID)
               	        .toString().split(delimiter)[1];
                       
                String nativeGuid =  NativeGUIDGenerator.generateNativeGuid(profile.getSystemType(),serialNumber);
                List<StorageSystem> systems = CustomQueryUtility.getActiveStorageSystemByNativeGuid(_dbClient, nativeGuid);
                if (!systems.isEmpty()) {
                    StorageSystem system = systems.get(0);
                    checkFirmwareVersion(firmwareInstance, system);
                }
            } else {
                String errMsg = String.format("No information obtained from Provider %s for Firmware version", profile.getIpAddress());
                throw new SMIPluginException(errMsg, SMIPluginException.ERRORCODE_OPERATIONFAILED);
            }
        } catch (SMIPluginException e) {
            throw e;
        } catch (Exception e) {
            String errMsg = String.format("An error occurred while verifying Firmware version: %s", e.getMessage());
            throw new SMIPluginException(SMIPluginException.ERRORCODE_OPERATIONFAILED, e, errMsg);
        }
    }

    /**
	 * Firmware check.
	 *
	 * @param firmwareInstance
	 * @param system
	 * @throws SMIPluginException
	 */
	private void checkFirmwareVersion(CIMInstance firmwareInstance, StorageSystem system)
            throws SMIPluginException {
        String instanceVersion = getCIMPropertyValue(firmwareInstance, VERSION);
        system.setFirmwareVersion(instanceVersion);

        String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(system.getSystemType()));
        _logger.info("Verifying version details : Minimum Supported Version {} - Discovered Firmware Version {}",
                minimumSupportedVersion, instanceVersion);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, instanceVersion) < 0) {
            String msg = String.format("Firmware version %s is not supported. Should be a minimum of %s",
                    instanceVersion, minimumSupportedVersion);
            _logger.warn(msg);
            system.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.toString());
            DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, coordinator, system.getId());
            _dbClient.persistObject(system);
            throw new SMIPluginException(msg, SMIPluginException.ERRORCODE_FIRMWARE_NOT_SUPPORTED);
        } else {
            system.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
            _dbClient.persistObject(system);            
        }
    }
}
