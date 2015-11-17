/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DecommissionedResource;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

public class DataCollectionJobUtil {
    private DbClient _dbClient;
    private RecordableEventManager _eventManager;

    private static final Logger _logger = LoggerFactory
            .getLogger(DataCollectionJobUtil.class);
    private Map<String, String> _configInfo;

    /**
     * Create AccessProfile from DiscoveryJob
     * 
     * TODO create subClasses for Accessprofile based on deviceType and Profile.
     * i.e. Metering-isilon accessProfile - a subclass under AccessProfile
     * 
     * @param clazz
     * @param objectID
     * @param jobProfile
     * @return AccessProfile
     * @throws IOException
     */
    public AccessProfile getAccessProfile(Class<? extends DataObject> clazz,
            URI objectID,
            String jobProfile, String nameSpace) throws DatabaseException, DeviceControllerException {
        DataObject taskObject = _dbClient.queryObject(clazz, objectID);
        AccessProfile profile = new AccessProfile();
        profile.setProfileName(jobProfile);
        profile.setRecordableEventManager(_eventManager);
        if (clazz == StorageProvider.class &&
                StorageProvider.InterfaceType.smis.name().equalsIgnoreCase(
                        ((StorageProvider) taskObject).getInterfaceType())) {
            populateSMISAccessProfile(profile, (StorageProvider) taskObject);
        } else if (clazz == StorageProvider.class &&
                StorageProvider.InterfaceType.hicommand.name().equalsIgnoreCase(
                        ((StorageProvider) taskObject).getInterfaceType())) {
            populateHDSAccessProfile(profile, (StorageProvider) taskObject);
        } else if (clazz == StorageProvider.class &&
                StorageProvider.InterfaceType.cinder.name().equalsIgnoreCase(
                        ((StorageProvider) taskObject).getInterfaceType())) {
            populateCinderAccessProfile(profile, (StorageProvider) taskObject);
        } else if ((clazz == StorageProvider.class && StorageProvider.InterfaceType.vplex
                .name().equalsIgnoreCase(((StorageProvider) taskObject).getInterfaceType()))
                || (clazz == StorageSystem.class && DiscoveredDataObject.Type.vplex.name()
                        .equalsIgnoreCase(((StorageSystem) taskObject).getSystemType()))) {
            populateVPLEXAccessProfile(profile, taskObject, nameSpace);
        } else if (clazz == StorageProvider.class &&
                StorageProvider.InterfaceType.scaleioapi.name().equalsIgnoreCase(
                        ((StorageProvider) taskObject).getInterfaceType())) {
            populateScaleIOAccessProfile(profile, (StorageProvider) taskObject);
        } else if (clazz == StorageProvider.class &&
                StorageProvider.InterfaceType.ddmc.name().equalsIgnoreCase(
                        ((StorageProvider) taskObject).getInterfaceType())) {
            populateDataDomainAccessProfile(profile, (StorageProvider) taskObject);
        } else if (clazz == StorageProvider.class &&
                StorageProvider.InterfaceType.ibmxiv.name().equalsIgnoreCase(
                        ((StorageProvider) taskObject).getInterfaceType())) {
            populateSMISAccessProfile(profile, (StorageProvider) taskObject);
            profile.setnamespace(Constants.IBM_NAMESPACE);
        } else if (clazz == StorageProvider.class &&
                StorageProvider.InterfaceType.xtremio.name().equalsIgnoreCase(
                        ((StorageProvider) taskObject).getInterfaceType())) {
            populateXtremIOAccessProfile(profile, (StorageProvider) taskObject);
        } else if (clazz == StorageSystem.class) {
            populateAccessProfile(profile, (StorageSystem) taskObject, nameSpace);
        } else if (clazz == ProtectionSystem.class) {
            populateAccessProfile(profile, (ProtectionSystem) taskObject, nameSpace);
        } else if (clazz == ComputeSystem.class) {
            populateAccessProfile(profile, (ComputeSystem) taskObject);
        }
        else if (clazz == NetworkSystem.class) {
            populateAccessProfile(profile, (NetworkSystem) taskObject);
        } else if (clazz == Host.class) {
            populateAccessProfile(profile, (Host) taskObject);
        } else if (clazz == Vcenter.class) {
            populateAccessProfile(profile, (Vcenter) taskObject);
        } else {
            throw new RuntimeException("getAccessProfile: profile is unknown for objects of type : "
                    + taskObject.getClass());
        }

        return profile;
    }

    private void populateScaleIOAccessProfile(AccessProfile accessProfile, StorageProvider providerInfo) {
        accessProfile.setSystemId(providerInfo.getId());
        accessProfile.setSystemClazz(providerInfo.getClass());
        accessProfile.setIpAddress(providerInfo.getIPAddress());
        accessProfile.setUserName(providerInfo.getUserName());
        accessProfile.setPassword(providerInfo.getPassword());
        accessProfile.setSystemType(DiscoveredDataObject.Type.scaleio.name());
        accessProfile.setPortNumber(providerInfo.getPortNumber());
    }

