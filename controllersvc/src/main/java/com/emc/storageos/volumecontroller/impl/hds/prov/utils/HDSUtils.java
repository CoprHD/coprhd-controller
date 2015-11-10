/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.model.EchoCommand;
import com.emc.storageos.hds.model.Error;
import com.emc.storageos.hds.model.HostStorageDomain;
import com.emc.storageos.hds.model.ISCSIName;
import com.emc.storageos.hds.model.Pool;
import com.emc.storageos.hds.model.WorldWideName;
import com.emc.storageos.plugins.AccessProfile;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class HDSUtils {

    private static final Logger log = LoggerFactory.getLogger(HDSUtils.class);

    public static void updateStoragePoolCapacity(DbClient dbClient, HDSApiClient client, StoragePool storagePool) {
        StorageSystem storageSystem = null;

        try {
            storageSystem = dbClient.queryObject(StorageSystem.class, storagePool.getStorageDevice());
            log.info(String.format("Old storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));

            String poolObjectId = getPoolObjectID(storagePool);
            String systemObjectId = getSystemObjectID(storageSystem);

            Pool hdsStoragePool = client.getStoragePoolInfo(systemObjectId, poolObjectId);

            // Update storage pool and save to data base
            storagePool.setFreeCapacity(hdsStoragePool.getFreeCapacity());
            // @TODO need to see how to get subscribed capacity of the hds pool
            // storagePool.setSubscribedCapacity(ControllerUtils.convertBytesToKBytes(subscribedCapacity));

            log.info(String.format("New storage pool capacity data for pool %n  %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.getFreeCapacity(),
                    storagePool.getSubscribedCapacity()));

            dbClient.persistObject(storagePool);
        } catch (Exception ex) {
            log.error(
                    String.format(
                            "Failed to update capacity of storage pool after volume provisioning operation. %n  Storage system: %s, storage pool %s .",
                            storageSystem.getId(), storagePool.getId()), ex);
        }

    }

    /**
     * Return pool objectID from Pool NativeGuid.
     * 
     * @param storagePool
     * @return
     */
    public static String getPoolObjectID(StoragePool storagePool) {
        Iterable<String> splitter = Splitter.on(HDSConstants.PLUS_OPERATOR).limit(4)
                .split(storagePool.getNativeGuid());
        return Iterables.getLast(splitter);
    }

    /**
     * Return port objectID from Port NativeGuid.
     * 
     * @param storagePort
     * @return
     */
    public static String getPortObjectID(StoragePort storagePort) {
        Iterable<String> splitter = Splitter.on(HDSConstants.PLUS_OPERATOR).limit(4)
                .split(storagePort.getNativeGuid());
        return Iterables.getLast(splitter);
    }

    /**
     * Return portid from Port NativeGuid.
     * 
     * @param storagePort
     * @return
     */
    public static String getPortID(StoragePort storagePort) {
        Iterable<String> splitter = Splitter.on(HDSConstants.DOT_OPERATOR).limit(4)
                .split(storagePort.getNativeGuid());
        return Iterables.getLast(splitter);
    }

    /**
     * Generates the HiCommand Device Manager Server URI.
     * 
     * Sample HDS mgmt server: http://lglak148:2001/service/StorageManager
     * 
     * @param system : Storage System details
     * @return : URI of the Device Manager.
     * @throws URISyntaxException
     */
    public static URI getHDSServerManagementServerInfo(StorageSystem system) throws URISyntaxException {

        String protocol = HDSConstants.HTTP_URL;
        if (Boolean.TRUE.equals(system.getSmisUseSSL())) {
            protocol = HDSConstants.HTTPS_URL;
        }

        String ipAddress = system.getSmisProviderIP();
        int portNumber = system.getSmisPortNumber();
        URI uri = new URI(protocol, null, ipAddress, portNumber, HDSConstants.HDS_DM_MGMT_URL_PATH, null, null);
        return uri;
    }

    /**
     * Generates the HiCommand Device Manager Server URI.
     * 
     * Sample HDS mgmt server: http://lglak148:2001/service/StorageManager
     * 
     * @param system : Storage System details
     * @return : URI of the Device Manager.
     * @throws URISyntaxException
     */
    public static URI getHDSServerManagementServerInfo(StorageProvider storageProvider) throws URISyntaxException {

        String protocol = HDSConstants.HTTP_URL;
        if (Boolean.TRUE.equals(storageProvider.getUseSSL())) {
            protocol = HDSConstants.HTTPS_URL;
        }

        String ipAddress = storageProvider.getIPAddress();
        int portNumber = storageProvider.getPortNumber();
        URI uri = new URI(protocol, null, ipAddress, portNumber, HDSConstants.HDS_DM_MGMT_URL_PATH, null, null);
        log.debug("HiCommand DM server url to query: {}", uri);
        return uri;
    }

    /**
     * Generates the HiCommand Device Manager Server URI.
     * 
     * Sample HDS mgmt server: http://lglak148:2001/service/StorageManager
     * 
     * @param system : Storage System details
     * @return : URI of the Device Manager.
     * @throws URISyntaxException
     */
    public static URI getHDSServerManagementServerInfo(AccessProfile accessProfile) throws URISyntaxException {

        String protocol = HDSConstants.HTTP_URL;
        if (Boolean.parseBoolean(accessProfile.getSslEnable())) {
            protocol = HDSConstants.HTTPS_URL;
        }

        String ipAddress = accessProfile.getIpAddress();
        int portNumber = accessProfile.getPortNumber();
        URI uri = new URI(protocol, null, ipAddress, portNumber, HDSConstants.HDS_DM_MGMT_URL_PATH, null, null);
        return uri;
    }

    /**
     * Return the storage system objectID.
     * 
     * @param system
     * @return
     */
    public static String getSystemObjectID(StorageSystem system) {
        Iterable<String> systemSplitter = Splitter.on(HDSConstants.PLUS_OPERATOR).limit(2).split(system.getNativeGuid());
        return Iterables.getLast(systemSplitter);
    }

    public static String getSystemModelSerialNum(StorageSystem system) {
        Iterable<String> systemSplitter = Splitter.on(HDSConstants.DOT_OPERATOR).limit(2).split(system.getNativeGuid());
        return Iterables.getLast(systemSplitter);
    }

    public static String getSystemArrayType(StorageSystem storageSystem) {
        return (storageSystem.getNativeGuid().split("\\."))[1];
    }

    public static String getSystemSerialNumber(StorageSystem system) {
        Iterable<String> systemSplitter = Splitter.on(HDSConstants.DOT_OPERATOR).limit(4).split(system.getNativeGuid());
        return Iterables.getLast(systemSplitter);
    }

    /**
     * makes simple rest call to device manager to validate the provider reachable state for passed hds providers
     * 
     * @param hicommandProviderList List of HiCommandDevice Manager provider URIs
     * @param dbClient
     * @param hdsApiFactory
     * @return List of Active Storage Providers
     */
    public static List<URI> refreshHDSConnections(final List<StorageProvider> hicommandProviderList,
            DbClient dbClient, HDSApiFactory hdsApiFactory) {
        List<URI> activeProviders = new ArrayList<URI>();
        for (StorageProvider storageProvider : hicommandProviderList) {
            try {
                HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                        HDSUtils.getHDSServerManagementServerInfo(storageProvider),
                        storageProvider.getUserName(), storageProvider.getPassword());
                // Makes sure "Hi Command Device manager" is reachable
                hdsApiClient.getStorageSystemsInfo();
                storageProvider.setConnectionStatus(ConnectionStatus.CONNECTED.name());
                activeProviders.add(storageProvider.getId());
                log.info("Storage Provider {} is reachable", storageProvider.getIPAddress());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                storageProvider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
                log.error("Storage Provider {} is not reachable", storageProvider.getIPAddress());
            } finally {
                dbClient.persistObject(storageProvider);
            }
        }
        return activeProviders;
    }

    /**
     * Generate LogicalUnit ObjectId from volume nativeId & system.
     * Ex. LU.HM700.211643.87
     * 
     * @param nativeId
     * @return
     */
    public static String getLogicalUnitObjectId(String nativeId, StorageSystem system) {
        StringBuffer luObjectId = new StringBuffer(HDSConstants.LU)
                .append(HDSConstants.DOT_OPERATOR);
        luObjectId.append(getSystemModelSerialNum(system))
                .append(HDSConstants.DOT_OPERATOR).append(nativeId);
        return luObjectId.toString();
    }

    /**
     * Generates the WWN of the volume manually.
     * This is a partial WWN which is a combination of serialNum + volumeId (in Hex format.)
     * 
     * @param luObjectID
     * @param volumeNativeId
     * @return
     */
    public static String generateHitachiWWN(String luObjectID, String volumeNativeId) {
        String arraySerialNum = luObjectID.split("\\.")[2];
        String arraySerialNumInHex = null;
        if (luObjectID.contains(HDSConstants.HUSVM_MODEL)) {
            // for HUS VM, we should ignore the first digit.
            arraySerialNumInHex = Integer.toHexString(Integer.parseInt(arraySerialNum
                    .substring(1)));
        }
        if (luObjectID.contains(HDSConstants.VSP_G1000_MODEL)) {
            // for VSP G100,
            arraySerialNumInHex = Integer.toHexString(Integer.parseInt(arraySerialNum));
        } else {
            arraySerialNumInHex = String.format("%08x", Integer
                    .parseInt(arraySerialNum));
        }
        StringBuffer generatedWWN = new StringBuffer(arraySerialNumInHex);
        String volumeIdInHexa = String.format("%08x", Integer
                .parseInt(volumeNativeId));
        generatedWWN.append(volumeIdInHexa);
        return generatedWWN.toString().toUpperCase();
    }

    /**
     * Generates the WWN of the volume manually for a given system and volume id.
     * This is a partial WWN which is a combination of serialNum + volumeId (in Hex format.)
     * 
     * @param luObjectID
     * @param volumeNativeId
     * @return
     */
    public static String generateHitachiVolumeWWN(StorageSystem storage, String volumeNativeId) {
        String arraySerialNumInHex = null;
        String serialNum = storage.getSerialNumber();
        if (storage.getModel().equalsIgnoreCase(HDSConstants.HUSVM_ARRAYFAMILY_MODEL)) {
            // for HUS VM, we should ignore the first digit.
            arraySerialNumInHex = Integer.toHexString(Integer.parseInt(serialNum
                    .substring(1)));
        } else {
            arraySerialNumInHex = String.format("%08x", Integer
                    .parseInt(serialNum));
        }
        StringBuffer generatedWWN = new StringBuffer(arraySerialNumInHex);
        String volumeIdInHexa = String.format("%08x", Integer
                .parseInt(volumeNativeId));
        generatedWWN.append(volumeIdInHexa);
        return generatedWWN.toString().toUpperCase();
    }

    /**
     * Utility method to check if there are any errors or not.
     * 
     * @param javaResult
     * @throws Exception
     */
    public static void verifyErrorPayload(JavaResult javaResult) throws Exception {
        EchoCommand command = javaResult.getBean(EchoCommand.class);
        if (null == command || null == command.getStatus()
                || HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
            Error error = javaResult.getBean(Error.class);
            // log.info("Error response received from Hitachi server for messageID", command.getMessageID());
            log.info("Hitachi command failed with error code:{} with message:{} for request:{}",
                    new Object[] { error.getCode().toString(), error.getDescription(), error.getSource() });
            throw HDSException.exceptions.errorResponseReceived(
                    error.getCode(), error.getDescription());
        }
    }

    public static boolean isVolumeModifyApplicable(URI volumeURI, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, volumeURI);
        return volume.getThinlyProvisioned() && null != volume.getAutoTieringPolicyUri();
    }

    public static HDSApiClient getHDSApiClient(HDSApiFactory apiFactory, StorageSystem storageSystem) throws URISyntaxException {
        HDSApiClient apiClient = apiFactory.getClient(
                HDSUtils.getHDSServerManagementServerInfo(storageSystem), storageSystem.getSmisUserName(),
                storageSystem.getSmisPassword());
        ;
        return apiClient;

    }

    /**
     * Check whether the given storageSystem is AMS series model or not.
     * 
     * @param storageSystem
     * @return
     */
    public static boolean checkForAMSSeries(StorageSystem storageSystem) {
        return (storageSystem.getModel() != null && storageSystem.getModel().startsWith(HDSConstants.AMS_SERIES_MODEL));
    }

    /**
     * Check whether the given storageSystem is HUSXXX model or not.
     * 
     * @param storageSystem
     * @return
     */
    public static boolean checkForHUSSeries(StorageSystem storageSystem) {
        return (storageSystem.getModel() != null
                && storageSystem.getModel().startsWith(HDSConstants.HUS_SERIES_MODEL)
                && !storageSystem.getModel().equalsIgnoreCase(HDSConstants.HUSVM_MODEL));
    }

    /**
     * Return the IPAddress from a given input string.
     * 
     * @param name
     * @param limit
     * @return
     */
    public static String extractIpAddress(String name, int limit,
            String delimiter) {
        Iterable<String> splitter = Splitter.on(delimiter).limit(limit)
                .split(name);
        return Iterables.getLast(splitter);
    }

    /**
     * Return a list of ISCSINames for the given list of initiatorName's.
     * 
     * @return List of ISCSINames
     */
    public static Function<String, ISCSIName> fctnPortNameToISCSIName() {
        return new Function<String, ISCSIName>() {

            @Override
            public ISCSIName apply(String portName) {
                return new ISCSIName(portName, null);
            }
        };
    }

    /**
     * Return WorldWideName for the given portWWN string.
     * This utility will be used google collections2 framework
     * to convert List of port WWN's to list of WorldWideName objects.
     * 
     * @return WorldWideName.
     */
    public static Function<String, WorldWideName> fctnPortWWNToWorldWideName() {
        return new Function<String, WorldWideName>() {

            @Override
            public WorldWideName apply(String portWWN) {
                return new WorldWideName(portWWN);
            }
        };
    }

    /**
     * Return a nickName for the given HSD object.
     * This utility is used by google collections2 framework
     * to convert List of HSD objects to a List of HSD nicknames.
     * 
     * @return HSD nickName.
     */
    public static Function<HostStorageDomain, String> fctnHSDToNickName() {
        return new Function<HostStorageDomain, String>() {

            @Override
            public String apply(HostStorageDomain hsd) {
                return hsd.getNickname();
            }
        };
    }

}
