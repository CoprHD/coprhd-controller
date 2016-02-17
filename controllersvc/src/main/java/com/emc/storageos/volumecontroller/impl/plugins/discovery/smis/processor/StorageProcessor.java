/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.EnumerateResponse;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Splitter;

public abstract class StorageProcessor extends PoolProcessor {
    private Logger _logger = LoggerFactory.getLogger(StorageProcessor.class);
    protected List<Object> _args;
    protected static final String EMC_IS_MAPPED = "EMCSVIsMapped";
    protected static final String EMC_RAID_LEVEL = "SSElementName";
    protected static final String EMC_RECOVERPOINT_ENABLED = "EMCSVRecoverPointEnabled";
    protected static final String EMC_THINLY_PROVISIONED = "EMCSVThinlyProvisioned";
    protected static final String EMC_IS_META_VOLUME = "EMCSVIsComposite";
    protected static final String EMC_DATA_FORMAT = "EMCSVDataFormat";
    protected static final String POLICYRULENAME = "PolicyRuleName";
    protected static final String EMC_DISK_TECHNOLOGY = "DiskTechnology";
    protected static final String EMC_SYSTEM_TYPE = "SystemType";
    protected static final String EMC_SPACE_CONSUMED = "EMCSpaceConsumed";
    protected static final String SPACE_CONSUMED = "SpaceConsumed";
    protected static final String EMC_ALLOCATED_CAPACITY = "AFSPSpaceConsumed";
    protected static final String EMC_PROVISIONEDCAPACITY = "ProvisionedCapacity";
    protected static final String EMC_TOTAL_CAPACITY = "TotalCapacity";
    protected static final String STORAGE_POOL_URI = "poolUri";
    protected static final String EMC_WWN = "EMCSVWWN";
    protected static final String SNAP_SHOT = "Snapshot";
    protected static final String SVCREATIONCLASSNAME = "SVCreationClassName";
    protected static final String SVSYSTEMCREATIONCLASSNAME = "SVSystemCreationClassName";
    protected static final String SVSYSTEMNAME = "SVSystemName";
    protected static final String SYSTEM_NAME = "SystemName";
    protected static final String SVDEVICEID = "SVDeviceID";
    protected static final String DEVICE_ID = "DeviceID";
    private static final String EMC_NATIVE_GUID = "NativeGuid";
    protected static final String EMC_NATIVE_ID = "SVDeviceID";
    protected static final String PORTID = "PermanentAddress";
    protected static final String EMC_IS_BOUND = "EMCSVIsBound";
    protected static int BATCH_SIZE = 100;

    private static final String SPACE_STR = " ";
    private static final String SLO = "SLO";
    private static final String WORKLOAD = "Workload";

    protected static final String USGAE_UNRESTRICTED = "2";
    protected static final String USAGE_DELTA_REPLICA_TARGET = "12";
    protected static final String USGAE_LOCAL_REPLICA_SOURCE = "6";
    protected static final String USAGE_LOCAL_REPLICA_TARGET = "8";
    protected static final String USAGE_LOCAL_REPLICA_SOURCE_OR_TARGET = "10";

    protected static final String SYNC_TYPE_MIRROR = "6";
    protected static final String SYNC_TYPE_SNAPSHOT = "7";
    protected static final String SYNC_TYPE_CLONE = "8";

    protected static final String SYMM_CLASS_PREFIX = "Symm_";

    protected static final String VOLUME = "Volume";
    protected static final String BLOCK_SNAPSHOT = "BlockSnapshot";
    protected static final String BLOCK_MIRROR = "BlockMirror";

    protected static final String TWELVE = "12";
    protected static final String EIGHT = "8";

    protected static final String DEPENDENT = "Dependent";