    private void populateAccessProfile(AccessProfile profile, NetworkSystem system) {
        profile.setSystemId(system.getId());
        profile.setSystemClazz(system.getClass());
        profile.setSystemType(system.getSystemType());
        if (system.getSystemType().equalsIgnoreCase(Type.mds.toString())) {
            profile.setIpAddress(system.getIpAddress());
            profile.setUserName(system.getUsername());
            profile.setPassword(system.getPassword());
            profile.setPortNumber(system.getPortNumber());
            profile.setSslEnable(Boolean.FALSE.toString());
        } else if (system.getSystemType().equalsIgnoreCase(Type.brocade.toString())) {
            profile.setIpAddress(system.getSmisProviderIP());
            profile.setUserName(system.getSmisUserName());
            profile.setPassword(system.getSmisPassword());
            profile.setPortNumber(system.getSmisPortNumber());
            profile.setSslEnable(system.getSmisUseSSL().toString());
            profile.setInteropNamespace("root/brocade1");
        }
    }

    private void populateAccessProfile(AccessProfile profile, ComputeSystem system) {
        profile.setSystemId(system.getId());
        profile.setSystemClazz(system.getClass());
        profile.setSystemType(Constants.COMPUTE);
        profile.setIpAddress(system.getIpAddress());
        profile.setUserName(system.getUsername());
        profile.setPassword(system.getPassword());
        profile.setPortNumber(system.getPortNumber());
        profile.setSslEnable("false");
    }

    private void populateAccessProfile(AccessProfile profile, Host host) {
        profile.setSystemId(host.getId());
        profile.setSystemClazz(host.getClass());
        profile.setSystemType(Type.host.toString());
        profile.setUserName(host.getUsername());
        profile.setPassword(host.getPassword());
    }

    private void populateAccessProfile(AccessProfile profile, Vcenter vcenter) {
        profile.setSystemId(vcenter.getId());
        profile.setSystemClazz(vcenter.getClass());
        profile.setSystemType(Type.vcenter.toString());
        profile.setUserName(vcenter.getUsername());
        profile.setPassword(vcenter.getPassword());
    }

    private void populateAccessProfile(AccessProfile profile, ProtectionSystem system, String nameSpace) {
        profile.setSystemId(system.getId());
        profile.setSystemClazz(system.getClass());
        profile.setSystemType(system.getSystemType());
        if (system.getSystemType().equalsIgnoreCase(Type.rp.toString())) {
            profile.setIpAddress(system.getIpAddress());
            profile.setUserName(system.getUsername());
            profile.setPassword(system.getPassword());
            profile.setPortNumber(system.getPortNumber());
            profile.setSslEnable(Boolean.TRUE.toString());
            profile.setserialID(system.getInstallationId());
        }
        if (null != nameSpace) {
            profile.setnamespace(nameSpace);
        }
    }

    /**
     * inject details needed for Scanning
     * 
     * @param accessProfile
     * @param providerInfo
     */
    private void populateSMISAccessProfile(AccessProfile accessProfile, StorageProvider providerInfo) {
        accessProfile.setSystemId(providerInfo.getId());
        accessProfile.setSystemClazz(providerInfo.getClass());
        accessProfile.setIpAddress(providerInfo.getIPAddress());
        accessProfile.setUserName(providerInfo.getUserName());
        accessProfile.setPassword(providerInfo.getPassword());
        accessProfile.setSystemType(getSystemType(providerInfo));
        accessProfile.setProviderPort(String.valueOf(providerInfo.getPortNumber()));
        accessProfile.setInteropNamespace(Constants.INTEROP);
        accessProfile.setSslEnable(String.valueOf(providerInfo.getUseSSL()));
    }

    private String getSystemType(StorageProvider provider) {
        if (StorageProvider.InterfaceType.smis.name().equals(provider.getInterfaceType())) {
            return Constants._Block;
        }
        else {
            return provider.getInterfaceType();
        }
    }

