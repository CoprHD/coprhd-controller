/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.api.CinderApiFactory;
import com.emc.storageos.cinder.model.VolumeTypes;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.cinder.CinderColletionException;
import com.emc.storageos.volumecontroller.impl.cinder.CinderUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

class Section {
    int index;
    String title;
    String volume_driver;
    String volume_backend_name;

    Section() {
        this.title = "";
        this.volume_driver = "";
        this.volume_backend_name = "";
    }
}

/**
 * CinderCommunicationInterface class is an implementation of
 * CommunicationInterface which is responsible to scan the OpenStack device manager.
 * It also does the discovery of pools (but not ports and adapters).
 * 
 */
public class CinderCommunicationInterface extends ExtendedCommunicationInterfaceImpl
{
    /**
     * Logger instance to log messages.
     */
    private static final Logger _logger = LoggerFactory.getLogger(CinderCommunicationInterface.class);
    private static final String CONFFILE = "/etc/cinder/cinder.conf";
    private static final String VOLUME_BACKEND_NAME = "volume_backend_name";
    private static final String VIPR_THICK_POOL = "vipr:is_thick_pool";
    private static final long DEFAULT_STORAGE_POOL_SIZE = ControllerUtils.convertBytesToKBytes("10995116277760"); //10 TB in Kilo Bytes
    static final Integer timeout = 10000;           // in milliseconds
    static final Integer connectTimeout = 10000;    // in milliseconds

    private CinderApiFactory _cinderApiFactory;
    private CinderEndPointInfo endPointInfo = null;