    /**
     * get UnManaged Volume Object path
     * 
     * @param path
     * @return
     */
    protected String getUnManagedVolumeNativeGuid(CIMObjectPath path, Map<String, Object> keyMap) {
        String systemName = null;
        String id = null;
        if (keyMap.containsKey(Constants.IS_NEW_SMIS_PROVIDER)
                && Boolean.valueOf(keyMap.get(Constants.IS_NEW_SMIS_PROVIDER).toString())) {
            systemName = path.getKey(SYSTEM_NAME).getValue().toString();
            systemName = systemName.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
            id = path.getKey(DEVICE_ID).getValue().toString();
        } else {
            systemName = path.getKey(SVSYSTEMNAME).getValue().toString();
            id = path.getKey(SVDEVICEID).getValue().toString();
        }
        return NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                systemName.toUpperCase(), id);
    }

    protected boolean findVolumesArefromSameArray(String sourceNativeGuid,
            String targetNativeGuid) {
        String targetSystem = targetNativeGuid.split(Constants.PATH_DELIMITER_REGEX)[1];
        if (sourceNativeGuid.contains(targetSystem)) {
            _logger.info("Source {} and target {} are from same arrays", sourceNativeGuid, targetNativeGuid);
            return true;
        }
        return false;
    }

    /**
     * get Native Guid from Volume Info Object path
     * 
     * @param path
     * @return
     */
    protected String getUnManagedVolumeNativeGuidFromVolumePath(CIMObjectPath path) {
        String systemName = path.getKey(Constants.SYSTEMNAME).getValue().toString();
        systemName = systemName.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
        String id = path.getKey(Constants.DEVICEID).getValue().toString();
        return NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                systemName.toUpperCase(), id);
    }

    /**
     * get Native Guid from Volume Info Object path
     * 
     * @param path
     * @return
     */
    protected String getVolumeViewNativeGuid(CIMObjectPath path, Map<String, Object> keyMap) {
        String systemName = null;
        String id = null;
        if (keyMap.containsKey(Constants.IS_NEW_SMIS_PROVIDER)
                && Boolean.valueOf(keyMap.get(Constants.IS_NEW_SMIS_PROVIDER).toString())) {
            systemName = path.getKey(SYSTEM_NAME).getValue().toString();
            systemName = systemName.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
            id = path.getKey(DEVICE_ID).getValue().toString();
        } else {
            systemName = path.getKey(SVSYSTEMNAME).getValue().toString();
            id = path.getKey(SVDEVICEID).getValue().toString();
        }
        // for snapshot or Volume , native Guid format is same
        return NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                systemName.toUpperCase(), id);
    }

    /**
     * get Native Guid from Volume Object path
     * 
     * @param path
     * @return
     */
    protected String getVolumeNativeGuid(CIMObjectPath path) {
        String systemName = path.getKey(Constants.SYSTEMNAME).getValue().toString();
        systemName = systemName.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
        String id = path.getKey(Constants.DEVICEID).getValue().toString();
        // for snapshot or Volume , native Guid format is same
        return NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                systemName.toUpperCase(), id);
    }

    protected URI getParentVolumeUri(CIMObjectPath sourcePath, DbClient dbClient)
            throws IOException {
        Volume volume = checkStorageVolumeExistsInDB(getVolumeNativeGuid(sourcePath),
                dbClient);
        if (null == volume) {
            return NullColumnValueGetter.getNullURI();
        }
        return volume.getId();
    }

    /**
     * get Storage System Id from Path
     * 
     * @param protocolEndPointPath
     * @return
     */
    protected String getStorageSystemFromPath(CIMObjectPath protocolEndPointPath) {
        Iterable<String> protocolEndPointItr = Splitter
                .on(Constants._plusDelimiter)
                .limit(4)
                .split(protocolEndPointPath.getKey(Constants.SYSTEMNAME).getValue()
                        .toString());
        String storageSystemId = getValueAtGivenPositionFromTierId(protocolEndPointItr, 0)
                + Constants.PLUS
                + getValueAtGivenPositionFromTierId(protocolEndPointItr, 1);
        return storageSystemId.toUpperCase();
    }

    protected StorageHADomain getStorageAdapter(DbClient dbClient, String adapterNativeGuid) {
        try {
            _logger.info("Adapter Native {}", adapterNativeGuid);
            @SuppressWarnings("deprecation")
            List<URI> adapterURIs = dbClient
                    .queryByConstraint(AlternateIdConstraint.Factory
                            .getStorageHADomainByNativeGuidConstraint(adapterNativeGuid));
            if (adapterURIs != null && !adapterURIs.isEmpty()) {
                for (URI adapterURI : adapterURIs) {
                    StorageHADomain adapter = dbClient.queryObject(
                            StorageHADomain.class, adapterURI);
                    _logger.info("Adapter {}", adapter.getId());
                    if (!adapter.getInactive()) {
                        return adapter;
                    }
                }
            }
        } catch (Exception e) {
            _logger.error("Adapter {} not found", adapterNativeGuid, e);
        }
        return null;
    }

    protected RemoteDirectorGroup checkRAGroupExistsInDB(DbClient dbClient, CIMInstance instance) {
        String raGroupNativeGuid = NativeGUIDGenerator.generateRAGroupNativeGuid(instance);
        _logger.info("RA Group Id :" + raGroupNativeGuid);
        @SuppressWarnings("deprecation")
        List<URI> raGroupUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getRAGroupByNativeGuidConstraint(raGroupNativeGuid));

        for (URI raGroupURI : raGroupUris) {
            RemoteDirectorGroup raGroup = dbClient.queryObject(RemoteDirectorGroup.class, raGroupURI);
            if (null != raGroup && !raGroup.getInactive()) {
                return raGroup;
            }
        }
        return null;
    }

    protected RemoteDirectorGroup getRAGroupUriFromDB(DbClient dbClient, String raGroupNativeGuid) {

        @SuppressWarnings("deprecation")
        List<URI> raGroupUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getRAGroupByNativeGuidConstraint(raGroupNativeGuid));

        for (URI raGroupURI : raGroupUris) {
            RemoteDirectorGroup raGroup = dbClient.queryObject(RemoteDirectorGroup.class, raGroupURI);
            if (null != raGroup && !raGroup.getInactive()) {
                return raGroup;
            }
        }
        return null;
    }

    /**
     * get Port Uri from DB
     * 
     * @param path
     * @return
     * @throws IOException
     */
    protected URI getPortUri(CIMObjectPath path, DbClient dbClient) throws IOException {
        String portName = path.getKey(Constants.NAME).getValue().toString();
        if (portName.contains("iqn")) {
            portName = portName.split(",")[0].toLowerCase();
        } else {
            portName = WWNUtility.getWWNWithColons(portName);
        }
        String portNativeGuid = NativeGUIDGenerator.generateNativeGuidForStoragePort(
                getStorageSystemFromPath(path), portName);
        return getStoragePortUriFromDB(portNativeGuid, dbClient);
    }

    /**
     * Check if Port exists in DB.
     * 
     * @param portInstance
     * @return
     * @throws IOException
     */
    protected StoragePort checkStoragePortExistsInDB(CIMInstance portInstance, StorageSystem device,
            DbClient dbClient) throws IOException {
        StoragePort port = null;
        String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(device,
                WWNUtility.getWWNWithColons(portInstance.getPropertyValue(PORTID).toString()),
                NativeGUIDGenerator.PORT);
        _logger.info("Port native Guid :{}", portNativeGuid);
        @SuppressWarnings("deprecation")
        List<URI> portURIs = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePortByNativeGuidConstraint(portNativeGuid));
        for (URI portURI : portURIs) {
            port = dbClient.queryObject(StoragePort.class, portURI);
            if (port != null && !port.getInactive()) {
                return port;
            }
        }

        return null;
    }

    /**
     * get Volume or snapshot Uri from DB
     * 
     * @param path
     * @return
     * @throws IOException
     */
    protected URI getVolumeOrSnapShotUri(CIMObjectPath path, DbClient dbClient)
            throws IOException {
        URI uri = null;
        String nativeGuid = getVolumeNativeGuid(path);
        Volume volume = checkStorageVolumeExistsInDB(nativeGuid, dbClient);
        if (null == volume || volume.getInactive()) {
            BlockSnapshot snapShot = checkSnapShotExistsInDB(nativeGuid, dbClient);
            if (!snapShot.getInactive()) {
                uri = snapShot.getId();
            }

        } else {
            uri = volume.getId();
        }
        return uri;
    }

    /**
     * check Storage Volume exists in DB
     * 
     * @param nativeGuid
     * @param dbClient
     * @return
     * @throws IOException
     */
    protected Volume checkStorageVolumeExistsInDB(String nativeGuid, DbClient dbClient)
            throws IOException {
        @SuppressWarnings("deprecation")
        List<URI> volumeUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeNativeGuidConstraint(nativeGuid));

        for (URI volumeURI : volumeUris) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            if (volume != null && !volume.getInactive()) {
                return volume;
            }
        }
        return null;
    }

    protected BlockMirror checkBlockMirrorExistsInDB(String nativeGuid, DbClient dbClient)
            throws IOException {
        @SuppressWarnings("deprecation")
        List<URI> mirrorUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getMirrorByNativeGuid(nativeGuid));

        for (URI mirrorURI : mirrorUris) {
            BlockMirror mirror = dbClient.queryObject(BlockMirror.class, mirrorURI);
            if (mirror != null && !mirror.getInactive()) {
                return mirror;
            }
        }
        return null;
    }

    /**
     * check Pre Existing Storage Volume exists in DB
     * 
     * @param nativeGuid
     * @param dbClient
     * @return
     * @throws IOException
     */
    protected UnManagedVolume checkUnManagedVolumeExistsInDB(
            String nativeGuid, DbClient dbClient) throws IOException {
        @SuppressWarnings("deprecation")
        List<URI> volumeUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeInfoNativeIdConstraint(nativeGuid));

        for (URI volumeURI : volumeUris) {
            UnManagedVolume volumeInfo = dbClient.queryObject(UnManagedVolume.class, volumeURI);
            if (volumeInfo != null && !volumeInfo.getInactive()) {
                return volumeInfo;
            }
        }
        return null;
    }

    /**
     * check Snapshot exists in DB
     * 
     * @param nativeGuid
     * @param dbClient
     * @return
     * @throws IOException
     */
    protected BlockSnapshot checkSnapShotExistsInDB(String nativeGuid, DbClient dbClient)
            throws IOException {
        @SuppressWarnings("deprecation")
        List<URI> snapShotUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getBlockSnapshotsByNativeGuid(nativeGuid));

        for (URI snapShotUri : snapShotUris) {
            BlockSnapshot snapShot = dbClient.queryObject(BlockSnapshot.class, snapShotUri);
            if (snapShot != null && !snapShot.getInactive()) {
                return snapShot;
            }
        }
        return null;
    }

    /**
     * Check if Port exists in DB.
     * 
     * @param portInstance
     * @return
     * @throws IOException
     */
    protected URI getStoragePortUriFromDB(String portNativeGuid, DbClient dbClient)
            throws IOException {
        URI portUri = NullColumnValueGetter.getNullURI();
        StoragePort port = null;
        @SuppressWarnings("deprecation")
        List<URI> portURIs = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePortByNativeGuidConstraint(portNativeGuid));
        for (URI portURI : portURIs) {
            port = dbClient.queryObject(StoragePort.class, portURI);
            if (port != null && !port.getInactive()) {
                portUri = port.getId();
                break;
            }
        }
        return portUri;
    }

    /**
     * get Masking View Native Guid from Masking View Object Path
     * 
     * @param lunMaskingViewPath
     * @return
     */
    protected String getMaskViewNativeGuid(CIMObjectPath lunMaskingViewPath) {
        Iterable<String> lunMaskingViewItr = Splitter
                .on(Constants._plusDelimiter)
                .limit(3)
                .split(lunMaskingViewPath.getKey(Constants.DEVICEID).getValue()
                        .toString());
        String storageSystemId = getValueAtGivenPositionFromTierId(lunMaskingViewItr, 0)
                + Constants.PLUS
                + getValueAtGivenPositionFromTierId(lunMaskingViewItr, 1);
        String maskName = getValueAtGivenPositionFromTierId(lunMaskingViewItr, 2);
        return NativeGUIDGenerator.generateNativeGuidForExportMask(
                storageSystemId.toUpperCase(), maskName);
    }

    /**
     * get Initiator native Guid
     * 
     * @param initiatorInstance
     * @return
     */
    protected String getInitiatorNativeGuid(CIMInstance initiatorInstance) {
        String storageId = getCIMPropertyValue(initiatorInstance, Constants.STORAGEID);
        return NativeGUIDGenerator.generateNativeGuidForInitiator(storageId);
    }

    /**
     * Loop through each VolumeInformation Entry in Enum, extract the property value from
     * Provider, and insert into Volume info's VolumeInformation Map.
     * 
     * @param storageVolumeInfo
     * @param volumeInstance
     * @param volumeInformation
     * @return
     */
    protected UnManagedVolume injectVolumeInformation(
            UnManagedVolume storageVolumeInfo, CIMInstance volumeInstance,
            Map<String, StringSet> volumeInformation) {
        if (null == volumeInformation) {
            volumeInformation = new HashMap<String, StringSet>();
        }
        for (SupportedVolumeInformation volumeInfo : SupportedVolumeInformation.values()) {
            injectIntoVolumeInformationContainer(volumeInformation,
                    volumeInfo.getInfoKey(), volumeInfo.getAlternateKey(), volumeInstance);
        }
        storageVolumeInfo.addVolumeInformation(volumeInformation);
        return storageVolumeInfo;
    }

    /**
     * Extract value from Provider for given volume info key, and then get its
     * Name and use that to inject to Map.
     * 
     * @param volumeInformation
     * @param infoKey
     * @param volumeInstance
     */
    protected void injectIntoVolumeInformationContainer(
            Map<String, StringSet> volumeInformation, String infoKey, String altKey,
            CIMInstance volumeInstance) {
        // till now, only string values are used, hence didn't had a need to return Object
        Object value = getCIMPropertyValue(volumeInstance, infoKey);
        // If the value is null, use the alternateKey to get the value.
        if (null == value) {
            value = getCIMPropertyValue(volumeInstance, altKey);
        }
        String charactersticName = SupportedVolumeInformation
                .getVolumeInformation(infoKey);
        if (null != value && null != charactersticName) {
            StringSet valueSet = new StringSet();
            // right now ,we don't have any properties which needs to be typecasted other than to String
            // hence doesn't have any instanceOf logic
            if (value instanceof String) {
                valueSet.add(value.toString());
            } else if (value instanceof String[]) {
                valueSet.addAll(Arrays.asList((String[]) value));
            }
            volumeInformation.put(charactersticName, valueSet);
        }
    }

    /**
     * insert into Map directly
     * 
     * @param storageVolumeInfo
     * @param infoKey
     * @param path
     */
    protected void injectIntoVolumeInformationContainer(
            UnManagedVolume storageVolumeInfo, String infoKey, CIMObjectPath path) {
        // till now, only string values are used, hence didn't had a need to return Object
        String value = getCIMPropertyValue(path, infoKey);
        String charactersticName = SupportedVolumeInformation
                .getVolumeInformation(infoKey);
        if (null != value && null != charactersticName) {
            StringSet valueSet = new StringSet();
            valueSet.add(value);
            storageVolumeInfo.putVolumeInfo(charactersticName, valueSet);
        }
    }

    /**
     * Loop through each VolumeCharacterstics Entry in Enum, extract the property value from
     * Provider, and insert into Volume info's VolumeCharacterstci Map.
     * 
     * @param storageVolumeInfo
     * @param volumeInstance
     * @param volumeInformation
     * @return
     */
    protected UnManagedVolume injectVolumeCharacterstics(
            UnManagedVolume storageVolumeInfo, CIMInstance volumeInstance,
            Map<String, String> volumeCharacterstics) {
        if (null == volumeCharacterstics) {
            volumeCharacterstics = new HashMap<String, String>();
        }
        for (SupportedVolumeCharacterstics characterstic : SupportedVolumeCharacterstics
                .values()) {
            injectIntoVolumeCharactersticContainer(volumeCharacterstics,
                    characterstic.getCharacterstic(), characterstic.getAlterCharacterstic(), volumeInstance);
        }
        if (storageVolumeInfo.getVolumeCharacterstics() == null) {
            storageVolumeInfo.setVolumeCharacterstics(new StringMap());
        }
        storageVolumeInfo.getVolumeCharacterstics().replace(volumeCharacterstics);
        return storageVolumeInfo;
    }

    /**
     * Extract value from Provider for given volume info key, and then get its
     * Name and use that to inject to Map.
     * 
     * @param volumeInformation
     * @param infoKey
     * @param volumeInstance
     */
    protected void injectIntoVolumeCharactersticContainer(
            Map<String, String> volumeCharacterstic, String charactersticKey, String altCharKey,
            CIMInstance volumeInstance) {
        // till now, only string values are used, hence didn't had a need to return Object
        Object value = getCIMPropertyValue(volumeInstance, charactersticKey);
        if (null == value) {
            value = getCIMPropertyValue(volumeInstance, altCharKey);
        }
        String charactersticName = SupportedVolumeCharacterstics
                .getVolumeCharacterstic(charactersticKey);
        if (null != value && null != charactersticName) {
            volumeCharacterstic.put(charactersticName, value.toString());
        }
    }

    /**
     * insert into Map directly
     * 
     * @param storageVolumeInfo
     * @param infoKey
     * @param path
     */
    protected void injectIntoVolumeCharactersticContainer(
            UnManagedVolume storageVolumeInfo, String charactersticKey, CIMObjectPath path) {
        // till now, only string values are used, hence didn't had a need to return Object
        String value = getCIMPropertyValue(path, charactersticKey);
        String charactersticName = SupportedVolumeCharacterstics
                .getVolumeCharacterstic(charactersticKey);
        if (null != value && null != charactersticName) {
            // right now ,we don't have any properties which needs to be typecasted other than to String
            // hence doesn't have any instanceOf logic
            storageVolumeInfo.putVolumeCharacterstics(charactersticName, value);
        }
    }

    @SuppressWarnings("unchecked")
    protected void processResultbyChunk(Object resultObj, Map<String, Object> keyMap) {
        CloseableIterator<CIMInstance> instances = null;
        EnumerateResponse<CIMInstance> instChunks = null;
        CIMObjectPath objPath = null;
        WBEMClient client = null;

        try {
            client = SMICommunicationInterface.getCIMClient(keyMap);
            objPath = getObjectPathfromCIMArgument(_args);
            instChunks = (EnumerateResponse<CIMInstance>) resultObj;
            // process entries returned in the Open operation
            _logger.info("Processing initial return");
            instances = instChunks.getResponses();
            int count = processInstances(instances, client);
            while (!instChunks.isEnd()) {
                _logger.info("Processing next chunk of size {}", BATCH_SIZE);
                instChunks = client.getInstancesWithPath(objPath, instChunks
                        .getContext(), new UnsignedInteger32(BATCH_SIZE));
                instances = instChunks.getResponses();
                count += processInstances(instances, client);
            }

            _logger.info("Total instances processed {}", count);
        } catch (Exception e) {
            _logger.error("Processing chunk failed :", e);
        } finally {
            if (null != instances) {
                instances.close();
            }

            if (null != instChunks) {
                try {
                    client.closeEnumeration(objPath, instChunks.getContext());
                } catch (Exception e) {
                    _logger.warn("Exception occurred while closing enumeration", e);
                }
            }
        }
    }

    protected int processInstances(Iterator<CIMInstance> instances) {
        return 0;
    }

    protected int processInstances(Iterator<CIMInstance> instances, WBEMClient client) {
        return 0;
    }

    protected String getSyncAspectMapKey(String srcObjPath, String elementName) {
        return srcObjPath + Constants.COLON + elementName;
    }

    protected String getAllocatedCapacity(CIMInstance volumeInstance,
            Map<String, String> volumeToSpaceConsumedMap, boolean isVMAX3) {
        String spaceConsumed = null;

        if (isVMAX3) {
            spaceConsumed = getCIMPropertyValue(volumeInstance,
                    EMC_SPACE_CONSUMED);
        } else {
            String volPath = volumeInstance.getObjectPath().toString();
            spaceConsumed = volumeToSpaceConsumedMap.get(volPath);
            if (spaceConsumed == null) {
                _logger.warn("Fail to get SpaceConsumed for {}", volPath);
            }
        }

        return spaceConsumed;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }

    protected CIMInstance getSyncElement(CIMInstance volumeInstance, WBEMClient client) {
        CloseableIterator<CIMObjectPath> storageSyncRefs;
        CIMInstance syncObject = null;
        try {
            storageSyncRefs = client.referenceNames(volumeInstance.getObjectPath(),
                    SmisConstants.CIM_STORAGE_SYNCHRONIZED, null);
            if (storageSyncRefs.hasNext()) {
                _logger.info("Processing the sync elements for volume: {}", volumeInstance.getObjectPath());
                CIMObjectPath storageSync = storageSyncRefs.next();
                if (null != storageSync) {
                    syncObject = client.getInstance(storageSync, false, false,
                            new String[] { SmisConstants.CP_SYNC_STATE });
                }
            }
        } catch (WBEMException e) {
            _logger.error("Not able to find the sync elements for volume {}", volumeInstance.getObjectPath());
        }
        return syncObject;
    }
}