    /**
     * inject details needed for Scanning
     * 
     * @param accessProfile
     * @param providerInfo
     */
    private void populateVPLEXAccessProfile(AccessProfile accessProfile,
            DataObject vplexDataObject, String nameSpace) {

        if (vplexDataObject instanceof StorageProvider) {
            // Access profile for provider scanning
            StorageProvider vplexProvider = (StorageProvider) vplexDataObject;
            accessProfile.setSystemId(vplexProvider.getId());
            accessProfile.setSystemClazz(vplexProvider.getClass());
            accessProfile.setIpAddress(vplexProvider.getIPAddress());
            accessProfile.setUserName(vplexProvider.getUserName());
            accessProfile.setPassword(vplexProvider.getPassword());
            accessProfile.setSystemType(StorageProvider.InterfaceType.vplex.name());
            accessProfile.setPortNumber(vplexProvider.getPortNumber());
        } else {
            // Access profile for storage system discovery.
            StorageSystem vplexSystem = (StorageSystem) vplexDataObject;

            // Get the active VPLEX management server for the VPLEX system.
            StorageProvider activeProvider = null;
            URI activeProviderURI = vplexSystem.getActiveProviderURI();
            if (!NullColumnValueGetter.isNullURI(activeProviderURI)) {
                activeProvider = _dbClient.queryObject(StorageProvider.class,
                        activeProviderURI);
            }

            // If there is no active provider, we can't discover.
            if (activeProvider == null) {
                vplexSystem.setLastDiscoveryStatusMessage("Discovery failed because we could not find an active management server");
                _dbClient.persistObject(vplexSystem);
                throw DeviceControllerException.exceptions.cannotFindActiveProviderForStorageSystem();
            }

            accessProfile.setSystemId(vplexSystem.getId());
            accessProfile.setSystemClazz(vplexSystem.getClass());
            accessProfile.setSystemType(vplexSystem.getSystemType());
            accessProfile.setserialID(vplexSystem.getSerialNumber());
            accessProfile.setIpAddress(activeProvider.getIPAddress());
            accessProfile.setUserName(activeProvider.getUserName());
            accessProfile.setPassword(activeProvider.getPassword());
            accessProfile.setPortNumber(activeProvider.getPortNumber());
            // accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        }
    }

    /**
     * inject details needed for Scanning
     * 
     * @param accessProfile
     * @param providerInfo
     */
    private void populateHDSAccessProfile(AccessProfile accessProfile, StorageProvider providerInfo) {
        accessProfile.setSystemId(providerInfo.getId());
        accessProfile.setSystemClazz(providerInfo.getClass());
        accessProfile.setIpAddress(providerInfo.getIPAddress());
        accessProfile.setUserName(providerInfo.getUserName());
        accessProfile.setPassword(providerInfo.getPassword());
        accessProfile.setSystemType(DiscoveredDataObject.Type.hds.name());
        accessProfile.setPortNumber(providerInfo.getPortNumber());
        accessProfile.setSslEnable(String.valueOf(providerInfo.getUseSSL()));
    }

    /**
     * inject details needed for Scanning
     * 
     * @param accessProfile
     * @param providerInfo
     */
    private void populateDataDomainAccessProfile(AccessProfile accessProfile, StorageProvider providerInfo) {
        accessProfile.setSystemId(providerInfo.getId());
        accessProfile.setSystemClazz(providerInfo.getClass());
        accessProfile.setIpAddress(providerInfo.getIPAddress());
        accessProfile.setUserName(providerInfo.getUserName());
        accessProfile.setPassword(providerInfo.getPassword());
        accessProfile.setSystemType(DiscoveredDataObject.Type.datadomain.name());
        accessProfile.setPortNumber(providerInfo.getPortNumber());
    }

    /**
     * inject details needed for Scanning
     * 
     * @param accessProfile
     * @param providerInfo
     */
    private void populateCinderAccessProfile(AccessProfile accessProfile, StorageProvider providerInfo) {
        accessProfile.setSystemId(providerInfo.getId());
        accessProfile.setSystemClazz(providerInfo.getClass());
        accessProfile.setIpAddress(providerInfo.getIPAddress());
        accessProfile.setUserName(providerInfo.getUserName());
        accessProfile.setPassword(providerInfo.getPassword());
        accessProfile.setSystemType("cinder");
        accessProfile.setPortNumber(providerInfo.getPortNumber());
        accessProfile.setSslEnable(String.valueOf(providerInfo.getUseSSL()));
    }

    /**
     * inject details needed for Scanning
     * 
     * @param accessProfile
     * @param providerInfo
     */
    private void populateXtremIOAccessProfile(AccessProfile accessProfile, StorageProvider providerInfo) {
        accessProfile.setSystemId(providerInfo.getId());
        accessProfile.setSystemClazz(providerInfo.getClass());
        accessProfile.setIpAddress(providerInfo.getIPAddress());
        accessProfile.setUserName(providerInfo.getUserName());
        accessProfile.setPassword(providerInfo.getPassword());
        accessProfile.setSystemType(DiscoveredDataObject.Type.xtremio.name());
        accessProfile.setPortNumber(providerInfo.getPortNumber());
        accessProfile.setSslEnable(String.valueOf(providerInfo.getUseSSL()));
    }

    /**
     * inject Details needed for Discovery
     * 
     * @param accessProfile
     * @param system
     * @throws IOException
     */
    private void injectDiscoveryProfile(AccessProfile accessProfile,
            StorageSystem system) throws DatabaseException,
            DeviceControllerException {

        StorageProvider provider = getActiveProviderForStorageSystem(system,
                accessProfile);

        populateSMISAccessProfile(accessProfile, provider);
        accessProfile.setSystemId(system.getId());
        accessProfile.setSystemClazz(system.getClass());
        accessProfile.setserialID(system.getSerialNumber());
        accessProfile.setSystemType(system.getSystemType());
        String namespace = Constants.EMC_NAMESPACE;
        if (Type.ibmxiv.name().equals(system.getSystemType())) {
            namespace = Constants.IBM_NAMESPACE;
        }

        // To-Do: get Namespace field in SMISProvider
        accessProfile.setInteropNamespace(namespace);
    }

    /**
     * Return the active StorageProvider for a given storage system.
     * 
     * @param system
     * @param accessProfile
     * @return
     */
    private StorageProvider getActiveProviderForStorageSystem(
            StorageSystem system, AccessProfile accessProfile) {
        URI activeProviderURI = system.getActiveProviderURI();
        if (NullColumnValueGetter.isNullURI(activeProviderURI)) {
            // Set the error message only when the job type is discovery.
            if (ControllerServiceImpl.isDiscoveryJobTypeSupported(accessProfile
                    .getProfileName())) {
                system.setLastDiscoveryStatusMessage("Discovery failed "
                        + "since it does not have an active Provider");
                _dbClient.persistObject(system);
            }
            throw DeviceControllerException.exceptions
                    .cannotFindActiveProviderForStorageSystem();
        }
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class,
                activeProviderURI);
        if (provider == null) {
            if (ControllerServiceImpl.DISCOVERY.equalsIgnoreCase(accessProfile
                    .getProfileName())) {
                system.setLastDiscoveryStatusMessage("Discovery failed "
                        + "since it does not have a valid active Provider");
                _dbClient.persistObject(system);
            }
            throw DeviceControllerException.exceptions
                    .cannotFindValidActiveProviderForStorageSystem();
        }
        return provider;
    }

