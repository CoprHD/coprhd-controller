/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeElementHBA;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UCSServiceProfileTemplate;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

public class NativeGUIDGenerator {

    // Logger reference.
    private static final Logger _logger = LoggerFactory.getLogger(NativeGUIDGenerator.class);

    // A reference to object that holds the device types.
    private static final Map<String, String> _deviceTypeMap = new HashMap<String, String>();

    private static final Set<String> OBJECT_TYPE_SET = new HashSet<String>();

    public static final String POOL = "POOL";

    public static final String PORT = "PORT";

    public static final String ADAPTER = "ADAPTER";

    public static final String FILESYSTEM = "FILESYSTEM";

    public static final String VOLUME = "VOLUME";

    public static final String SNAPSHOT = "SNAPSHOT";

    public static final String QUOTADIRECTORY = "QUOTADIRECTORY";

    private static final String INSTANCEID = "InstanceID";

    public static final String FASTPOLICY = "FASTPOLICY";

    public static final String SLO_POLICY = "SLO_POLICY";

    public static final String AUTO_TIERING_POLICY = "AUTOTIERINGPOLICY";

    private static final String TIER = "TIER";

    private static final String MASKINGVIEW = "MASKINGVIEW";

    private static final String INITIATOR = "INITIATOR";

    public static final String UN_MANAGED_VOLUME = "UNMANAGEDVOLUME";

    public static final String UN_MANAGED_FILESYSTEM = "UNMANAGEDFILESYSTEM";

    public static final String UN_MANAGED_FILE_EXPORT_RULE = "UNMANAGEDFILEEXPORTRULE";

    private static final String NAME = "NAME";

    private static final String REMOTE_GROUP = "REMOTEGROUP";

    private static final String UN_MANAGED_FILE_SHARE = "UNMANAGEDFILESHARE";

    public static final String PHYSICAL_NAS = "PHYSICALNAS";

    public static final String VIRTUAL_NAS = "VIRTUALNAS";

    static {
        OBJECT_TYPE_SET.add(POOL);
        OBJECT_TYPE_SET.add(PORT);
        OBJECT_TYPE_SET.add(ADAPTER);
        OBJECT_TYPE_SET.add(PHYSICAL_NAS);
        OBJECT_TYPE_SET.add(VIRTUAL_NAS);
    }

    /**
     * static block maps the names existed as part of indications with the corresponding devices
     * 
     * @param StorageSystem
     * @return
     */
    static {
        _deviceTypeMap.put(StorageSystem.Type.vmax.name(), "SYMMETRIX");
        _deviceTypeMap.put(StorageSystem.Type.vnxblock.name(), "CLARIION");
        _deviceTypeMap.put(StorageSystem.Type.isilon.name(), "ISILON");
        _deviceTypeMap.put(StorageSystem.Type.datadomain.name(), "DATADOMAIN");
        _deviceTypeMap.put(StorageSystem.Type.vnxfile.name(), "CELERRA");
        _deviceTypeMap.put(StorageSystem.Type.netapp.name(), "NETAPP");
        _deviceTypeMap.put(StorageSystem.Type.netappc.name(), "NETAPPC");
        _deviceTypeMap.put(NetworkSystem.Type.brocade.name(), "BROCADE");
        _deviceTypeMap.put(NetworkSystem.Type.mds.name(), "MDS");
        _deviceTypeMap.put(StorageSystem.Type.vplex.name(), "VPLEX");
        _deviceTypeMap.put(ProtectionSystem.Type.rp.name(), "RP");
        _deviceTypeMap.put(ComputeSystem.Type.ucs.name(), "UCS");
        _deviceTypeMap.put(StorageSystem.Type.hds.name(), "HDS");
        _deviceTypeMap.put(StorageSystem.Type.ibmxiv.name(), "IBMXIV");
        _deviceTypeMap.put(StorageSystem.Type.openstack.name(), "OPENSTACK");
        _deviceTypeMap.put(StorageSystem.Type.scaleio.name(), "SCALEIO");
        _deviceTypeMap.put(StorageSystem.Type.vnxe.name(), "VNXE");
        _deviceTypeMap.put(StorageSystem.Type.xtremio.name(), "XTREMIO");
        _deviceTypeMap.put(StorageSystem.Type.ecs.name(), "ECS");
    }

