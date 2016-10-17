/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.scapi.SizeUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPort;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortFibreChannelConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortIscsiConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScFaultDomain;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplay;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageType;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageTypeStorageUsage;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolumeStorageUsage;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenter;
import com.emc.storageos.storagedriver.model.Initiator.Protocol;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedReplication;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;

/**
 * Utility methods for the driver.
 */
public class DellSCUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DellSCUtil.class);

    private static DellSCUtil instance;

    /**
     * Private constructor.
     */
    private DellSCUtil() {

    }

    /**
     * Gets the util instance.
     * 
     * @return The util instance.
     */
    public static DellSCUtil getInstance() {
        if (instance == null) {
            instance = new DellSCUtil();
        }

        return instance;
    }

    /**
     * Gets a VolumeSnapshot object for a given replay.
     *
     * @param replay The Storage Center snapshot.
     * @param snapshot The VolumeSnapshot object to populate or null.
     * @return The VolumeSnapshot object.
     */
    public VolumeSnapshot getVolumeSnapshotFromReplay(ScReplay replay, VolumeSnapshot snapshot) {
        VolumeSnapshot snap = snapshot;
        if (snap == null) {
            snap = new VolumeSnapshot();
        }
        snap.setNativeId(replay.instanceId);
        snap.setDeviceLabel(replay.instanceName);
        snap.setDisplayName(replay.instanceName);
        snap.setStorageSystemId(String.valueOf(replay.scSerialNumber));
        snap.setWwn(replay.globalIndex);
        snap.setParentId(replay.createVolume.instanceId);
        snap.setAllocatedCapacity(SizeUtil.sizeStrToBytes(replay.size));
        snap.setProvisionedCapacity(SizeUtil.sizeStrToBytes(replay.size));
        return snap;
    }

    /**
     * Populates a StorageVolume instance with Storage Center volume data.
     *
     * @param api The API connection.
     * @param volume The Storage Center volume.
     * @param cgInfo Consistency group information or null.
     * @return The StorageVolume.
     * @throws StorageCenterAPIException
     */
    public StorageVolume getStorageVolumeFromScVolume(StorageCenterAPI api, ScVolume volume, Map<ScReplayProfile, List<String>> cgInfo)
            throws StorageCenterAPIException {
        ScVolumeStorageUsage storageUsage = api.getVolumeStorageUsage(volume.instanceId);

        StorageVolume driverVol = new StorageVolume();
        driverVol.setStorageSystemId(volume.scSerialNumber);
        driverVol.setStoragePoolId(volume.storageType.instanceId);
        driverVol.setNativeId(volume.instanceId);
        driverVol.setThinlyProvisioned(true);
        driverVol.setProvisionedCapacity(SizeUtil.sizeStrToBytes(volume.configuredSize));
        driverVol.setAllocatedCapacity(SizeUtil.sizeStrToBytes(storageUsage.totalDiskSpace));
        driverVol.setWwn(volume.deviceId);
        driverVol.setDeviceLabel(volume.name);

        // Check consistency group membership
        if (cgInfo != null) {
            for (ScReplayProfile cg : cgInfo.keySet()) {
                if (cgInfo.get(cg).contains(volume.instanceId)) {
                    // Found our volume in a consistency group
                    driverVol.setConsistencyGroup(cg.instanceId);
                    break;
                }
            }
        }

        return driverVol;
    }

    /**
     * Gets consistency groups and volumes from a given Storage Center.
     *
     * @param api The API connection.
     * @param ssn The Storage Center serial number.
     * @return The consistency groups and their volumes.
     */
    public Map<ScReplayProfile, List<String>> getGCInfo(StorageCenterAPI api, String ssn) {
        Map<ScReplayProfile, List<String>> result = new HashMap<>();
        ScReplayProfile[] cgs = api.getConsistencyGroups(ssn);
        for (ScReplayProfile cg : cgs) {
            result.put(cg, new ArrayList<>());
            try {
                ScVolume[] vols = api.getReplayProfileVolumes(cg.instanceId);
                for (ScVolume vol : vols) {
                    result.get(cg).add(vol.instanceId);
                }
            } catch (StorageCenterAPIException e) {
                LOG.warn(String.format("Error getting volumes for consistency group %s: %s", cg.instanceId, e));
            }
        }

        return result;
    }

    /**
     * Gets a consistency group object from an SC replay profile.
     *
     * @param cg The ScReplayProfile.
     * @param volumeConsistencyGroup The consistency group object or null.
     * @return The consistency group object.
     */
    public VolumeConsistencyGroup getVolumeConsistencyGroupFromReplayProfile(ScReplayProfile cg,
            VolumeConsistencyGroup volumeConsistencyGroup) {
        if (volumeConsistencyGroup == null) {
            volumeConsistencyGroup = new VolumeConsistencyGroup();
        }

        volumeConsistencyGroup.setAccessStatus(AccessStatus.READ_WRITE);
        volumeConsistencyGroup.setNativeId(cg.instanceId);
        volumeConsistencyGroup.setDeviceLabel(cg.name);
        volumeConsistencyGroup.setStorageSystemId(cg.scSerialNumber);

        return volumeConsistencyGroup;
    }

    /**
     * Gets a StoragePort object for an ScControllerPort.
     *
     * @param api The API connection.
     * @param scPort The ScControllerPort.
     * @return The StoragePort.
     */
    public StoragePort getStoragePortForControllerPort(StorageCenterAPI api, ScControllerPort scPort) {
        return getStoragePortForControllerPort(api, scPort, null);
    }

    /**
     * Gets a StoragePort object for an ScControllerPort.
     *
     * @param api The API connection.
     * @param scPort The ScControllerPort.
     * @param haZone The fault domain name.
     * @return The StoragePort.
     */
    public StoragePort getStoragePortForControllerPort(StorageCenterAPI api, ScControllerPort scPort, String haZone) {
        StoragePort port = new StoragePort();

        port.setNativeId(scPort.instanceId);
        port.setStorageSystemId(scPort.scSerialNumber);

        // Get the port configuration
        port.setPortHAZone(getHaZone(api, scPort, haZone));

        if (ScControllerPort.FC_TRANSPORT_TYPE.equals(scPort.transportType)) {
            setFCPortInfo(api, scPort, port);
        } else if (ScControllerPort.ISCSI_TRANSPORT_TYPE.equals(scPort.transportType)) {
            setISCSIPortInfo(api, scPort, port);
        }

        StoragePort.OperationalStatus status = StoragePort.OperationalStatus.OK;
        if (!ScControllerPort.PORT_STATUS_UP.equals(scPort.status)) {
            status = StoragePort.OperationalStatus.NOT_OK;
        }
        port.setOperationalStatus(status);
        port.setPortName(port.getDeviceLabel());
        port.setEndPointID(port.getPortNetworkId());
        port.setAccessStatus(AccessStatus.READ_WRITE);

        return port;
    }

    /**
     * Gets the HA zone name.
     * 
     * @param api The API connection.
     * @param scPort The ScControllerPort.
     * @param hazone The zone name if known.
     * @return The zone name.
     */
    private String getHaZone(StorageCenterAPI api, ScControllerPort scPort, String hazone) {
        if (hazone != null && !hazone.isEmpty()) {
            return hazone;
        }
        String haZone = "";
        ScFaultDomain[] faultDomains = api.getControllerPortFaultDomains(scPort.instanceId);
        if (faultDomains.length > 0) {
            // API returns list, but currently only one fault domain per port allowed
            haZone = faultDomains[0].name;
        }

        return haZone;
    }

    /**
     * Sets FC specific info for a port.
     *
     * @param api The API connection.
     * @param scPort The Storage Center port.
     * @param port The storage port object to populate.
     */
    private void setFCPortInfo(StorageCenterAPI api, ScControllerPort scPort, StoragePort port) {
        port.setDeviceLabel(scPort.wwn);
        port.setTransportType(StoragePort.TransportType.FC);

        ScControllerPortFibreChannelConfiguration portConfig = api.getControllerPortFCConfig(
                scPort.instanceId);
        port.setPortNetworkId(formatWwn(scPort.wwn));
        port.setEndPointID(port.getPortNetworkId());
        port.setPortSpeed(SizeUtil.speedStrToGigabits(portConfig.speed));
        port.setPortGroup(String.format("%s", portConfig.homeControllerIndex));
        port.setPortSubGroup(String.format("%s", portConfig.slot));
        port.setTcpPortNumber(0L);
    }

    /**
     * Sets iSCSI specific info for a port.
     *
     * @param api The API connection.
     * @param scPort The Storage Center port.
     * @param port The storage port object to populate.
     */
    private void setISCSIPortInfo(StorageCenterAPI api, ScControllerPort scPort, StoragePort port) {
        port.setDeviceLabel(scPort.iscsiName);
        port.setTransportType(StoragePort.TransportType.IP);

        ScControllerPortIscsiConfiguration portConfig = api.getControllerPortIscsiConfig(
                scPort.instanceId);
        port.setEndPointID(scPort.iscsiName);
        port.setIpAddress(scPort.iscsiIpAddress);
        port.setPortNetworkId(scPort.iscsiName);
        port.setNetworkId(scPort.getNetwork());
        port.setPortSpeed(SizeUtil.speedStrToGigabits(portConfig.speed));
        port.setPortGroup(String.format("%s", portConfig.homeControllerIndex));
        port.setPortSubGroup(String.format("%s", portConfig.slot));
        port.setTcpPortNumber(portConfig.portNumber);
    }

    /**
     * Gets a StoragePool object for a storage type.
     *
     * @param api The Storage Center API connection.
     * @param storageType The storage type.
     * @param pool The StoragePool object to populate or null.
     * @return The StoragePool.
     */
    public StoragePool getStoragePoolFromStorageType(StorageCenterAPI api, ScStorageType storageType, StoragePool pool) {
        if (pool == null) {
            pool = new StoragePool();
        }
        pool.setNativeId(storageType.instanceId);
        pool.setStorageSystemId(storageType.scSerialNumber);
        LOG.info("Discovered Pool {}, storageSystem {}",
                pool.getNativeId(), pool.getStorageSystemId());

        pool.setDeviceLabel(storageType.name);
        pool.setDisplayName(storageType.name);
        pool.setPoolName(storageType.name);
        pool.setCapabilities(new ArrayList<>(0));

        // Get the supported transport protocols
        Set<StoragePool.Protocols> protocols = new HashSet<>();
        List<String> transportProtocols = getSupportedProtocols(api, storageType.scSerialNumber);
        if (transportProtocols.contains(Protocol.FC.toString())) {
            protocols.add(StoragePool.Protocols.FC);
        }
        if (transportProtocols.contains(Protocol.iSCSI.toString())) {
            protocols.add(StoragePool.Protocols.iSCSI);
        }
        pool.setProtocols(protocols);

        pool.setPoolServiceType(StoragePool.PoolServiceType.block);
        pool.setMaximumThickVolumeSize(0L);
        pool.setMinimumThickVolumeSize(0L);
        pool.setMaximumThinVolumeSize(549755813888L); // Max 512 TB
        pool.setMinimumThinVolumeSize(1048576L); // Min 1 GB
        pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_ONLY);

        ScStorageTypeStorageUsage su = api.getStorageTypeStorageUsage(storageType.instanceId);
        LOG.info("Space info: {} {} {}", su.allocatedSpace, su.freeSpace, su.usedSpace);
        pool.setSubscribedCapacity(SizeUtil.sizeStrToKBytes(su.usedSpace));
        pool.setFreeCapacity(SizeUtil.sizeStrToKBytes(su.freeSpace));
        pool.setTotalCapacity(SizeUtil.sizeStrToKBytes(su.allocatedSpace));
        pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY);

        return pool;
    }

    /**
     * Gets the transport protocols supported by an array.
     *
     * @param api The SC API connection.
     * @param scSerialNumber The Storage Center serial number to check.
     * @return The supported protocols.
     */
    private List<String> getSupportedProtocols(StorageCenterAPI api, String scSerialNumber) {

        List<String> protocols = new ArrayList<>();
        boolean hasIScsi = false;
        boolean hasFC = false;
        ScControllerPort[] controllerPorts = api.getTargetPorts(scSerialNumber, null);
        for (ScControllerPort scPort : controllerPorts) {
            if (ScControllerPort.FC_TRANSPORT_TYPE.equals(scPort.transportType)) {
                hasFC = true;
            } else if (ScControllerPort.ISCSI_TRANSPORT_TYPE.equals(scPort.transportType)) {
                hasIScsi = true;
            }
        }

        if (hasIScsi) {
            protocols.add(Protocols.iSCSI.toString());
        }
        if (hasFC) {
            protocols.add(Protocols.FC.toString());
        }

        return protocols;
    }

    /**
     * Populate a StorageSystem object with Storage Center info.
     *
     * @param api The SC API connection.
     * @param sc The Storage Center.
     * @param storageSystemOrNull The StorageSystem to populate or null.
     * @return The StorageSystem.
     */
    public StorageSystem getStorageSystemFromStorageCenter(StorageCenterAPI api, StorageCenter sc,
            StorageSystem storageSystemOrNull) {
        StorageSystem storageSystem = storageSystemOrNull;
        if (storageSystem == null) {
            storageSystem = new StorageSystem();
        }

        storageSystem.setSerialNumber(sc.scSerialNumber);
        storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
        storageSystem.setModel(sc.modelSeries);
        storageSystem.setProvisioningType(SupportedProvisioningType.THIN);
        storageSystem.setNativeId(sc.scSerialNumber);

        // Parse out version information
        String[] version = sc.version.split("\\.");
        storageSystem.setMajorVersion(version[0]);
        storageSystem.setMinorVersion(version[1]);
        storageSystem.setFirmwareVersion(sc.version);
        storageSystem.setIsSupportedVersion(true);

        storageSystem.setDeviceLabel(sc.scName);
        storageSystem.setDisplayName(sc.name);

        // Make sure it's reported that we support CGs
        Set<SupportedReplication> supportedReplications = new HashSet<>();
        supportedReplications.add(SupportedReplication.elementReplica);
        supportedReplications.add(SupportedReplication.groupReplica);
        storageSystem.setSupportedReplications(supportedReplications);

        // Check the supported protocols for this array
        List<String> protocols = getSupportedProtocols(api, sc.scSerialNumber);
        storageSystem.setProtocols(protocols);

        return storageSystem;
    }

    /**
     * Gets a formatted WWN.
     *
     * @param wwn The raw WWN.
     * @return The formatted WWN.
     */
    public String formatWwn(String wwn) {
        if (wwn == null || wwn.length() != 16) {
            return wwn;
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < wwn.length(); i += 2) {
            parts.add(wwn.substring(i, i + 2).toUpperCase());
        }

        return String.join(":", parts);
    }
}