    /**
     * Populate accessProfile values from storageDevice.
     * 
     * @param accessProfile
     * @param storageDevice
     * @throws IOException
     */
    private void populateAccessProfile(AccessProfile accessProfile,
            StorageSystem storageDevice, String nameSpace) throws DatabaseException, DeviceControllerException {
        accessProfile.setSystemId(storageDevice.getId());
        accessProfile.setSystemClazz(storageDevice.getClass());
        if (Type.vnxblock.toString().equalsIgnoreCase(
                storageDevice.getSystemType())
                || Type.vmax.toString().equalsIgnoreCase(
                        storageDevice.getSystemType())
                || Type.ibmxiv.name().equals(
                        storageDevice.getSystemType())) {
            injectDiscoveryProfile(accessProfile, storageDevice);
        } else if (Type.vnxfile.toString().equalsIgnoreCase(storageDevice.getSystemType())) {
            accessProfile.setIpAddress(storageDevice.getIpAddress());
            accessProfile.setUserName(storageDevice.getUsername());
            accessProfile.setPassword(storageDevice.getPassword());
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setserialID(storageDevice.getSerialNumber());
            if (null != storageDevice.getPortNumber()) {
                accessProfile.setPortNumber(storageDevice.getPortNumber());
            }
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(
                Type.isilon.toString())) {
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setIpAddress(storageDevice.getIpAddress());
            accessProfile.setUserName(storageDevice.getUsername());
            accessProfile.setserialID(storageDevice.getSerialNumber());
            accessProfile.setPassword(storageDevice.getPassword());
            accessProfile.setPortNumber(storageDevice.getPortNumber());
            accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(
                Type.vplex.toString())) {
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setIpAddress(storageDevice.getIpAddress());
            accessProfile.setUserName(storageDevice.getUsername());
            accessProfile.setserialID(storageDevice.getSerialNumber());
            accessProfile.setPassword(storageDevice.getPassword());
            accessProfile.setPortNumber(storageDevice.getPortNumber());
            accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(Type.netapp.toString())
                || storageDevice.getSystemType().equals(Type.netappc.toString())
                || Type.vnxe.toString().equalsIgnoreCase(storageDevice.getSystemType())) {
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setIpAddress(storageDevice.getIpAddress());
            accessProfile.setUserName(storageDevice.getUsername());
            accessProfile.setPassword(storageDevice.getPassword());
            accessProfile.setserialID(storageDevice.getSerialNumber());
            accessProfile.setPortNumber(storageDevice.getPortNumber());
            accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(
                Type.rp.toString())) {
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setIpAddress(storageDevice.getIpAddress());
            accessProfile.setUserName(storageDevice.getUsername());
            accessProfile.setserialID(storageDevice.getSerialNumber());
            accessProfile.setPassword(storageDevice.getPassword());
            accessProfile.setPortNumber(storageDevice.getPortNumber());
            accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(Type.datadomain.toString())) {
            injectDiscoveryProfile(accessProfile, storageDevice);
            accessProfile.setPortNumber(storageDevice.getSmisPortNumber());
            accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(
                Type.scaleio.toString())) {
            injectDiscoveryProfile(accessProfile, storageDevice);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(
                Type.openstack.toString())) {
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setIpAddress(storageDevice.getSmisProviderIP());
            accessProfile.setUserName(storageDevice.getSmisUserName());
            accessProfile.setserialID(storageDevice.getSerialNumber());
            accessProfile.setPassword(storageDevice.getSmisPassword());
            accessProfile.setPortNumber(storageDevice.getSmisPortNumber());
            accessProfile.setLastSampleTime(0L);
        } else if (storageDevice.getSystemType().equals(
                Type.xtremio.toString())) {
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setIpAddress(storageDevice.getSmisProviderIP());
            accessProfile.setUserName(storageDevice.getSmisUserName());

            accessProfile.setPassword(storageDevice.getSmisPassword());
            accessProfile.setPortNumber(storageDevice.getSmisPortNumber());
            accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(
                Type.ecs.toString())) {
            accessProfile.setSystemType(storageDevice.getSystemType());
            accessProfile.setIpAddress(storageDevice.getIpAddress());
            accessProfile.setUserName(storageDevice.getUsername());
            accessProfile.setserialID(storageDevice.getSerialNumber());
            accessProfile.setPassword(storageDevice.getPassword());
            accessProfile.setPortNumber(storageDevice.getPortNumber());
            accessProfile.setLastSampleTime(0L);
            if (null != nameSpace) {
                accessProfile.setnamespace(nameSpace);
            }
        } else if (storageDevice.getSystemType().equals(Type.hds.toString())) {
            populateHDSAccessProfile(accessProfile, storageDevice, nameSpace);
        } else {
            throw new RuntimeException("populateAccessProfile: Device type unknown : "
                    + storageDevice.getSystemType());
        }
    }