    /**
     * The format of this native guid is StorageSystem+SerialNumber
     * 
     * @param device
     * @return
     */
    public static String generateNativeGuid(StorageSystem device) {
        return String.format("%s+%s", _deviceTypeMap.get(device.getSystemType()), device.getSerialNumber());
    }

    /**
     * The format of this native guid is ComputeSystem+IpAddress+Port
     * 
     * @param device
     * @return
     */
    public static String generateNativeGuid(ComputeSystem device) {
        return String.format("%s+%s+%s", _deviceTypeMap.get(device.getSystemType()), device.getIpAddress(), device.getPortNumber());
    }

    /**
     * The format of this native guid is ComputeSystem+ComputeElement
     * 
     * @param device
     * @return
     */
    public static String generateNativeGuid(ComputeSystem cs, ComputeElement computeElement) {
        return String.format("%s+%s+%s", _deviceTypeMap.get(cs.getSystemType()), cs.getIpAddress(), computeElement.getLabel());
    }

    /**
     * The format of this native guid is ComputeSystem+ServiceProfileTemplateDn
     * 
     * @param device
     * @return
     */
    public static String generateNativeGuid(UCSServiceProfileTemplate serviceProfileTemplate, String systemType) {
        return String.format("%s+%s", _deviceTypeMap.get(systemType), serviceProfileTemplate.getDn());
    }

    /**
     * The format of this native guid is ComputeSystemType+Protocol+ComputeElementHBA
     * 
     * @param device
     * @return
     */
    public static String generateNativeGuid(ComputeElementHBA computeElementHBA, String systemType) {
        return String.format("%s+%s+%s", _deviceTypeMap.get(systemType), computeElementHBA.getProtocol(), computeElementHBA.getLabel());
    }

    /**
     * The format of this native guid is NetworkSystem+IPAddress+Port
     * NOTE: in the case of Brocade, an IP and port may not be
     * passed - in this case the SMIS provider IP and port will be used
     * 
     * @param netDevice
     * @return String nativeGuid
     */
    public static String generateNativeGuid(NetworkSystem netDevice) {

        if (netDevice.getSystemType().equalsIgnoreCase(NetworkSystem.Type.brocade.name())
                && netDevice.getSmisProviderIP() != null
                && netDevice.getSmisPortNumber() != null) {
            return String.format("%s+%s+%s", _deviceTypeMap.get(netDevice.getSystemType()), netDevice.getSmisProviderIP(),
                    netDevice.getSmisPortNumber());
        }

        return String
                .format("%s+%s+%s", _deviceTypeMap.get(netDevice.getSystemType()), netDevice.getIpAddress(), netDevice.getPortNumber());
    }

    /**
     * The format of this native guid is TransportType+DeviceType+FabricWWN.
     * TransportType is FC, DeviceType is mds or brocade and FabricWWN is
     * the unique WWN assigned to the fabric.
     * 
     * @param transportType - FC
     * @param deviceType - mds or brocade
     * @param fabricId - the fabric WWN
     * @return String nativeGuid
     */
    public static String generateTransportZoneNativeGuid(String transportType, String deviceType, String fabricId) {
        return String.format("%s+%s+%s", transportType, _deviceTypeMap.get(deviceType), fabricId);
    }

    /**
     * The format of this native guid using the given deviceType & SerialNumber.
     * 
     * @param deviceType : DeviceType.
     * @param serialNumber : serialNumber.
     * @return nativeGuid of the system.
     */
    public static String generateNativeGuid(String deviceType, String serialNumber) {
        return String.format("%s+%s", _deviceTypeMap.get(deviceType),
                serialNumber);
    }

    /**
     * Need to refactor NativeGuid Generator Code
     * 
     * @param poolObjectPath
     * @return String
     */
    public static String generateNativeGuidForPool(CIMObjectPath poolObjectPath) {
        String[] poolSplitter = poolObjectPath.getKey(INSTANCEID).getValue().toString()
                .split(Constants.PATH_DELIMITER_REGEX);
        return String.format("%s+%s+%s+%s+%s", poolSplitter[0].toUpperCase(),
                poolSplitter[1],
                POOL,
                poolSplitter[2],
                poolSplitter[3]);

    }

