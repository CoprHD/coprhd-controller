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
package com.emc.storageos.driver.dellsc.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.DellSCDriverException;
import com.emc.storageos.driver.dellsc.DellSCDriverTask;
import com.emc.storageos.driver.dellsc.DellSCUtil;
import com.emc.storageos.driver.dellsc.scapi.SizeUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPort;
import com.emc.storageos.driver.dellsc.scapi.objects.ScMapping;
import com.emc.storageos.driver.dellsc.scapi.objects.ScMappingProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScPhysicalServer;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScServer;
import com.emc.storageos.driver.dellsc.scapi.objects.ScServerOperatingSystem;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.Initiator.HostOsType;
import com.emc.storageos.storagedriver.model.Initiator.Protocol;
import com.emc.storageos.storagedriver.model.Initiator.Type;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * Helper class for driver provisioning operations.
 */
public class DellSCProvisioning {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCProvisioning.class);

    private static final String SERVER_CREATE_FAIL_MSG = "Unable to find or create server on the array.";

    private DellSCPersistence persistence;
    private DellSCUtil util;

    /**
     * Initialize the instance.
     * 
     * @param persistence The persistence interface.
     */
    public DellSCProvisioning(DellSCPersistence persistence) {
        this.persistence = persistence;
        this.util = new DellSCUtil();
    }

    /**
     * Create storage volumes with a given set of capabilities.
     * Before completion of the request, set all required data for provisioned
     * volumes in "volumes" parameter.
     *
     * @param volumes Input/output argument for volumes.
     * @param storageCapabilities Input argument for capabilities. Defines
     *            storage capabilities of volumes to create.
     * @return The volume creation task.
     */
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities storageCapabilities) {
        DriverTask task = new DellSCDriverTask("createVolume");

        Map<String, List<ScReplayProfile>> consistencyGroups = new HashMap<>();
        StringBuilder errBuffer = new StringBuilder();
        int volumesCreated = 0;
        for (StorageVolume volume : volumes) {
            LOG.debug("Creating volume {} on system {}", volume.getDisplayName(), volume.getStorageSystemId());
            String ssn = volume.getStorageSystemId();
            try (StorageCenterAPI api = persistence.getSavedConnection(ssn)) {
                // See if we need to add to a consistency group
                String cgID = new DellSCUtil().findCG(api, ssn, volume.getConsistencyGroup(), consistencyGroups);

                ScVolume scVol = api.createVolume(
                        ssn,
                        volume.getDisplayName(),
                        volume.getStoragePoolId(),
                        SizeUtil.byteToGig(volume.getRequestedCapacity()),
                        cgID);

                volume.setProvisionedCapacity(SizeUtil.sizeStrToBytes(scVol.configuredSize));
                volume.setAllocatedCapacity(0L); // New volumes don't allocate any space
                volume.setWwn(scVol.deviceId);
                volume.setNativeId(scVol.instanceId);
                volume.setDeviceLabel(scVol.name);
                volume.setAccessStatus(AccessStatus.READ_WRITE);

                volumesCreated++;
                LOG.info("Created volume '{}'", scVol.name);
            } catch (StorageCenterAPIException | DellSCDriverException dex) {
                String error = String.format("Error creating volume %s: %s", volume.getDisplayName(), dex);
                LOG.error(error);
                errBuffer.append(String.format("%s%n", error));
            }
        }

        task.setMessage(errBuffer.toString());

        if (volumesCreated == volumes.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (volumesCreated == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Expand volume to a new size.
     * Before completion of the request, set all required data for expanded
     * volume in "volume" parameter.
     *
     * @param storageVolume Volume to expand. Type: Input/Output argument.
     * @param newCapacity Requested capacity. Type: input argument.
     * @return The volume expansion task.
     */
    public DriverTask expandVolume(StorageVolume storageVolume, long newCapacity) {
        DriverTask task = new DellSCDriverTask("expandVolume");
        try (StorageCenterAPI api = persistence.getSavedConnection(storageVolume.getStorageSystemId())) {
            ScVolume scVol = api.expandVolume(storageVolume.getNativeId(), SizeUtil.byteToGig(newCapacity));
            storageVolume.setProvisionedCapacity(SizeUtil.sizeStrToBytes(scVol.configuredSize));

            task.setStatus(TaskStatus.READY);
            LOG.info("Expanded volume '{}'", scVol.name);
        } catch (DellSCDriverException | StorageCenterAPIException dex) {
            String error = String.format("Error expanding volume %s: %s",
                    storageVolume.getDisplayName(), dex);
            LOG.error(error);
            task.setMessage(error);
            task.setStatus(TaskStatus.FAILED);
        }

        return task;
    }

    /**
     * Delete volume.
     *
     * @param volume The volume to delete.
     * @return The volume deletion task.
     */
    public DriverTask deleteVolume(StorageVolume volume) {
        DellSCDriverTask task = new DellSCDriverTask("deleteVolume");

        try (StorageCenterAPI api = persistence.getSavedConnection(volume.getStorageSystemId())) {
            api.deleteVolume(volume.getNativeId());

            task.setStatus(TaskStatus.READY);
            LOG.info("Deleted volume '{}'", volume.getNativeId());
        } catch (DellSCDriverException | StorageCenterAPIException dex) {
            String error = String.format("Error deleting volume %s", volume.getNativeId(), dex);
            LOG.error(error);
            task.setFailed(error);
        }

        return task;
    }

    /**
     * Export volumes to initiators through a given set of ports. If ports are
     * not provided, use port requirements from ExportPathsServiceOption
     * storage capability.
     *
     * @param initiators The initiators to export to.
     * @param volumes The volumes to export.
     * @param volumeToHLUMap Map of volume nativeID to requested HLU. HLU
     *            value of -1 means that HLU is not defined and will
     *            be assigned by array.
     * @param recommendedPorts List of storage ports recommended for the export.
     *            Optional.
     * @param availablePorts List of ports available for the export.
     * @param capabilities The storage capabilities.
     * @param usedRecommendedPorts True if driver used recommended and only
     *            recommended ports for the export, false
     *            otherwise.
     * @param selectedPorts Ports selected for the export (if recommended ports
     *            have not been used).
     * @return The export task.
     * @throws DellSCDriverException
     */
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts,
            List<StoragePort> availablePorts, StorageCapabilities capabilities,
            MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        LOG.info("Exporting volumes to inititators");
        DellSCDriverTask task = new DellSCDriverTask("exportVolumes");

        ScServer server = null;
        StringBuilder errBuffer = new StringBuilder();
        int volumesMapped = 0;
        Set<StoragePort> usedPorts = new HashSet<>();
        List<String> discoveredPorts = new ArrayList<>();

        for (StorageVolume volume : volumes) {
            String ssn = volume.getStorageSystemId();
            try (StorageCenterAPI api = persistence.getSavedConnection(ssn)) {
                // Find our actual volume
                ScVolume scVol = api.getVolume(volume.getNativeId());
                if (scVol == null) {
                    throw new DellSCDriverException(
                            String.format("Unable to find volume %s", volume.getNativeId()));
                }

                // Look up the server if needed
                if (server == null) {
                    server = createOrFindScServer(api, ssn, initiators);
                }

                if (server == null) {
                    // Unable to find or create the server, can't continue
                    throw new DellSCDriverException(SERVER_CREATE_FAIL_MSG);
                }

                int preferredLun = -1;
                if (volumeToHLUMap.containsKey(volume.getNativeId())) {
                    String hlu = volumeToHLUMap.get(volume.getNativeId());
                    try {
                        preferredLun = Integer.parseInt(hlu);
                    } catch (NumberFormatException e) {
                        LOG.warn("Unable to parse preferred LUN {}", hlu);
                    }
                }

                ScMappingProfile profile = null;

                // See if the volume is already mapped
                ScMappingProfile[] mappingProfiles = api.getServerVolumeMapping(
                        scVol.instanceId, server.instanceId);
                if (mappingProfiles.length > 0) {
                    // This one is already mapped
                    profile = mappingProfiles[0];
                } else {
                    profile = api.createVolumeMappingProfile(
                            scVol.instanceId, server.instanceId, preferredLun);
                }

                ScMapping[] maps = api.getMappingProfileMaps(profile.instanceId);
                for (ScMapping map : maps) {
                    volumeToHLUMap.put(volume.getNativeId(), String.valueOf(map.lun));
                    if (!discoveredPorts.contains(map.controllerPort.instanceId)) {
                        ScControllerPort scPort = api.getControllerPort(map.controllerPort.instanceId);
                        StoragePort port = util.getStoragePortForControllerPort(api, scPort, null);
                        discoveredPorts.add(scPort.instanceId);
                        usedPorts.add(port);
                    }
                }

                volumesMapped++;
                LOG.info("Volume '{}' exported to server '{}'", scVol.name, server.name);
            } catch (StorageCenterAPIException | DellSCDriverException dex) {
                String error = String.format("Error mapping volume %s: %s", volume.getDisplayName(), dex);
                LOG.error(error);
                errBuffer.append(String.format("%s%n", error));

                if (SERVER_CREATE_FAIL_MSG.equals(dex.getMessage())) {
                    // Game over
                    break;
                }
            }
        }

        // See if we were able to use all of the recommended ports
        usedRecommendedPorts.setValue(recommendedPorts.size() == usedPorts.size());
        if (!usedRecommendedPorts.isTrue()) {
            selectedPorts.addAll(usedPorts);
        }

        task.setMessage(errBuffer.toString());

        if (volumesMapped == volumes.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (volumesMapped == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Finds an existing server definition.
     *
     * @param ssn The Storage Center to check.
     * @param api The API connection.
     * @param initiators The list of initiators.
     * @return The server object or null.
     */
    private ScServer findScServer(StorageCenterAPI api, String ssn, List<Initiator> initiators) {
        return createOrFindScServer(api, ssn, initiators, false);
    }

    /**
     * Finds an existing server definition or creates a new one.
     *
     * @param ssn The Storage Center to check.
     * @param api The API connection.
     * @param initiators The list of initiators.
     * @return The server object or null.
     */
    private ScServer createOrFindScServer(StorageCenterAPI api, String ssn, List<Initiator> initiators) {
        return createOrFindScServer(api, ssn, initiators, true);
    }

    /**
     * Finds an existing server definition or creates a new one.
     *
     * @param ssn The Storage Center to check.
     * @param api The API connection.
     * @param initiators The list of initiators.
     * @param createIfNotFound Whether to create the server if it's not found.
     * @return The server object or null.
     */
    private ScServer createOrFindScServer(StorageCenterAPI api, String ssn, List<Initiator> initiators, boolean createIfNotFound) {
        boolean isCluster = false;
        String clusterName = "";
        ScServerOperatingSystem os = null;

        Map<String, ScServer> serverLookup = new HashMap<>();
        for (Initiator init : initiators) {
            boolean cluster = init.getInitiatorType().equals(Type.Cluster);
            isCluster = isCluster || cluster;
            clusterName = init.getClusterName();

            if (os == null) {
                os = findOsType(api, ssn, init.getHostOsType());
            }

            String iqnOrWwn = init.getPort();
            if (init.getProtocol().equals(Protocol.FC)) {
                // Make sure it's in the format we expect
                iqnOrWwn = iqnOrWwn.replace(":", "").toUpperCase();
            }
            ScServer individualServer = api.findServer(ssn, iqnOrWwn);

            if (individualServer == null && createIfNotFound) {
                if (serverLookup.containsKey(init.getHostName())) {
                    // Need to add this initiator to existing server definition
                    individualServer = serverLookup.get(init.getHostName());
                    api.addHbaToServer(
                            individualServer.instanceId,
                            iqnOrWwn,
                            init.getProtocol().equals(Protocol.iSCSI));
                } else {
                    // Need to create a new server definition

                    try {
                        individualServer = api.createServer(
                                ssn,
                                init.getHostName(),
                                iqnOrWwn,
                                init.getProtocol().equals(Protocol.iSCSI),
                                os.instanceId);
                    } catch (StorageCenterAPIException e) {
                        // Well that's rather unfortunate
                        LOG.warn(String.format("Error creating server: %s", e));
                        continue;
                    }
                }
            }

            if (individualServer != null) {
                serverLookup.put(init.getHostName(), individualServer);
            }
        }

        if (isCluster) {
            // Find our cluster server definition
            ScServer server = null;
            for (ScServer scServer : serverLookup.values()) {
                ScPhysicalServer phyServer = null;
                if (scServer instanceof ScPhysicalServer) {
                    phyServer = (ScPhysicalServer) scServer;
                } else {
                    phyServer = api.getPhysicalServerDefinition(scServer.instanceId);
                }

                if (phyServer == null || phyServer.parent == null) {
                    continue;
                }

                server = api.getServerDefinition(phyServer.parent.instanceId);
                break;
            }

            if (server == null) {
                try {
                    // Create cluster server definition
                    server = api.createClusterServer(
                            ssn,
                            clusterName,
                            os.instanceId);
                } catch (StorageCenterAPIException e) {
                    LOG.warn(String.format("Error creating cluster: %s", e));
                    return null;
                }
            }

            // Now make sure all servers are set to be under this cluster
            for (ScServer scServer : serverLookup.values()) {
                LOG.info("Adding server '{}' to cluster '{}', result: {}",
                        scServer.name,
                        server.name,
                        api.setAddServerToCluster(scServer.instanceId, server.instanceId));
            }

            return server;
        } else {
            if (serverLookup.size() != 1) {
                LOG.warn("Looking for server returned {} servers.",
                        serverLookup.size());
            }

            for (ScServer scServer : serverLookup.values()) {
                // Just return the first one
                return scServer;
            }
        }

        return null;
    }

    /**
     * Find an appropriate server operating system type.
     *
     * @param hostOsType The provided OS type.
     * @return The SC OS type.
     */
    private ScServerOperatingSystem findOsType(StorageCenterAPI api, String ssn, HostOsType hostOsType) {
        String product;
        String version;
        switch (hostOsType) {
            case Windows:
                product = "Windows";
                version = "2012 MPIO";
                break;
            case HPUX:
                product = "HP UX";
                version = "11i v3";
                break;
            case Linux:
                // Behavior is pretty much the same between them, so
                // we'll just default to Red Hat
                product = "Red Hat Linux";
                version = "6.x";
                break;
            case Esx:
                product = "VMWare ESX";
                version = "5.1";
                break;
            case AIX:
            case AIXVIO:
                product = "AIX";
                version = "7.1 MPIO";
                break;
            case SUNVCS:
            case No_OS:
            case Other:
            default:
                product = "Other";
                version = "Multipath";
                break;
        }

        ScServerOperatingSystem[] osTypes = api.getServerOperatingSystems(ssn, product);

        // First try for an exact match
        for (ScServerOperatingSystem os : osTypes) {
            if (version.equals(os.version)) {
                return os;
            }
        }

        // Just get the first one
        for (ScServerOperatingSystem os : osTypes) {
            return os;
        }

        return null;
    }

    /**
     * Remove volume exports to initiators.
     *
     * @param initiators The initiators to remove from.
     * @param volumes The volumes to remove.
     * @return The unexport task.
     */
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        LOG.info("Unexporting volumes from initiators");
        DriverTask task = new DellSCDriverTask("unexportVolumes");

        ScServer server = null;
        StringBuilder errBuffer = new StringBuilder();
        int volumesUnmapped = 0;

        for (StorageVolume volume : volumes) {
            String ssn = volume.getStorageSystemId();
            try (StorageCenterAPI api = persistence.getSavedConnection(ssn)) {
                // Find our actual volume
                ScVolume scVol = api.getVolume(volume.getNativeId());
                if (scVol == null) {
                    throw new DellSCDriverException(
                            String.format("Unable to find volume %s", volume.getNativeId()));
                }

                // Look up the server if needed
                if (server == null) {
                    server = findScServer(api, ssn, initiators);
                }

                if (server == null) {
                    // Unable to find the server, can't continue
                    throw new DellSCDriverException(SERVER_CREATE_FAIL_MSG);
                }

                ScMappingProfile[] mappingProfiles = api.findMappingProfiles(
                        server.instanceId, scVol.instanceId);
                for (ScMappingProfile mappingProfile : mappingProfiles) {
                    api.deleteMappingProfile(mappingProfile.instanceId);
                }
                volumesUnmapped++;
                LOG.info("Volume '{}' unexported from server '{}'", scVol.name, server.name);
            } catch (StorageCenterAPIException | DellSCDriverException dex) {
                String error = String.format("Error unmapping volume %s: %s", volume.getDisplayName(), dex);
                LOG.error(error);
                errBuffer.append(String.format("%s%n", error));

                if (SERVER_CREATE_FAIL_MSG.equals(dex.getMessage())) {
                    // Game over
                    break;
                }
            }
        }

        task.setMessage(errBuffer.toString());

        if (volumesUnmapped == volumes.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (volumesUnmapped == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }
}