    /**
     * Get the AccessProfile details based on the profile Name.
     * For Hitachi, metering is handled thru SMI-S.
     * Hitachi supports two types of providers
     * 1. Embedded SMI-S running SVP -> supports only HUS VM, VSP, VSP G1000.
     * 2. HiCommand Suite Provider -> supports only AMS series
     * 
     * @TODO need to look at HUS series model.
     * @TODO User should create a new user using storage navigator to do metering.
     * 
     *       Prerequisites for Embedded SMI-S Provider.
     *       User should enable certificate to activate SMI-S and enable performance monitor using storage navigator.
     * 
     * @TODO should we document above prerequisites?
     * 
     * @param accessProfile
     * @param storageDevice
     * @param nameSpace
     */
    private void populateHDSAccessProfile(AccessProfile accessProfile,
            StorageSystem storageDevice, String nameSpace) {

        accessProfile.setSystemType(storageDevice.getSystemType());
        accessProfile.setserialID(storageDevice.getSerialNumber());
        accessProfile.setLastSampleTime(0L);

        if (null != nameSpace) {
            accessProfile.setnamespace(nameSpace);
        }

        if (accessProfile.getProfileName().equalsIgnoreCase(
                ControllerServiceImpl.METERING)) {
            accessProfile
                    .setIpAddress(getHDSSMISIPAddressBasedOnModel(storageDevice));
            accessProfile.setInteropNamespace(HDSConstants.HITACHI_NAMESPACE);
            // Currently API is not supporting hence hardcoded to make them
            // work.
            // @TODO remove once API is fixed.
            accessProfile.setPortNumber(getHDSSMISPortBasedOnModel(storageDevice));
            accessProfile.setSslEnable(Boolean.TRUE.toString());
            accessProfile.setUserName(storageDevice.getSmisUserName());
            accessProfile.setPassword(storageDevice.getSmisPassword());
        } else {
            StorageProvider activeProvider = getActiveProviderForStorageSystem(
                    storageDevice, accessProfile);
            // For discovery use the active provider credentials directly.
            accessProfile.setIpAddress(activeProvider.getIPAddress());
            accessProfile.setPortNumber(activeProvider.getPortNumber());
            accessProfile.setUserName(activeProvider.getUserName());
            accessProfile.setPassword(activeProvider.getPassword());
            accessProfile.setSslEnable(String.valueOf(activeProvider
                    .getUseSSL()));
        }

    }

    /**
     * If storageDevice is not AMS, use embedded provider else HCS ipAddress.
     * 
     * @param storageDevice
     * @return
     */
    private String getHDSSMISIPAddressBasedOnModel(StorageSystem storageDevice) {
        return (!HDSUtils.checkForAMSSeries(storageDevice) ? storageDevice
                .getIpAddress() : storageDevice.getSmisProviderIP());
    }