    public void setCinderApiFactory(CinderApiFactory cinderApiFactory)
    {
        _cinderApiFactory = cinderApiFactory;
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        // Do Nothing - It will be implemented later
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Process the cinder.conf file and deduce storage systems from it
     */
    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        _logger.info("Scanning started for provider: {}", accessProfile.getSystemId());
        StorageProvider storageProvider = _dbClient.queryObject(StorageProvider.class, accessProfile.getSystemId());
        String username = storageProvider.getUserName();
        String password = storageProvider.getPassword();
        String hostName = storageProvider.getIPAddress();
        // map to hold cinder end point info
        StringMap providerKeys = storageProvider.getKeys();
        if (providerKeys == null)
        {
            providerKeys = new StringMap();
        }
        updateKeyInProvider(providerKeys, CinderConstants.KEY_CINDER_HOST_NAME, hostName);
        Integer portNumber = storageProvider.getPortNumber();
        ArrayList<Section> sections = new ArrayList<Section>();
        String volume_driver = "";
        String auth_strategy = "unknown";

        ChannelSftp sftp = null;
        Session session = null;

        try
        {
            JSch jsch = new JSch();
            session = jsch.getSession(username, hostName, portNumber);
            session.setPassword(password);
            Hashtable<String, String> config = new Hashtable<String, String>();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(timeout);
            _logger.debug("Session Connected...");
            Channel channel = session.openChannel("sftp");
            sftp = (ChannelSftp) channel;
            InputStream ins;
            sftp.connect(connectTimeout);
            if (sftp.isConnected()) {
                _logger.debug("SFTP Connected");

                ins = sftp.get(CONFFILE);
                BufferedReader b = new BufferedReader(new InputStreamReader(ins));

                int next_section_index = 0;
                String section_title = "";
                Boolean auth_section = false;
                while (!sftp.isEOF())
                {
                    String line = b.readLine();
                    if (line == null) {
                        _logger.debug("End of buffer -- break");
                        break;
                    }
                    if (isComment(line)) {  // skip comments
                        continue;
                    }
                    if (isSection(line)) {  // start new section
                        section_title = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
                        Section section = new Section();
                        section.index = next_section_index;
                        section.title = section_title;
                        sections.add(section);
                        next_section_index++;
                        _logger.debug("Section {}: Title: {}", section.index, section.title);
                        auth_section = section_title.startsWith(auth_strategy);
                        continue;
                    }
                    if (!line.contains("=")) {   // not a value-parameter pair
                        continue;
                    }
                    // Now process each line
                    String[] splits = line.split("=");
                    if (splits.length == 2) {
                        String parameter = splits[0].trim();
                        String value = splits[1].trim();
                        if (auth_section) {
                            if (parameter.equalsIgnoreCase("admin_user") || 
                                    parameter.equalsIgnoreCase("username")) {
                                updateKeyInProvider(providerKeys,
                                        CinderConstants.KEY_CINDER_REST_USER, value);
                                _logger.debug("REST user name = {}", value);
                            }
                            else if (parameter.equalsIgnoreCase("admin_password") ||
                                    parameter.equalsIgnoreCase("password")) {
                                updateKeyInProvider(providerKeys,
                                        CinderConstants.KEY_CINDER_REST_PASSWORD, value);
                                _logger.debug("REST password = {}", value);
                            }
                            else if (parameter.equalsIgnoreCase("admin_tenant_name") ||
                                    parameter.equalsIgnoreCase("project_name")) {
                                updateKeyInProvider(providerKeys,
                                        CinderConstants.KEY_CINDER_TENANT_NAME, value);
                                _logger.debug("Tenant name = {}", value);
                            }
                            else if (parameter.equalsIgnoreCase("auth_uri")) {
                                updateKeyInProvider(providerKeys,
                                        CinderConstants.KEY_CINDER_REST_URI_BASE, value);
                                _logger.info("REST uri = {}", value);
                            }
                        }
                        else {
                            // this is a storage section
                            _logger.debug("Storage section: parameter = {},  value = {}", parameter, value);
                            if (parameter.equalsIgnoreCase("auth_strategy")) {
                                auth_strategy = value.trim();
                                _logger.info("Auth strategy = {}", auth_strategy);
                            }
                            else if (parameter.equalsIgnoreCase("volume_driver")) {
                                volume_driver = value.trim();
                                sections.get(next_section_index - 1).volume_driver = volume_driver;
                                _logger.debug("Volume driver = {}", volume_driver);
                            }
                            else if (parameter.equalsIgnoreCase("volume_backend_name")) {
                                String volume_backend_name = value.trim();
                                _logger.debug("Volume backend_name = {}", volume_backend_name);
                                sections.get(next_section_index - 1).volume_backend_name = volume_backend_name;
                            }
                        }
                    } /* if splits.length */

                } /* while not EOF */

                b.close();
            } /* if sftp is connected */
            storageProvider.setConnectionStatus(ConnectionStatus.CONNECTED.name());
        } /* try */
        catch (Exception e) {
            // exceptionOccured = true;
            storageProvider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
            _logger.error("Exception occurred while scanning provider {}",
                    accessProfile.getSystemId(), e);
        } finally
        {
            fillStorageSystemCache(accessProfile, sections);
            if (storageProvider.getKeys() == null)
            { /* first time scan */
                storageProvider.setKeys(providerKeys);
            }
            _dbClient.persistObject(storageProvider);
            if (sftp != null) {
                sftp.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
        _logger.info("Scanning ended for provider: {}", accessProfile.getSystemId());
    }

    private void fillStorageSystemCache(AccessProfile accessProfile, ArrayList<Section> sections) {
        Map<String, StorageSystemViewObject> storageSystemsCache = accessProfile.getCache();

        // Analyze each section, and update the cache if a new backend is found
        for (Section section : sections) {
            if (section.volume_driver.isEmpty()) {
                continue;
            }
            String systemType = StorageSystem.Type.openstack.name();
            String model = "";
            String driverName = section.volume_driver;
            String label = section.volume_backend_name;
            if (label.isEmpty()) {   // use section title instead
                label = section.title;
            }
            // If it is the default section and label doesn't have Default tag, then add Default tag in label
            if (section.title.equalsIgnoreCase(CinderConstants.DEFAULT) &&
                    !label.toUpperCase().contains(CinderConstants.DEFAULT)) {
                label = String.format("%s_%s", CinderConstants.DEFAULT, label);
            }
            String vendor = "";

            String[] driver_splits = section.volume_driver.split("\\.");
            if (driver_splits.length > 3) {
                vendor = driver_splits[3];
                if (driver_splits.length > 4) {
                    model = driver_splits[4];
                }
                if (driver_splits.length > 5) {
                    driverName = driver_splits[5];
                }
            }

            // truncate the driver name if it is lengthy, since label length cannot be >64.
            if (driverName.contains(".")) {
                driverName = driverName.substring(driverName.lastIndexOf("."));
            }
            StorageSystemViewObject systemVO = null;
            label = String.format("%s_%s", label, driverName);
            String serialNumber = generateSerialNumber(label + accessProfile.getIpAddress());
            String nativeGuid = String.format("%s+%s", label, serialNumber);
            if (storageSystemsCache.containsKey(nativeGuid)) {
                systemVO = storageSystemsCache.get(nativeGuid);
                _logger.info("Updating existing Storage System: label = {}, model = {},", label, model);
                _logger.info(" type = {}, serial Number generated = {}", systemType, serialNumber);
            } else {
                systemVO = new StorageSystemViewObject();
                _logger.info("Adding new Storage System: label = {}, model = {},", label, model);
                _logger.info(" type = {}, serial Number generated = {}", systemType, serialNumber);
            }
            systemVO.setDeviceType(systemType);
            systemVO.addprovider(accessProfile.getSystemId()
                    .toString());
            systemVO.setProperty(StorageSystemViewObject.MODEL, model);
            systemVO.setProperty(StorageSystemViewObject.SERIAL_NUMBER, serialNumber);
            systemVO.setProperty(StorageSystemViewObject.STORAGE_NAME, nativeGuid);

            storageSystemsCache.put(nativeGuid, systemVO);
        }
        _logger.info("Found {} system(s) during scanning for ip {}",
                storageSystemsCache.size(), accessProfile.getIpAddress());
    }

    /**
     * Generate serial number which will be of 11 digits.
     * 
     * @param string (label + Driver name + Provider IP address/FQDN)
     * @return serial number
     */
    private String generateSerialNumber(String str) {
        int value = str.hashCode();
        String serialNumber = null;
        /**
         * The String which we generate is of 11 digits.
         * The first digit is either 0 or 1. Remaining 10 digits are generated from hashCode()
         * - add 0 at the beginning when value is +ve
         * - add 1 at the beginning when value is -ve
         * (to differentiate it from value which would obtain in +ve case)
         */
        if (value < 0) {
            /** when the generated number is negative, it is an overflown value of int max size(2147483647) */
            value = -value;
            serialNumber = String.format("%010d", value);
            serialNumber = "1" + serialNumber;
        } else {
            serialNumber = String.format("%011d", value);
        }
        return serialNumber;
    }

    /**
     * Get volume types, and create a storage pool for each volume type
     */
    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                        .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES
                                .toString()))) {
            discoverUnManagedVolumes(accessProfile);
        } else {
            _logger.info("Discovery started for system {}", accessProfile.getSystemId());

            List<StoragePool> newPools = new ArrayList<StoragePool>();
            List<StoragePool> updatePools = new ArrayList<StoragePool>();
            List<StoragePool> allPools = new ArrayList<StoragePool>();

            String token = "";
            StorageSystem system = null;
            StorageProvider provider = null;

            String detailedStatusMessage = "Unknown Status";
            try
            {
                String hostName = null;
                String restuserName = null;
                String restPassword = null;
                String restBaseUri = null;
                String tenantName = null;
                String oldToken = null;
                String tenantId = null;

                system = _dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
                system.setReachableStatus(true);

                // first add storage ports if necessary
                addPorts(system);

                // now do the pool discovery
                URI providerUri = system.getActiveProviderURI();
                provider = _dbClient.queryObject(StorageProvider.class, providerUri);
                if (null != provider.getKeys())
                {
                    StringMap providerKeys = provider.getKeys();
                    oldToken = providerKeys.get(CinderConstants.KEY_CINDER_REST_TOKEN);
                    hostName = providerKeys.get(CinderConstants.KEY_CINDER_HOST_NAME);
                    restuserName = providerKeys.get(CinderConstants.KEY_CINDER_REST_USER);
                    restPassword = providerKeys.get(CinderConstants.KEY_CINDER_REST_PASSWORD);
                    restBaseUri = providerKeys.get(CinderConstants.KEY_CINDER_REST_URI_BASE);
                    tenantName = providerKeys.get(CinderConstants.KEY_CINDER_TENANT_NAME);
                    tenantId = providerKeys.get(CinderConstants.KEY_CINDER_TENANT_ID);
                }

                if (null == endPointInfo)
                {
                    endPointInfo = new CinderEndPointInfo(hostName, restuserName, restPassword, tenantName);
                    if (restBaseUri.startsWith(CinderConstants.HTTP_URL))
                    {
                        endPointInfo.setCinderBaseUriHttp(restBaseUri);
                    }
                    else
                    {
                        endPointInfo.setCinderBaseUriHttps(restBaseUri);
                    }

                    // Always set the token and tenant id, when new instance is created
                    endPointInfo.setCinderToken(oldToken);
                    endPointInfo.setCinderTenantId(tenantId);
                }

                CinderApi api = _cinderApiFactory.getApi(providerUri, endPointInfo);
                _logger.debug("discover : Got the cinder api factory for provider with id: {}", providerUri);

                // check if the cinder is authenticated and if the token is valid
                if (null == oldToken || (isTokenExpired(oldToken)))
                {
                    // This means, authentication is required, go and fetch the token
                    token = api.getAuthToken(restBaseUri + "/tokens");

                    if (null != token)
                    {
                        _logger.debug("Got new token : {}", token);
                        // update the token in the provider
                        provider.addKey(CinderConstants.KEY_CINDER_REST_TOKEN, token);
                        provider.addKey(CinderConstants.KEY_CINDER_TENANT_ID, endPointInfo.getCinderTenantId());
                    }

                }
                else
                {
                    token = oldToken;
                    _logger.debug("Using the old token : {}", token);
                }

                if (token.length() > 1)
                {
                    // Now get the number of volume types
                    VolumeTypes types = api.getVolumeTypes();
                    if (types != null)
                    {
                        _logger.info("Got {} Volume Type(s)", types.volume_types.length);
                        boolean isDefaultStoragePoolCreated = false;
                        for (int i = 0; i < types.volume_types.length; i++)
                        {
                            boolean isNew = false;
                            String poolName = types.volume_types[i].name;
                            String nativeGuid = types.volume_types[i].id;
                            _logger.info("Storage Pool name = {}, id = {}", poolName, nativeGuid);
                            // Now find association with storage system
                            Map<String, String> extra_specs = types.volume_types[i].extra_specs;
                            String system_title = extra_specs.get(VOLUME_BACKEND_NAME);
                            boolean isThickPool = Boolean
                                    .parseBoolean(extra_specs.get(VIPR_THICK_POOL));

                            // If no volume backend name, use default
                            if (system_title == null)
                            {
                                system_title = CinderConstants.DEFAULT;
                            }

                            if (system.getNativeGuid().toUpperCase().contains(system_title.toUpperCase()))
                            {
                                // Check if volume type belongs to the default storage system
                                if (system.getNativeGuid().toUpperCase().startsWith(CinderConstants.DEFAULT)) {
                                    isDefaultStoragePoolCreated = true;
                                }
                                // do the association
                                _logger.info("Found association between system {} and pool {}", system_title, poolName);
                                StoragePool pool = checkPoolExistsInDB(nativeGuid);

                                if (null == pool)
                                {
                                    isNew = true;
                                    pool = createPoolforStorageSystem(system, nativeGuid, poolName, isThickPool);
                                    newPools.add(pool);
                                }

                                pool.setPoolName(poolName);
                                pool.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                                pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                                if (!isNew)
                                {
                                    updatePools.add(pool);
                                }
                            }
                            else
                            {
                                _logger.info("Pool {} doesn't belong to storage system {}", poolName, system.getLabel());
                            }
                        } /* for */

                        // If it is a default storage system and volume type is not created in cinder,
                        // create default storage pool for storage system
                        if (system.getNativeGuid().toUpperCase().startsWith(CinderConstants.DEFAULT)
                                && !isDefaultStoragePoolCreated) {
                            _logger.debug("Creating defual pool for default storage system");
                            String nativeGuid = "DefaultPool";
                            StoragePool pool = checkPoolExistsInDB(nativeGuid);
                            String poolName = "DefaultPool";
                            if (null != pool) {
                                updatePools.add(pool);
                            } else {
                                pool = createPoolforStorageSystem(system, nativeGuid, poolName, false);
                                newPools.add(pool);
                            }
                            pool.setPoolName(poolName);
                            pool.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                            pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                        }

                        StoragePoolAssociationHelper.setStoragePoolVarrays(system.getId(), newPools, _dbClient);
                        allPools.addAll(newPools);
                        allPools.addAll(updatePools);
                        ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVpool(allPools,
                                _dbClient, _coordinator, accessProfile.getSystemId());
                        _logger.info("New pools size: {}", newPools.size());
                        _logger.info("updatePools size: {}", updatePools.size());
                        DiscoveryUtils.checkStoragePoolsNotVisible(allPools, _dbClient, system.getId());
                        _dbClient.createObject(newPools);
                        _dbClient.persistObject(updatePools);

                        // discovery succeeds
                        detailedStatusMessage = String.format("Discovery completed successfully for OpenStack: %s",
                                accessProfile.getSystemId());
                    } /* if types */
                    else
                    {
                        _logger.error("Error in getting volume types from cinder");
                    }
                } /* if token length */
                else
                {
                    _logger.error("Error in getting token from keystone");
                }

            } catch (Exception e)
            {

                if (null != system)
                {
                    cleanupDiscovery(system);
                }
                detailedStatusMessage = String.format(
                        "Discovery failed for Storage System: %s because %s",
                        system.toString(), e.getLocalizedMessage());
                _logger.error(detailedStatusMessage, e);

                throw new CinderColletionException(false,
                        ServiceCode.DISCOVERY_ERROR,
                        null, detailedStatusMessage, null, null);

            } finally
            {
                try
                {
                    if (system != null)
                    {
                        system.setLastDiscoveryStatusMessage(detailedStatusMessage);
                        _dbClient.persistObject(system);
                    }

                    // persist the provider
                    if (null != provider)
                    {
                        _dbClient.persistObject(provider);
                    }
                } catch (DatabaseException e)
                {
                    _logger.error(
                            "Failed to persist cinder storage system to Database, Reason: {}",
                            e.getMessage(), e);
                }
            }
            _logger.info("Discovery Ended for system {}", accessProfile.getSystemId());
        }
    }

    private StoragePool createPoolforStorageSystem(StorageSystem system,
            String nativeGuid, String poolName, boolean isThickPool) {
        _logger.debug("Creating storage pool {} for storage system {}", poolName, system);
        StoragePool pool = new StoragePool();
        pool.setNativeGuid(nativeGuid);
        pool.setStorageDevice(system.getId());
        pool.setId(URIUtil.createId(StoragePool.class));
        pool.setNativeId(nativeGuid);
        pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY
                .toString());
        pool.setPoolServiceType(PoolServiceType.block.toString());
        pool.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED
                .toString());
        StringSet protocols = new StringSet();
        String sys_nativeGuid = system.getNativeGuid().toLowerCase();
        if (sys_nativeGuid.contains("iscsi")) {
            protocols.add("iSCSI");
        }
        if (sys_nativeGuid.contains("fc")) {
            protocols.add("FC");
        }
        pool.setProtocols(protocols);

        if (isThickPool) {
            pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THICK_ONLY
                    .toString());
            pool.setMaximumThickVolumeSize(DEFAULT_STORAGE_POOL_SIZE);  // 10 TB in Kilo Bytes
        } else {
            pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_ONLY
                    .toString());
            pool.setMaximumThinVolumeSize(DEFAULT_STORAGE_POOL_SIZE);  // 10 TB in Kilo Bytes
        }
        // UNSYNC_ASSOC -> snapshot, UNSYNC_UNASSOC -> clone
        StringSet copyTypes = new StringSet();
        copyTypes.add(StoragePool.CopyTypes.UNSYNC_ASSOC.name());
        copyTypes.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
        pool.setSupportedCopyTypes(copyTypes);
        pool.setThinVolumePreAllocationSupported(Boolean.FALSE);
        // TODO workaround to display the display name based on the pool name
        pool.setLabel(poolName);

        /*
         * TODO: Keeping the total capacity as 10 TB as there is no API/CLI
         * to know the volume type's capacity from cinder. These values
         * should be updated for actual capacity if in future cinder comes up
         * a way to know these values.
         * 
         * Further these values will be adjusted as volume/snapshot gets created/deleted
         */

        pool.setFreeCapacity(DEFAULT_STORAGE_POOL_SIZE); // 10 TB in Kilo Bytes
        pool.setTotalCapacity(DEFAULT_STORAGE_POOL_SIZE);  // 10 TB in Kilo Bytes

        return pool;
    }

    private void discoverUnManagedVolumes(AccessProfile accessProfile) {
        // Do Nothing - It will be implemented later
        _logger.info("UnManaged volume discovery is not supported. Storage System: {}",
                accessProfile.getSystemId());
    }

    /**
     * Checks if the last token fetched is expired
     * 
     * The token fetched will be valid for one hour only
     * 
     * @param oldToken
     * @return true or false
     */
    private boolean isTokenExpired(String oldToken)
    {
        // TODO : Add the implementation to check the token expiry
        return true;
    }

    /**
     * This method adds storage ports to the system if not done already
     * 
     * @throws IOException
     */
    private void addPorts(StorageSystem system) throws IOException
    {
        StorageProtocol.Transport supportedProtocol = getProtocol(system);

        _logger.info("Checking if system {} needs a new storage port", system.getLabel());
        if (supportedProtocol == null) {
            return;
        }

        _logger.info("System {} supports protocol {}",
                system.getLabel(), supportedProtocol.name().toString());
        boolean isPresent = false;
        List<StoragePort> allPorts = new ArrayList<StoragePort>();
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        URI sysid = system.getId();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(sysid),
                storagePortURIs);
        Iterator<URI> storagePortsIter = storagePortURIs.iterator();
        while (storagePortsIter.hasNext())
        {
            URI storagePortURI = storagePortsIter.next();
            StoragePort storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);
            if (storagePort != null && !storagePort.getInactive() &&
                    (storagePort.getTransportType().equalsIgnoreCase(
                            supportedProtocol.name().toString())))
            {
                _logger.info("System {} already has port for protocol {}",
                        system.getLabel(), supportedProtocol.name().toString());
                isPresent = true;
                if (storagePort.getPortNetworkId() == null)
                {
                    // update it -- this needs to change
                    storagePort.setPortNetworkId(storagePort.getNativeGuid());
                    _dbClient.persistObject(storagePort);
                }
                allPorts.add(storagePort);
            }
        } // while
        if (!isPresent)
        {
            StorageHADomain adapter = CinderUtils.getStorageAdapter(system, _dbClient);

            // Now we need to add the port to the system
            _logger.info("Adding new storage port of type {} to storage system {}",
                    supportedProtocol.name().toString(), system.getLabel());
            StoragePort port = new StoragePort();
            port.setId(URIUtil.createId(StoragePort.class));
            port.setStorageDevice(sysid);
            // port name is "DEFAULT" since we don't know how to name it
            String portName = CinderConstants.DEFAULT;
            String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system,
                    portName, NativeGUIDGenerator.PORT);
            port.setNativeGuid(nativeGuid);
            port.setPortNetworkId(nativeGuid);

            port.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED
                    .toString());
            // always treat it as a frontend port
            port.setPortType(PortType.frontend.name());
            port.setOperationalStatus(OperationalStatus.OK.toString());
            port.setTransportType(supportedProtocol.name().toString());
            port.setLabel(portName);
            port.setPortName(portName);
            port.setStorageHADomain(adapter.getId());
            port.setPortGroup(CinderConstants.CINDER_PORT_GROUP);
            port.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
            port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
            _dbClient.createObject(port);
            allPorts.add(port);
        }

        StoragePortAssociationHelper.updatePortAssociations(allPorts, _dbClient);
        DiscoveryUtils.checkStoragePortsNotVisible(allPorts, _dbClient, sysid);
    }

    /**
     * Deduce the supported protocol from the storage system native ID
     * Note: this method may be prone to error
     * But we don't have any other reliable API from OpenStack for this
     */
    private StorageProtocol.Transport getProtocol(StorageSystem system)
    {
        String nativeGuid = system.getNativeGuid().toLowerCase();
        if (nativeGuid.contains("iscsi")) // name contains iscsi
        {
            return StorageProtocol.Transport.IP;
        }

        if (nativeGuid.contains("fc")) // name contains FC
        {
            return StorageProtocol.Transport.FC;
        }

        if (nativeGuid.contains("nfs"))    // probably a file type -- do not use this
        {
            return null;
        }

        return StorageProtocol.Transport.IP;   // default assume iscsi
    }

    /**
     * If discovery fails, then mark the system as unreachable. The discovery
     * framework will remove the storage system from the database.
     * 
     * @param system
     *            the system that failed discovery.
     */
    private void cleanupDiscovery(StorageSystem system)
    {
        try
        {
            system.setReachableStatus(false);
            _dbClient.persistObject(system);
        } catch (DatabaseException e)
        {
            _logger.error("discoverStorage failed. Failed to update discovery status to ERROR.", e);
        }

    }

    /**
     * Check if Pool exists in DB.
     * 
     * @param poolInstance
     * @param _dbClient
     * @param profile
     * @return
     * @throws IOException
     */
    protected StoragePool checkPoolExistsInDB(String nativeGuid) throws IOException
    {
        StoragePool pool = null;
        URIQueryResultList queryResult = new URIQueryResultList();
        // use NativeGuid to lookup Pools in DB
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePoolByNativeGuidConstraint(nativeGuid), queryResult);
        while (queryResult.iterator().hasNext())
        {
            URI poolURI = queryResult.iterator().next();
            if (null != poolURI)
            {
                StoragePool poolInDB = _dbClient.queryObject(StoragePool.class, poolURI);
                if (!poolInDB.getInactive()) {
                    pool = poolInDB;
                    break;
                }
            }
        }
        return pool;
    }

    private boolean isComment(String line)
    {
        return (line.trim().startsWith("#"));
    }

    private boolean isSection(String line)
    {
        return (line.trim().startsWith("["));
    }

    private void updateKeyInProvider(StringMap providerKeys, String key, String value)
    {
        if (!providerKeys.containsKey(key) || !providerKeys.get(key).equals(value))
        {
            providerKeys.put(key, value);
        }
    }
}