    /**
     * Generates the native guid format as StorageSystem+SerialNumber+PORT+WWN for StorgePort Objects
     * 
     * @param dbClient
     * @param port
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(DbClient dbClient, StoragePort port) {
        StorageSystem device = dbClient.queryObject(StorageSystem.class, port.getStorageDevice());
        return String.format("%s+%s+" + PORT + "+%s", _deviceTypeMap.get(device.getSystemType()), device.getSerialNumber(),
                port.getPortNetworkId());
    }

    /**
     * Generates the native guid format as StorageSystem+SerialNumber+<<TYPE>>+UNIQUE_ID for port, adapter & pool Objects.
     * 
     * @param device : storage system.
     * @param uniqueId : unique name.
     * @param type : type of the object to generated nativeGuid.
     * @return nativeGuid.
     * @throws IOException
     */
    public static String generateNativeGuid(StorageSystem device, String uniqueId, String type) {
        String typeStr = "UNKNOWN";
        if (OBJECT_TYPE_SET.contains(type)) {
            typeStr = type;
        }
        return String.format("%s+%s+%s+%s", _deviceTypeMap.get(device.getSystemType()), device.getSerialNumber(), typeStr, uniqueId);
    }

    /**
     * Generates the native guid format as ProtectionSystem+InstallationId+<<TYPE>>+UNIQUE_ID for port, adapter & pool Objects.
     * 
     * @param device : storage system.
     * @param uniqueId : unique name.
     * @param type : type of the object to generated nativeGuid.
     * @return nativeGuid.
     * @throws IOException
     */
    public static String generateNativeGuid(ProtectionSystem device, String uniqueId, String type) {
        String typeStr = "UNKNOWN";
        if (OBJECT_TYPE_SET.contains(type)) {
            typeStr = type;
        }
        return String.format("%s+%s+%s+%s", _deviceTypeMap.get(device.getSystemType()), device.getInstallationId(), typeStr, uniqueId);
    }

    /**
     * Generates the native guid format as StorageSystem+SerialNumber+ADAPTER+ADAPTER_NAME for StorgePort Objects
     * 
     * @param dbClient
     * @param port
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(DbClient dbClient, StorageHADomain adapter) {
        StorageSystem device = dbClient.queryObject(StorageSystem.class, adapter.getStorageDeviceURI());
        return String.format("%s+%s+" + ADAPTER + "+%s", _deviceTypeMap.get(device.getSystemType()), device.getSerialNumber(),
                adapter.getAdapterName());
    }

    /**
     * Generates the format StorageSystem+SerialNumber+VOLUME+NativeId native guid for Volume Objects
     * 
     * @param dbClient
     * @param volume
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(DbClient dbClient, Volume volume) throws IOException {
        StorageSystem device = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        return String.format("%s+%s+" + VOLUME + "+%s", _deviceTypeMap.get(device.getSystemType()), device.getSerialNumber(),
                volume.getNativeId());
    }

    /**
     * Generates the native guid for provider triggered indications of type VNX and VMAX VolumeView Indications
     * 
     * @param cimIndication
     * @return
     * @throws IOException
     */
    public static String generateSPNativeGuidFromVolumeViewIndication(Hashtable<String, String> cimIndication) {
        return generateSPNativeGuidFromIndication(cimIndication, CIMConstants.SOURCE_INSTANCE_MODEL_PATH_SP_INSTANCE_ID);
    }

    /**
     * Generates the native guid for provider triggered indications of type VNX and VMAX StoragePool Indications
     * 
     * @param cimIndication
     * @return
     * @throws IOException
     */
    public static String generateSPNativeGuidFromSPIndication(Hashtable<String, String> cimIndication) throws IOException {
        return generateSPNativeGuidFromIndication(cimIndication, CIMConstants.SOURCE_INSTANCE_MODEL_PATH_INSTANCE_ID);
    }