    /**
     * Return the default ports based on the model.
     * 
     * @param storageDevice
     * @return
     */
    private int getHDSSMISPortBasedOnModel(StorageSystem storageDevice) {
        return (!HDSUtils.checkForAMSSeries(storageDevice) ? 5989 : 5988);
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public RecordableEventManager getEventManager() {
        return _eventManager;
    }

    public void setEventManager(RecordableEventManager eventManager) {
        _eventManager = eventManager;
    }

    /**
     * 1. Iterate through the scanner result cache.
     * 2. For each serialId, find a storageSystem.
     * 3. If it is null, then create a new StorageSystem and update the details.
     * 4. If it is not null, then update active providers & registered status.
     * 
     * @param scannedSystemsNativeGuidsMap
     *            : scanner result cache.
     */
    public void performBookKeeping(
            Map<String, StorageSystemViewObject> scannedSystemsNativeGuidsMap,
            List<URI> providerList) {
        StorageSystem storageSystem = null;
        List<StorageSystem> systemsToPersist = new ArrayList<StorageSystem>();
        List<StorageSystem> systemsToCreate = new ArrayList<StorageSystem>();
        Set<String> scannedSystemNativeGuidKeySet;
        synchronized (scannedSystemsNativeGuidsMap) {
            scannedSystemNativeGuidKeySet = new HashSet(scannedSystemsNativeGuidsMap.keySet());
        }
        Set<URI> scannedProviderList = new HashSet<URI>(providerList);
        Map<URI, List<String>> providersToUpdate = new HashMap<URI, List<String>>();

        for (String scannedSystemNativeGuid : scannedSystemNativeGuidKeySet) {
            try {
                _logger.info("scannedSystemNativeGuid:" + scannedSystemNativeGuid);
                List<StorageSystem> systems =
                        CustomQueryUtility.getActiveStorageSystemByNativeGuid(_dbClient, scannedSystemNativeGuid);
                if (DecommissionedResource.checkDecommissioned(_dbClient, scannedSystemNativeGuid,
                        StorageSystem.class)) {
                    scannedSystemsNativeGuidsMap.remove(scannedSystemNativeGuid);
                    _logger.info("Storage system {} was decommissioned and cannot be added to Vipr", scannedSystemNativeGuid);
                    continue;
                }

                if (null == systems || systems.isEmpty()) {
                    storageSystem = createStorageSystem(
                            scannedSystemsNativeGuidsMap.get(scannedSystemNativeGuid),
                            scannedSystemNativeGuid, providersToUpdate);
                    if (storageSystem != null) {
                        systemsToCreate.add(storageSystem);
                        _logger.info("Added new storage system to be created to the create list with Native Guid:" + storageSystem.getNativeGuid());
                    }
                }
            } catch (Exception e) {
                _logger.error(e.getMessage(), e);
                _logger.error(
                        "Exception while creating new system: {} due to {}",
                        storageSystem != null ? storageSystem.getId() : "N/A", e.getCause());
            }
        }
        try {
            _dbClient.createObject(systemsToCreate);
        } catch (DatabaseException ex) {
            _logger.error(
                    "Exception occurred while persisting new StorageSystems due to ",
                    ex);
        }
        // If a provider is not managing an array and it is moved to some other array,
        // then this will update the active provider in the storage system.
        updateActiveProviderDetailsInDbSystem(scannedSystemsNativeGuidsMap,
                systemsToPersist, scannedProviderList, providersToUpdate);
        // Persist all storage systems & providers
        persistAllSystemsAndProviders(systemsToPersist,
                getSMISProvidersWithUpdatedSystems(providersToUpdate));
    }

    /**
     * Update all the SMISProviders with their actively managed storage systems information.
     * 
     * @param providersToUpdate : dataStructure holds the provider => list of managed systems.
     */
    private List<StorageProvider> getSMISProvidersWithUpdatedSystems(Map<URI, List<String>> providersToUpdate) {
        List<StorageProvider> providerList = new ArrayList<StorageProvider>();
        if (!providersToUpdate.isEmpty()) {
            Iterator<URI> providerIdKeyItr = providersToUpdate.keySet()
                    .iterator();
            while (providerIdKeyItr.hasNext()) {
                URI providerIdKey = null;
                try {
                    providerIdKey = providerIdKeyItr.next();
                    List<String> storageSystemList = providersToUpdate
                            .get(providerIdKey);
                    StorageProvider provider = _dbClient.queryObject(
                            StorageProvider.class, providerIdKey);
                    if (null != provider.getStorageSystems()) {
                        provider.getStorageSystems().clear();
                        provider.getStorageSystems().addAll(storageSystemList);
                    } else {
                        StringSet storageSystems = new StringSet();
                        storageSystems.addAll(storageSystemList);
                        provider.setStorageSystems(storageSystems);
                    }
                    providerList.add(provider);
                } catch (DatabaseException ioEx) {
                    _logger.error(
                            "IOException occurred while updating storageSystems for provider {}",
                            providerIdKey);
                }
            }
        }
        return providerList;
    }

    /**
     * Creates a new StorageSystem if there is a new system managed by provider.
     * 
     * @param storageSystemViewObject
     *            : system details to persist.
     * @param scannedStorageSystemNativeGuid
     *            : new Array nativeGuid.
     * @param providersToUpdate: dataStructure to hold provider and its systems managed.
     * @return : StorageSystem db object.
     * @throws IOException
     */
    private StorageSystem createStorageSystem(
            StorageSystemViewObject storageSystemViewObject, String scannedStorageSystemNativeGuid,
            Map<URI, List<String>> providersToUpdate)
            throws IOException {

        Set<String> providerSet = storageSystemViewObject.getProviders();
        StorageSystem newStorageSystem = null;
        Iterator<String> iterator = providerSet.iterator();
        if (iterator.hasNext()) {
            // Find StorageProvider that should be associated with this StorageSystem.
            StorageProvider providerFromDB;
            do {
                String provider = iterator.next();
                providerFromDB = _dbClient.queryObject(StorageProvider.class, URI.create(provider));
            } while (iterator.hasNext() && (providerFromDB == null || providerFromDB.getInactive()));

            // If after looking through the provider list there was nothing active found, return null
            if (providerFromDB == null || providerFromDB.getInactive()) {
                _logger.info(String.format("StorageSystem %s was found during scan but could not find its associated active providers. " +
                        "Could have been deleted while scan was occurring.", scannedStorageSystemNativeGuid));
                return null;
            }
            _logger.info("Scanned StorageSystemNativeGuid for a new Storage System:" + scannedStorageSystemNativeGuid);
            newStorageSystem = new StorageSystem();
            newStorageSystem.setId(URIUtil.createId(StorageSystem.class));
            newStorageSystem.setNativeGuid(scannedStorageSystemNativeGuid);
            newStorageSystem.setSystemType(storageSystemViewObject.getDeviceType());

            String model = storageSystemViewObject.getProperty(storageSystemViewObject.MODEL);
            if (StringUtils.isNotBlank(model)) {
                newStorageSystem.setModel(model);
            }
            String serialNo = storageSystemViewObject.getProperty(storageSystemViewObject.SERIAL_NUMBER);
            if (StringUtils.isNotBlank(serialNo)) {
                newStorageSystem.setSerialNumber(serialNo);
            }
            String version = storageSystemViewObject.getProperty(storageSystemViewObject.VERSION);
            if (StringUtils.isNotBlank(version)) {
                newStorageSystem.setFirmwareVersion(version);
            }

            String name = storageSystemViewObject.getProperty(storageSystemViewObject.STORAGE_NAME);
            if (StringUtils.isNotBlank(name)) {
                newStorageSystem.setLabel(name);
            }

            StringSet allProviders = new StringSet(providerSet);
            newStorageSystem.setProviders(allProviders);
            setActiveProviderDetailsInSystem(providerFromDB, newStorageSystem, providersToUpdate);
            newStorageSystem.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            newStorageSystem.setReachableStatus(true);
        }
        return newStorageSystem;
    }

    /**
     * Update the Provider => systems managed.
     * 
     * @param provider : SMISProvider
     * @param providersToUpdate : dataStructure holds the provider -> list of managed systems.
     * @param storageSystemInDB : StorageDevice
     */
    private void updateStorageSystemsInProvider(StorageProvider provider,
            Map<URI, List<String>> providersToUpdate, StorageSystem storageSystemInDB) {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        List<String> storageSystems = null;
        if (providersToUpdate.containsKey(provider.getId())) {
            storageSystems = providersToUpdate.get(provider.getId());
        } else {
            storageSystems = new ArrayList<String>();
        }
        storageSystems.add(storageSystemInDB.getId().toString());
        providersToUpdate.put(provider.getId(), storageSystems);
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Set the ActiveProvider details in StorageSystem.
     * 
     * @param provider
     *            : current Active Provider.
     * @param system
     *            : storageSystem to update.
     */
    private void setActiveProviderDetailsInSystem(StorageProvider provider,
            StorageSystem system, Map<URI, List<String>> providersToUpdate) {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        // set the active provider details in the StorageSystem.
        system.setReachableStatus(true);
        system.setActiveProviderURI(provider.getId());
        // TODO needs to create Modify/add new columns. As of now we will use the same smis related attributes
        system.setSmisPassword(provider.getPassword());
        system.setSmisPortNumber(provider.getPortNumber());
        system.setSmisProviderIP(provider.getIPAddress());
        system.setSmisUserName(provider.getUserName());
        system.setSmisUseSSL(provider.getUseSSL());
        updateStorageSystemsInProvider(provider, providersToUpdate, system);
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Finds & update the storage systems in the following scenarios.
     * 
     * 1. Query all systems from db.
     * 2. If the system is not vnxblock or vmaxblock, then continue.
     * 3. Verify each system whether it is in the scanned list or not.
     * 4. If it is in the scannedList then update the activeprovider details.
     * 5. If it is not in the scannedList then
     * 6 .Check whether the system's active Provider is part of the InitialScanList
     * (both Providers which tookj part for Scanning + providers which had dropped due
     * to connection issues found in refreshConnections)
     * 7. If yes, then set check whether the system is registered or not.
     * If it is registered, then set the reachableStatus to false
     * else delete all its components.
     * 8. If NO, then don't disturb.
     * 
     * 
     * @param scannedSystemsNativeGuidsMap
     *            : List of scanned Systems NativeGuids.
     * @param systemsToPersist : List of systems to persist.
     * @param initialScanList : intialScanned List of providers.
     * @param providersToUpdate : Providers to update holds provider => List of managed systems
     */
    public void updateActiveProviderDetailsInDbSystem(
            Map<String, StorageSystemViewObject> scannedSystemsNativeGuidsMap,
            List<StorageSystem> systemsToPersist, Set<URI> initialScanList,
            Map<URI, List<String>> providersToUpdate) {
        Iterator<URI> storageSystemUrisInDb;
        StorageSystem storageSystemInDb = null;
        try {
            storageSystemUrisInDb = _dbClient.queryByType(StorageSystem.class, true).iterator();
        } catch (DatabaseException e) {
            _logger.error(
                    "Exception occurred while querying db to get StorageSystems due to ",
                    e);
            return;
        }
        while (storageSystemUrisInDb.hasNext()) {
            URI dbSystemUri = storageSystemUrisInDb.next();
            try {
                storageSystemInDb = _dbClient.queryObject(StorageSystem.class,
                        dbSystemUri);
                if (null == storageSystemInDb || !storageSystemInDb.storageSystemHasProvider()) {
                    continue;
                }
                // By this time, DB has true reflection of physical Environment
                // Case 1: registered and managed by provider. mark it visible
                // Case 2: not registered still managed by provider. mark it
                // visible.
                String dbSystemNativeGuid = storageSystemInDb.getNativeGuid();
                if (scannedSystemsNativeGuidsMap
                        .containsKey(dbSystemNativeGuid)) {
                    StorageSystemViewObject systemDetails = scannedSystemsNativeGuidsMap
                            .get(dbSystemNativeGuid);
                    updateActiveProviders(systemDetails, storageSystemInDb,
                            providersToUpdate);
                } else {
                    if (initialScanList.contains(storageSystemInDb
                            .getActiveProviderURI())) {
                        // Case 3: registered but not managed by provider mark
                        // it
                        // Invisible.
                        if (RegistrationStatus.REGISTERED.toString()
                                .equalsIgnoreCase(
                                        storageSystemInDb
                                                .getRegistrationStatus())) {
                            injectReachableStatusInSystem(storageSystemInDb,
                                    null, NullColumnValueGetter.getNullURI(), false);
                        }
                    }
                }
            } catch (Exception e) {
                _logger.error(
                        "Exception while updating visible status for id: {} due to",
                        storageSystemInDb.getId(), e);
            }
            systemsToPersist.add(storageSystemInDb);
        }
    }

    /**
     * Updates the active/passive provider details in the StorageSystem object.
     * 
     * @param scannedStorageSystemViewObj
     *            : scannedProvider details of this system.
     * @param storageSystemInDb
     *            : storageSystem in DB.
     * @param providersToUpdate : Providers to update.
     */
    private void updateActiveProviders(
            StorageSystemViewObject scannedStorageSystemViewObj, StorageSystem storageSystemInDb,
            Map<URI, List<String>> providersToUpdate) throws IOException {
        Set<String> allProviders = scannedStorageSystemViewObj.getProviders();
        // 1. If activeProviders are null then set the active to null and blank set to providers.
        if (allProviders == null || allProviders.isEmpty()) {
            injectReachableStatusInSystem(storageSystemInDb, null, NullColumnValueGetter.getNullURI(), false);
            return;
            // 2. If Current ActiveProvider is not in ActiveList.
        } else if (!allProviders.contains(storageSystemInDb.getActiveProviderURI().toString())) {
            Iterator<String> iterator = allProviders.iterator();
            if (iterator.hasNext()) {
                String newProviderURI = iterator.next();
                injectReachableStatusInSystem(storageSystemInDb, allProviders,
                        URI.create(newProviderURI), true);
                StorageProvider newProvider = _dbClient.queryObject(
                        StorageProvider.class, URI.create(newProviderURI));
                setActiveProviderDetailsInSystem(newProvider,
                        storageSystemInDb, providersToUpdate);
            } else {
                injectReachableStatusInSystem(storageSystemInDb, null,
                        NullColumnValueGetter.getNullURI(), false);
            }
        } else {
            // If the current provider is already active, then set its passive providers.
            StringSet dbSystemAllProviders = storageSystemInDb.getProviders();
            if (null != dbSystemAllProviders && !dbSystemAllProviders.isEmpty()) {
                storageSystemInDb.getProviders().addAll(allProviders);
            } else {
                StringSet scannedProviders = new StringSet(allProviders);
                storageSystemInDb.setProviders(scannedProviders);
            }
            // Even if the current provider is active, we should update the storage systems in SMISProvider.
            for (String providerStr : allProviders) {
                StorageProvider provider = _dbClient.queryObject(StorageProvider.class, URI.create(providerStr));
                updateStorageSystemsInProvider(provider, providersToUpdate, storageSystemInDb);

                // even if the current provider is already active, we need to update the provider details
                // in storage system object, so that any change in provider object will take effect.
                if (provider.getId().equals(storageSystemInDb.getActiveProviderURI())) {
                    storageSystemInDb.setSmisProviderIP(provider.getIPAddress());
                    storageSystemInDb.setSmisPortNumber(provider.getPortNumber());
                    storageSystemInDb.setSmisUserName(provider.getUserName());
                    storageSystemInDb.setSmisPassword(provider.getPassword());
                    storageSystemInDb.setSmisUseSSL(provider.getUseSSL());
                }
            }
        }
    }

    /**
     * Update the reachable status, ActiveProviderURI & allProviders in system.
     * This method is written to avoid duplicate code.
     * 
     * @param storageSystemInDb : StorageSystem object.
     * @param allProviders : allProviders managing this system
     * @param newActiveProviderURI : If provider is down, then new provider to update.
     * @param reachable : reachable status.
     */
    private void injectReachableStatusInSystem(StorageSystem storageSystemInDb,
            Set<String> allProviders, URI newActiveProviderURI,
            boolean reachable) {
        StringSet dbSystemAllProviders = storageSystemInDb.getProviders();
        if (null != allProviders && !allProviders.isEmpty()) {
            if (null != dbSystemAllProviders) {
                storageSystemInDb.getProviders().clear();
                storageSystemInDb.getProviders().addAll(allProviders);
            } else {
                StringSet scannedProviders = new StringSet(allProviders);
                storageSystemInDb.setProviders(scannedProviders);
            }
        } else {
            storageSystemInDb.getProviders().clear();
        }
        storageSystemInDb.setReachableStatus(reachable);
        if (null != newActiveProviderURI) {
            storageSystemInDb.setActiveProviderURI(newActiveProviderURI);
        }
        if (!reachable) {
            storageSystemInDb.setSmisPassword("");
            storageSystemInDb.setSmisPortNumber(0);
            storageSystemInDb.setSmisProviderIP("");
            storageSystemInDb.setSmisUserName("");
        }
    }

    /**
     * Persist all storageSystems with the active/passive.
     * Persists all smisprovider with currently managed systems.
     * provider information in DB.
     * 
     * @param systemsToPersist
     *            : Updated list of Storage systems to commit to DB.
     * @param providerToPersist : Update list of providers with its managed systems list.
     */
    private void persistAllSystemsAndProviders(
            final List<StorageSystem> systemsToPersist,
            final List<StorageProvider> providerToPersist) {
        try {
            _dbClient.persistObject(systemsToPersist);
            _dbClient.persistObject(providerToPersist);
        } catch (DatabaseException ex) {
            _logger.error(
                    "Exception occurred while updating StorageSystems & SMISProviders due to ",
                    ex);
        }
    }

    public void setConfigInfo(Map<String, String> configInfo) {
        _configInfo = configInfo;
    }

    public Map<String, String> getConfigInfo() {
        return _configInfo;
    }
}
