/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep.RecoverPointRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.VirtualPoolUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BlockProviderUtils {

    public static final String VPLEX_DISTRIBUTED = "VPLEX_DISTRIBUTED";

    public static List<? extends BlockObjectRestRep> getBlockResources(ViPRCoreClient viprClient, URI tenantId, URI hostOrClusterId,
            boolean onlyMounted) {
        List<URI> hostIds = buildHostIdsList(viprClient, hostOrClusterId, onlyMounted);

        List<BlockObjectRestRep> volumes = Lists.newArrayList();
        for (BlockObjectRestRep volume : getExportedBlockResources(viprClient, tenantId, hostOrClusterId)) {
            boolean isMounted = isMounted(hostIds, volume);
            if (onlyMounted && isMounted) {
                volumes.add(volume);
            }
            else if (!onlyMounted && !isMounted) {
                volumes.add(volume);
            }
        }
        return volumes;
    }

    public static List<VolumeRestRep> getBlockVolumes(ViPRCoreClient viprClient, URI tenantId, URI hostOrClusterId,
            boolean onlyMounted) {
        List<URI> hostIds = buildHostIdsList(viprClient, hostOrClusterId, onlyMounted);

        List<VolumeRestRep> volumes = Lists.newArrayList();
        for (VolumeRestRep volume : getExportedBlockVolumes(viprClient, tenantId, hostOrClusterId)) {
            boolean isMounted = isMounted(hostIds, volume);
            if (onlyMounted && isMounted) {
                volumes.add(volume);
            }
            else if (!onlyMounted && !isMounted) {
                volumes.add(volume);
            }
        }
        return volumes;
    }
    
    /**
     * Method to return all volumes associated with a specific datastore
     * 
     * @param viprClient 
     * @param tenantId 
     * @param hostOrClusterId 
     * @param datastore name
     * 
     * @return list of volumes associated with specified datastore
     */
    public static List<VolumeRestRep> getBlockVolumesForDatastore(ViPRCoreClient viprClient, URI tenantId, URI hostOrClusterId,
            String datastore) {
        List<URI> hostIds = buildHostIdsList(viprClient, hostOrClusterId, true);

        List<VolumeRestRep> volumes = Lists.newArrayList();
        for (VolumeRestRep volume : getExportedBlockVolumes(viprClient, tenantId, hostOrClusterId)) {
            for (URI hostId : hostIds) {
                String ds = KnownMachineTags.getBlockVolumeVMFSDatastore(hostId, volume);
                if (StringUtils.isNotBlank(ds) && ds.equals(datastore)) {
                    volumes.add(volume);
                }
            }
        }
        return volumes;
    }

    protected static List<URI> buildHostIdsList(ViPRCoreClient viprClient, URI hostOrClusterId, boolean onlyMounted) {
        List<URI> hostIds = Lists.newArrayList();

        if (onlyMounted) {
            // we're looking for volumes mounted on this host/cluster
            hostIds.add(hostOrClusterId);
        }
        else {
            // we're looking for volumes that are not mounted on this host/cluster
            hostIds.addAll(HostProvider.getHostIds(viprClient, hostOrClusterId));

            // Add Cluster ID to the end of the list of host ids
            URI clusterId = getClusterId(viprClient, hostOrClusterId);
            if (clusterId != null) {
                hostIds.add(clusterId);
            }
        }

        return hostIds;
    }

    protected static URI getClusterId(ViPRCoreClient viprClient, URI hostOrClusterId) {
        if (BlockStorageUtils.isCluster(hostOrClusterId)) {
            // if the id is a cluster id we can just add it
            return hostOrClusterId;
        }
        else {
            // if the id is not a cluster id we need to get the cluster id from the host and add that
            HostRestRep host = viprClient.hosts().get(hostOrClusterId);
            if (host.getCluster() != null) {
                return host.getCluster().getId();
            }
        }
        return null;
    }

    public static List<? extends BlockObjectRestRep> getExportedBlockResources(ViPRCoreClient client, URI tenantId, URI hostOrClusterId) {
        List<ExportGroupRestRep> exports = getExportsForHostOrCluster(client, tenantId, hostOrClusterId);
        Set<URI> volumeIds = getExportedResourceIds(exports, ResourceType.VOLUME);
        Set<URI> snapshotIds = getExportedResourceIds(exports, ResourceType.BLOCK_SNAPSHOT);

        List<BlockObjectRestRep> resources = new ArrayList<>();
        resources.addAll(client.blockVolumes().getByIds(volumeIds));
        resources.addAll(client.blockSnapshots().getByIds(snapshotIds));
        return resources;
    }

    public static List<VolumeRestRep> getExportedBlockVolumes(ViPRCoreClient client, URI tenantId, URI hostOrClusterId) {
        List<ExportGroupRestRep> exports = getExportsForHostOrCluster(client, tenantId, hostOrClusterId);
        Set<URI> volumeIds = getExportedResourceIds(exports, ResourceType.VOLUME);
        return client.blockVolumes().getByIds(volumeIds);
    }

    public static List<ExportGroupRestRep> getExportsForHostOrCluster(ViPRCoreClient client, URI tenantId, URI hostOrClusterId) {
        if (BlockStorageUtils.isHost(hostOrClusterId)) {
            return client.blockExports().findContainingHost(hostOrClusterId, null, null);
        }
        else {
            return client.blockExports().findByCluster(hostOrClusterId, null, null);
        }
    }

    public static Set<URI> getExportedResourceIds(Collection<ExportGroupRestRep> exports, ResourceType type) {
        Set<URI> ids = new HashSet<>();
        for (ExportGroupRestRep export : exports) {
            // export volumes can be volumes or snapshots
            for (ExportBlockParam resource : export.getVolumes()) {
                if (ResourceType.isType(type, resource.getId())) {
                    ids.add(resource.getId());
                }
            }
        }
        return ids;
    }

    public static boolean isMounted(Collection<URI> hostIds, BlockObjectRestRep volume) {
        for (URI hostId : hostIds) {
            if (isMounted(hostId, volume)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMounted(URI hostId, BlockObjectRestRep volume) {
        String mountPoint = KnownMachineTags.getBlockVolumeMountPoint(hostId, volume);
        if (StringUtils.isNotBlank(mountPoint)) {
            return true;
        }

        String datastore = KnownMachineTags.getBlockVolumeVMFSDatastore(hostId, volume);

        return StringUtils.isNotBlank(datastore);
    }

    public static boolean isLocalMirrorSupported(BlockVirtualPoolRestRep virtualPool) {
        boolean supported = (virtualPool.getProtection() != null) &&
                (virtualPool.getProtection().getContinuousCopies() != null) &&
                (virtualPool.getProtection().getContinuousCopies().getMaxMirrors() > 0);

        if (virtualPool.getHighAvailability() != null &&
                VirtualPoolUtils.isVplexDistributed(virtualPool.getHighAvailability().getType()) &&
                virtualPool.getProtection() != null &&
                virtualPool.getProtection().getContinuousCopies() != null) {

            supported = ((virtualPool.getProtection().getContinuousCopies().getMaxMirrors() > 0 &&
                    virtualPool.getProtection().getContinuousCopies().getVpool() != null) ||
                    (virtualPool.getProtection().getContinuousCopies().getHaMaxMirrors() != null &&
                            virtualPool.getProtection().getContinuousCopies().getHaMaxMirrors() > 0 &&
                    virtualPool.getProtection().getContinuousCopies().getHaVpool() != null));

        }
        return supported;
    }

    public static boolean isLocalSnapshotSupported(BlockVirtualPoolRestRep virtualPool) {
        return (virtualPool.getProtection() != null) &&
                (virtualPool.getProtection().getSnapshots() != null) &&
                (virtualPool.getProtection().getSnapshots().getMaxSnapshots() > 0);
    }

    public static boolean isRemoteSnapshotSupported(VolumeRestRep volume) {
        return getVolumeRPRep(volume) != null;
    }

    public static boolean isMetadataVolume(VolumeRestRep volume) {
        PersonalityTypes personality = getVolumePersonality(volume);
        return personality != null && PersonalityTypes.METADATA.equals(personality);
    }

    public static boolean isRPSourceVolume(VolumeRestRep volume) {
        PersonalityTypes personality = getVolumePersonality(volume);
        return personality != null && PersonalityTypes.SOURCE.equals(personality);
    }

    public static boolean isRPTargetVolume(VolumeRestRep volume) {
        PersonalityTypes personality = getVolumePersonality(volume);
        return personality != null && PersonalityTypes.TARGET.equals(personality);
    }
    
    public static boolean isSnapshotSessionSupportedForVolume(VolumeRestRep volume) {        
        return ((volume.getSupportsSnapshotSessions() != null) && volume.getSupportsSnapshotSessions());
    }
        
    public static boolean isSnapshotSessionSupportedForCG(BlockConsistencyGroupRestRep cg) {        
        return ((cg.getSupportsSnapshotSessions() != null) && cg.getSupportsSnapshotSessions());
    }
    
    public static RecoverPointRestRep getVolumeRPRep(VolumeRestRep volume) {
        if (volume.getProtection() != null &&
                volume.getProtection().getRpRep() != null) {
            return volume.getProtection().getRpRep();
        }
        return null;
    }

    public static PersonalityTypes getVolumePersonality(VolumeRestRep volume) {
        RecoverPointRestRep rp = getVolumeRPRep(volume);
        if (rp != null && rp.getPersonality() != null) {
            return PersonalityTypes.valueOf(rp.getPersonality());
        }
        return null;
    }

    public static boolean isVpoolProtectedByVarray(BlockVirtualPoolRestRep vpool, URI targetVArray) {
        return targetVArray != null && isVplexDistributedVPool(vpool) && targetVArray.equals(getHAVarrayId(vpool));
    }

    public static boolean isVplexDistributedVPool(BlockVirtualPoolRestRep vpool) {
        return vpool != null && VPLEX_DISTRIBUTED.equalsIgnoreCase(getHAType(vpool));
    }

    public static boolean isSRDFTargetVolume(BlockObjectRestRep blockObj) {
        if (blockObj instanceof VolumeRestRep) {
            VolumeRestRep volume = (VolumeRestRep) blockObj;
            return volume.getProtection() != null &&
                    volume.getProtection().getSrdfRep() != null &&
                    volume.getProtection().getSrdfRep().getPersonality().equals(PersonalityTypes.TARGET.toString());
        }
        return false;
    }

    public static URI getHAVarrayId(BlockVirtualPoolRestRep volumeVpool) {
        if (volumeVpool != null &&
                volumeVpool.getHighAvailability() != null &&
                volumeVpool.getHighAvailability().getHaVirtualArrayVirtualPool() != null &&
                volumeVpool.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray() != null) {
            return volumeVpool.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray();
        }
        return null;
    }

    public static String getHAType(BlockVirtualPoolRestRep volumeVpool) {
        if (volumeVpool != null &&
                volumeVpool.getHighAvailability() != null &&
                volumeVpool.getHighAvailability().getType() != null) {
            return volumeVpool.getHighAvailability().getType();
        }
        return StringUtils.EMPTY;
    }

    public static boolean isVplex(StorageSystemRestRep storageSystem) {
        return StringUtils.equals(storageSystem.getSystemType(), "vplex");
    }

    public static boolean isVplex(StorageSystemConnectivityRestRep connectivity) {
        for (String connectionType : connectivity.getConnectionTypes()) {
            if (StringUtils.equals(connectionType, "vplex")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVnxBlock(StorageSystemRestRep storageSystem) {
        return StringUtils.equals(storageSystem.getSystemType(), "vnxblock");
    }

    public static boolean isVmax(StorageSystemRestRep storageSystem) {
        return StringUtils.equals(storageSystem.getSystemType(), "vmax");
    }

    public static boolean isRegistered(DiscoveredSystemObjectRestRep system) {
        return (system != null) && "REGISTERED".equals(system.getRegistrationStatus());
    }

    public static Map<URI, String> volumeNameMap(List<VolumeRestRep> volumes) {
        Map<URI, String> volumeNameMap = Maps.newHashMap();
        for (VolumeRestRep volume : volumes) {
            volumeNameMap.put(volume.getId(), volume.getName());
        }
        return volumeNameMap;
    }

    public static boolean isSupportedVPool(BlockVirtualPoolRestRep vpool) {
        return vpool != null && vpool.getMultiVolumeConsistent() != null && vpool.getMultiVolumeConsistent();
    }

    public static boolean isType(URI uri, String name) {
        return uri.toString().startsWith("urn:storageos:" + name);
    }

    /**
     * returns the list of application sub groups for an application
     * 
     * @param viprClient
     * @param applicationId
     * @return
     */
    public static Set<String> getApplicationReplicationGroupNames(ViPRCoreClient viprClient, URI applicationId) {
        VolumeGroupRestRep application = viprClient.application().getApplication(applicationId);
        Set<String> visibleGroups = new HashSet<String>();
        Set<String> groupNames = application.getReplicationGroupNames();
        for (String grp : groupNames) {
            if (!isRPTargetReplicationGroup(grp)) {
                visibleGroups.add(grp);
            }
        }
        return visibleGroups;
    }

    /**
     * returns true if the replication group is a RP Target replication group
     * 
     * @param group
     * @return
     */
    public static boolean isRPTargetReplicationGroup(String group) {
        if (group != null) {
            String[] parts = StringUtils.split(group, '-');
            if (parts.length > 1 && parts[parts.length - 1].equals("RPTARGET")) {
                return true;
            }
        }
        return false;
    }

    /**
     * returns true if the volume is a RP source or target volume
     * 
     * @param vol
     * @return
     */
    public static boolean isVolumeRP(VolumeRestRep vol) {
        if (vol.getProtection() != null && vol.getProtection().getRpRep() != null) {
            return true;
        }
        return false;
    }

    /**
     * return true if the volume is a vplex volume
     * 
     * @param vol
     * @return
     */
    public static boolean isVolumeVPLEX(VolumeRestRep vol) {
        if (vol.getHaVolumes() != null && !vol.getHaVolumes().isEmpty()) {
            return true;
        }
        return false;
    }
}