    /**
     * Generates the native guid for provider triggered indications of type VNX and VMAX StoragePool and VolumeView Indications
     * Example Values for reference :
     * SourceInstanceModelPathInstanceID : SYMMETRIX+000195900704+TP+GopiTest
     * SourceInstanceModelPathCompositeID : SYMMETRIX+000195900704+TP+GopiTest
     * 
     * @param cimIndication
     * @param serialNumber
     * @return
     * @throws IOException
     */
    private static String generateSPNativeGuidFromIndication(Hashtable<String, String> cimIndication,
            String poolNativeId_IndicationAttribute) {

        String serialNumber = null;
        String deviceType = null;
        String poolNativeId = null;
        String spInstanceId = null;
        try {

            spInstanceId = cimIndication
                    .get(poolNativeId_IndicationAttribute);

            String[] individualIds = spInstanceId.split(Constants.PATH_DELIMITER_REGEX);

            try {
                serialNumber = individualIds[1];
                poolNativeId = spInstanceId
                        .substring(spInstanceId.indexOf(serialNumber) + serialNumber.length() + 1, spInstanceId.length());
            } catch (Exception e) {
                _logger.error("Format of SourceInstanceModelPathSPInstanceID is not correct {}", spInstanceId);
                return null;
            }

            String modelPathCompositeId = cimIndication
                    .get(CIMConstants.SOURCE_INSTANCE_MODEL_PATH_COMPOSITE_ID);

            // get device type matched in composite id .
            if (modelPathCompositeId != null
                    && modelPathCompositeId
                            .indexOf(CIMConstants.CLARIION_PREFIX) != -1) {
                deviceType = _deviceTypeMap.get(StorageSystem.Type.vnxblock.name());
            } else if (modelPathCompositeId != null
                    && modelPathCompositeId
                            .indexOf(CIMConstants.SYMMETRIX_PREFIX) != -1) {
                deviceType = _deviceTypeMap.get(StorageSystem.Type.vmax.name());
            }

            _logger.debug("Using serialNumber - {}, deviceType - {} poolNativeId - {} to compute NativeGuid ",
                    new Object[] { serialNumber, deviceType, poolNativeId });

            if (serialNumber == null
                    || (serialNumber != null && serialNumber.length() <= 0)
                    || deviceType == null
                    || (deviceType != null && deviceType.length() <= 0)
                    || poolNativeId == null
                    || (poolNativeId != null && poolNativeId.length() <= 0)) {
                return null;
            }

            String nativeGuid = getNativeGuidforPool(deviceType, serialNumber,
                    poolNativeId);
            _logger.debug("Required format of NativeGuid computed : {}",
                    nativeGuid);
            return nativeGuid;
        } catch (Exception e) {
            _logger.error("Unable to compute native guid using indication's SourceInstanceModelPathSPInstanceID {} - {}", spInstanceId,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Generates the format StorageSystem+SerialNumber+FILESYSTEM+NativeId native guid for FileShare Objects
     * 
     * @param dbClient
     * @param fileShare
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(DbClient dbClient, FileShare fileShare) throws IOException {
        StorageSystem device = dbClient.queryObject(StorageSystem.class, fileShare.getStorageDevice());
        return String.format("%s+%s+" + FILESYSTEM + "+%s", _deviceTypeMap.get(device.getSystemType()), device.getSerialNumber(),
                fileShare.getNativeId());
    }

    /**
     * Generates the format StorageSystem+SerialNumber+SNAPSHOT+NativeId native guid for Snapshot Objects
     * 
     * @param dbClient
     * @param snapshot
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(DbClient dbClient, Snapshot snapshot) throws IOException {
        FileShare fs = dbClient.queryObject(FileShare.class, snapshot.getParent());
        StorageSystem device = dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        return String.format("%s+%s+" + SNAPSHOT + "+%s", _deviceTypeMap.get(device.getSystemType()),
                device.getSerialNumber(), snapshot.getNativeId());
    }

    public static String getNativeGuidforSnapshot(StorageSystem deviceType, String serialNumber, String nativeId)
    {
        _logger.info("Device Type : {} Serial No : {} nativeId : {}", new Object[] { _deviceTypeMap.get(deviceType.getSystemType()),
                serialNumber, nativeId });
        return String.format("%s+%s+SNAPSHOT+%s", _deviceTypeMap.get(deviceType.getSystemType()), serialNumber, nativeId);
    }

    /**
     * Generates the format StorageSystem+SerialNumber+FileSystemName+QUOTADIRECTORY+NativeId native guid for Quota
     * Directory Objects
     * 
     * @param dbClient
     * @param quotaDir
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(DbClient dbClient, QuotaDirectory quotaDir, String fsName) throws IOException {
        FileShare fs = dbClient.queryObject(FileShare.class, quotaDir.getParent());
        StorageSystem device = dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        return String.format("%s+%s+%s+" + QUOTADIRECTORY + "+%s", _deviceTypeMap.get(device.getSystemType()),
                device.getSerialNumber(), fsName, quotaDir.getName());
    }

    public static String generateNativeGuidForQuotaDir(DbClient dbClient, String quotaDirName, URI fsURI) throws IOException {
        FileShare fs = dbClient.queryObject(FileShare.class, fsURI);
        StorageSystem device = dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        return String.format("%s+%s+%s+" + QUOTADIRECTORY + "+%s", _deviceTypeMap.get(device.getSystemType()),
                device.getSerialNumber(), fs.getName(), quotaDirName);
    }

    /**
     * Generates the format StorageSystem+SerialNumber+FILESYSTEM+NativeId native guid for FileShare Objects
     * 
     * @param deviceType
     * @param serialNumber
     * @param fileShareNativeId
     * @return file share native Guid
     */
    public static String generateNativeGuid(String deviceType, String serialNumber, String fileShareNativeId) {
        return String.format("%s+%s+" + FILESYSTEM + "+%s", _deviceTypeMap.get(deviceType), serialNumber, fileShareNativeId);
    }

    private static String getNativeGuidforPool(String deviceType, String serialNumber, String poolNativeId)
    {
        return String.format("%s+%s+POOL+%s", deviceType, serialNumber, poolNativeId);
    }

    /**
     * Generates the format of native guid as StorageSystem+SerialNumber+POOL+PoolName for StorgePool Objects
     * 
     * @param dbClient
     * @param pool
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(DbClient dbClient, StoragePool pool) throws IOException {
        StorageSystem device = dbClient.queryObject(StorageSystem.class, pool.getStorageDevice());
        return getNativeGuidforPool(_deviceTypeMap.get(device.getSystemType()), device.getSerialNumber(), pool.getNativeId());
        // String.format("%s+%s+"+POOL+"+%s", _deviceTypeMap.get(device.getDeviceType()), device.getSerialNumber(), pool.getNativeId());
    }

    /**
     * Generates the native guid for file system related provider triggered indications
     * 
     * @param cimIndication
     * @param serialNumber
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(Hashtable<String, String> cimIndication, String serialNumber) {

        String prefixTag = cimIndication.get(
                CIMConstants.SOURCE_INSTANCE_MODEL_PATH_CLASS_PREFIX_TAG)
                .toUpperCase();
        _logger.debug("prefixTag :{}", prefixTag);
        String compositeId = cimIndication
                .get(CIMConstants.SOURCE_INSTANCE_MODEL_PATH_COMPOSITE_ID);
        _logger.debug("compositeId :{}", compositeId);
        if (compositeId != null && compositeId.lastIndexOf("/") != -1) {
            compositeId = compositeId
                    .substring(0, compositeId.lastIndexOf("/"));
        }

        if (serialNumber == null
                || (serialNumber != null && serialNumber.length() <= 0)
                || prefixTag == null
                || (prefixTag != null && prefixTag.length() <= 0)
                || compositeId == null
                || (compositeId != null && compositeId.length() <= 0)) {
            return null;
        }

        String nativeGuid = String.format("%s+%s+" + FILESYSTEM + "+%s", prefixTag, serialNumber, compositeId);
        _logger.debug("Bourne format NativeGuid computed : {}", nativeGuid);
        return nativeGuid;
    }

    /**
     * Generates the native guid for block related provider triggered indications
     * 
     * @param cimIndication
     * @return
     * @throws IOException
     */
    public static String generateNativeGuid(Hashtable<String, String> cimIndication) throws IOException {

        String nativeGuid = cimIndication
                .get(CIMConstants.SOURCE_INSTANCE_MODEL_PATH_COMPOSITE_ID);
        _logger.debug("Native Guid recieved as part of indication : {}", nativeGuid);

        // Convert the composite id to match Bourne format.
        if (nativeGuid != null
                && nativeGuid.indexOf(CIMConstants.CLARIION_PREFIX) != -1) {
            nativeGuid = nativeGuid.replace(CIMConstants.CLARIION_PREFIX,
                    CIMConstants.CLARIION_PREFIX_TO_UPPER);
        }
        if (nativeGuid != null) {
            nativeGuid = nativeGuid.replace("/", CIMConstants.VOLUME_PREFIX);
        }
        _logger.debug("Bourne format NativeGuid computed : {}", nativeGuid);
        return nativeGuid;

    }

    public static String generateNativeGuid(StorageSystem storageDevice, BlockSnapshot snapshot) {
        String storageDeviceType = _deviceTypeMap.get(storageDevice.getSystemType());
        return String.format("%s+%s+" + VOLUME + "+%s", storageDeviceType,
                storageDevice.getSerialNumber(), snapshot.getNativeId());
    }

    public static String generateNativeGuid(ProtectionSystem system, BlockSnapshot snapshot) {
        String storageDeviceType = _deviceTypeMap.get(system.getSystemType());
        return String.format("%s+%s+" + VOLUME + "+%s", storageDeviceType,
                system.getInstallationId(), snapshot.getNativeId());
    }

    public static String generateNativeGuid(StorageSystem storageDevice, BlockMirror mirror) {
        String storageDeviceType = _deviceTypeMap.get(storageDevice.getSystemType());
        return String.format("%s+%s+" + VOLUME + "+%s", storageDeviceType,
                storageDevice.getSerialNumber(), mirror.getNativeId());
    }

    public static String generateAutoTierPolicyNativeGuid(String systemNativeGuid, String policyName, String policy) {
        return String.format("%s+" + policy + "+%s", systemNativeGuid, policyName);
    }

    /**
     * Returns the tiering policy key string used for AutoTieringPolicy
     * NativeGuid generation for the given system type.
     * 
     * @param system storage system
     * @return tiering policy key
     */
    public static String getTieringPolicyKeyForSystem(StorageSystem system) {
        String policyKey = null;
        String systemType = system.getSystemType();
        if (Type.vmax.name().equalsIgnoreCase(systemType)
                && system.checkIfVmax3()) {
            policyKey = SLO_POLICY;
        } else if (Type.vmax.name().equals(systemType)
                || Type.vnxblock.name().equals(systemType)
                || Type.vnxe.name().equals(systemType)) {
            policyKey = FASTPOLICY;
        } else if (Type.hds.name().equals(systemType)) {
            policyKey = AUTO_TIERING_POLICY;
        } else {
            policyKey = FASTPOLICY;
        }
        return policyKey;
    }

    public static String generateStorageTierNativeGuidForVmaxTier(String systemNativeGuid, String tierName) {
        return String.format("%s+" + TIER + "+%s", systemNativeGuid, tierName);

    }

    public static String generateStorageTierNativeGuidForVnxTier(String systemNativeGuid, String poolID, String tierName) {
        return String.format("%s+" + TIER + "+%s+%s", systemNativeGuid, poolID, tierName);

    }

    public static String generateStorageTierNativeGuidForHDSTier(StorageSystem system, String poolID, String tierName) {
        return String.format("%s+" + "%s+" + TIER + "+%s+%s", _deviceTypeMap.get(system.getSystemType()),
                system.getSerialNumber(), poolID, tierName);
    }

    public static String generateNativeGuidForPreExistingVolume(String systemNativeGuid, String id) {
        return String.format("%s+" + UN_MANAGED_VOLUME + "+%s", systemNativeGuid, id);

    }

    public static String generateNativeGuidForPreExistingFileSystem(String deviceType, String serialNumber, String fileShareNativeId) {
        return String.format("%s+%s+" + UN_MANAGED_FILESYSTEM + "+%s", _deviceTypeMap.get(deviceType), serialNumber, fileShareNativeId);

    }

    public static String generateNativeGuidForVolumeOrBlockSnapShot(String systemNativeGuid, String snapShotId) {
        return String.format("%s+" + VOLUME + "+%s", systemNativeGuid, snapShotId);

    }

    public static String generateNativeGuidForExportMask(String systemNativeGuid, String maskName) {
        return String.format("%s+" + MASKINGVIEW + "+%s", systemNativeGuid, maskName);

    }

    public static String generateNativeGuidForStoragePort(String systemNativeGuid, String portId) {
        return String.format("%s+" + PORT + "+%s", systemNativeGuid, portId);

    }

    public static String generateNativeGuidForInitiator(String initiatorId) {
        return String.format(INITIATOR + "+%s", initiatorId);

    }

    /**
     * Generates the NativeGuid format as SystemNativeGuid+VirtualNasName for Virtual NAS.
     * 
     * @param System NativeGuid.
     * @param vNasName : virtual NAS name.
     * @return nativeGuid.
     */
    public static String generateNativeGuidForVirtualNAS(String systemNativeGuid, String vNasName) {
        return String.format("%s" + VIRTUAL_NAS + "+%s", systemNativeGuid, vNasName);
    }

    /**
     * Generates the NativeGuid format as SystemNativeGuid+PhysicalNasName for Physical NAS.
     * 
     * @param System NativeGuid.
     * @param pNasName : physical NAS name.
     * @return nativeGuid.
     */
    public static String generateNativeGuidForPhysicalNAS(String systemNativeGuid, String pNasName) {
        return String.format("%s" + PHYSICAL_NAS + "+%s", systemNativeGuid, pNasName);
    }

    /**
     * FC Port value from indication SourceInstanceSystemName : SYMMETRIX+000198700406+FA-1E
     * iSCSI port value from indication SourceInstanceSystemName : SYMMETRIX+000198700406+SE-1G
     * 
     * @param sourceInstanceSystemName
     * @param portNetworkId
     * @return
     */
    public static String generateNativeGuidForStoragePortFromIndication(String sourceInstanceSystemName, String portNetworkId) {
        String[] splitedString = sourceInstanceSystemName.split(Constants.PATH_DELIMITER_REGEX);
        return String.format("%s+%s+PORT+%s", splitedString[0], splitedString[1], portNetworkId);
    }

    public static String generateRAGroupNativeGuid(CIMInstance instance) {
        // Format : SYMMETRIX+000195701573+NAME+000195701505+27+000195701573+27 to
        // Format : SYMMETRIX+000195701573+REMOTEGROUP+000195701505+27+000195701573+27

        // VMAX3
        // SYMMETRIX-+-000196700566-+-12-+-000196700572-+-12
        // SYMMETRIX-+-000196700572-+-12-+-000196700566-+-12
        //
        // Format : SYMMETRIX-+-000196700566-+-12-+-000196700572-+-12 to
        // Format : SYMMETRIX+000196700566+REMOTEGROUP+000196700566+12+000196700572+12
        String instanceId = (String) instance
                .getPropertyValue(Constants.INSTANCEID);
        if (instanceId.contains(Constants.SMIS80_DELIMITER)
                || !instanceId.contains(NAME)) {
            instanceId = instanceId.replaceAll(
                    Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
            String sourceArray = instanceId
                    .split(Constants.PATH_DELIMITER_REGEX)[1];
            return instanceId.replaceFirst(Constants.PATH_DELIMITER_REGEX,
                    Constants.PLUS + sourceArray + Constants.PLUS
                            + REMOTE_GROUP + Constants.PLUS);
        } else {
            return instanceId.replace(NAME, REMOTE_GROUP);
        }
    }

    public static String generateRAGroupNativeGuid(CIMObjectPath path) {
        // Format : SYMMETRIX+000195701573+NAME+000195701505+27+000195701573+27 to
        // Format : SYMMETRIX+000195701573+REMOTEGROUP+000195701505+27+000195701573+27

        // VMAX3
        // SYMMETRIX-+-000196700566-+-12-+-000196700572-+-12
        // SYMMETRIX-+-000196700572-+-12-+-000196700566-+-12
        //
        // Format : SYMMETRIX-+-000196700566-+-12-+-000196700572-+-12 to
        // Format : SYMMETRIX+000196700566+REMOTEGROUP+000196700566+12+000196700572+12
        String instanceId = (String) path.getKey(Constants.INSTANCEID).getValue();
        if (instanceId.contains(Constants.SMIS80_DELIMITER)
                || !instanceId.contains(NAME)) {
            instanceId = instanceId.replaceAll(
                    Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
            String sourceArray = instanceId
                    .split(Constants.PATH_DELIMITER_REGEX)[1];
            return instanceId.replaceFirst(Constants.PATH_DELIMITER_REGEX,
                    Constants.PLUS + sourceArray + Constants.PLUS
                            + REMOTE_GROUP + Constants.PLUS);
        } else {
            return instanceId.replace(NAME, REMOTE_GROUP);
        }
    }

    public static String generateNativeGuidForPreExistingFileExportRule(StorageSystem storageSystem, String fileShareNativeId) {
        return String.format("%s+%s+" + UN_MANAGED_FILE_EXPORT_RULE + "+%s", _deviceTypeMap.get(storageSystem.getSystemType()),
                storageSystem.getSerialNumber().toUpperCase(), fileShareNativeId);
    }

    public static String generateNativeGuidForPreExistingFileShare(StorageSystem storageSystem, String fileShareNativeId) {
        return String.format("%s+%s+" + UN_MANAGED_FILE_SHARE + "+%s", _deviceTypeMap.get(storageSystem.getSystemType()), storageSystem
                .getSerialNumber().toUpperCase(), fileShareNativeId);
    }

}
