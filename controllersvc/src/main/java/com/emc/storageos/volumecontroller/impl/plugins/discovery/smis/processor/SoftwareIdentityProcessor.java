/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.StorageProvider;

import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class SoftwareIdentityProcessor extends Processor{
    private Logger _logger = LoggerFactory.getLogger(SoftwareIdentityProcessor.class);
    private static final String VERSIONSTRING = "VersionString";
    private static final String MANFACTURER = "Manufacturer";
    private static final String DESCRIPTION = "Description";
    private static final String MAJORVERSION = "MajorVersion";
    private static final String MINORVERSION = "MinorVersion";
    private static final String REVISIONNUMBER = "RevisionNumber";
    private static final String BUILD_NUMBER = "BuildNumber";
    private static final String BLOCKED_VER802 = "8.0.2";
    
    private DbClient _dbClient;
    private CoordinatorClient coordinator;

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        AccessProfile profile = null;
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            coordinator = (CoordinatorClient) keyMap.get(Constants.COORDINATOR_CLIENT);
            profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            if (it.hasNext()) {
                CIMInstance softwareInstance = it.next();
                String providerId = profile.getIpAddress() + "-" + profile.getProviderPort();
                List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByProviderId(_dbClient, providerId);
                if (!providers.isEmpty()) {
                    checkProviderVersion(softwareInstance, providers.get(0));
                }
            } else {
                String errMsg = String.format("No information obtained from Provider %s for Provider version", profile.getIpAddress());
                throw new SMIPluginException(errMsg, SMIPluginException.ERRORCODE_OPERATIONFAILED);
            }
        } catch (SMIPluginException e) {
            throw e;
        } catch (Exception e) {
            String errMsg = String.format("An error occurred while verifying Provider version: %s", e.getMessage());
            throw new SMIPluginException(SMIPluginException.ERRORCODE_OPERATIONFAILED, e, errMsg);
        }
    }

    /**
     * Provider version check.
     *
     * @param softwareInstance
     * @param provider
     * @throws SMIPluginException
     */    
    private void checkProviderVersion(CIMInstance softwareInstance, StorageProvider provider)
            throws SMIPluginException {
        String instanceVersion =
                String.format("%s.%s.%s.%s",
                        getCIMPropertyValue(softwareInstance, MAJORVERSION),
                        getCIMPropertyValue(softwareInstance, MINORVERSION),
                        getCIMPropertyValue(softwareInstance, REVISIONNUMBER),
                        getCIMPropertyValue(softwareInstance, BUILD_NUMBER));
        provider.setVersionString(getCIMPropertyValue(softwareInstance, VERSIONSTRING));

        // Get supported version from Co-ordinator.
        String minimumSupportedVersion = ControllerUtils.getPropertyValueFromCoordinator(coordinator, isIBMInstance(softwareInstance) ? Constants.IBMXIV_PROVIDER_VERSION : Constants.PROVIDER_VERSION);

        _logger.info("Verifying version details : Minimum Supported Version {} - Discovered Provider Version {}",
                minimumSupportedVersion, instanceVersion);
        
        //Avoid 8.0.2 providers; This code is written only for VMAX providers
        //Other arrays(VNX, XIV) don't have providers running with 802 version
        if (instanceVersion.indexOf(BLOCKED_VER802) == 0) {
            provider.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.toString());
            _dbClient.persistObject(provider);
        	String msg = String.format("Provider version %s is not supported.", BLOCKED_VER802);
        	_logger.warn(msg);
        	throw new SMIPluginException(msg, SMIPluginException.ERRORCODE_PROVIDER_NOT_SUPPORTED);
        }
        
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, instanceVersion) < 0) {
            provider.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.toString());
            _dbClient.persistObject(provider);

            String msg = String.format("Provider version %s is not supported. Should be a minimum of %s",
                    instanceVersion, minimumSupportedVersion);
            _logger.warn(msg);
            throw new SMIPluginException(msg, SMIPluginException.ERRORCODE_PROVIDER_NOT_SUPPORTED);
        } else {
            provider.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
            provider.setDescription(getCIMPropertyValue(softwareInstance, DESCRIPTION));
            provider.setManufacturer(getCIMPropertyValue(softwareInstance, MANFACTURER));
            _dbClient.persistObject(provider);            
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
        
    }
}
