/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.RemoteReplicationSets;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;

@Component
@AssetNamespace("vipr")
public class RemoteReplicationProvider extends BaseAssetOptionsProvider {

    private RemoteReplicationSets setsForPoolVarray;

    /**
     * Return menu options for replication modes supported by the remote replication
     * set(s) matching the given VirtualPool and VirtualArray.  If no set is found,
     * an error is returned.
     *
     * @param virtualArrayId ID of Virtual Array
     * @param virtualPoolId  ID of VirtualPool
     * @return  list of asset options for catalog service order form
     */
    @Asset("remoteReplicationMode")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool" })
    public List<AssetOption>
    getRemoteReplicationModes(AssetOptionsContext ctx, URI virtualArrayId, URI virtualPoolId) {

        RemoteReplicationSetRestRep rrSet = getRrSet(ctx,virtualArrayId, virtualPoolId);

        RemoteReplicationSetRestRep rrSetObj = api(ctx).remoteReplicationSets().
                getRemoteReplicationSetsRestRep(rrSet.getId().toString());

        List<AssetOption> options = new ArrayList<>();
        for(String mode : rrSetObj.getSupportedReplicationModes()) {
            options.add(new AssetOption(mode,mode));
        }
        return options;
    }

    /**
     * Return menu options for replication groups supported by the remote replication
     * set(s) matching the given VirtualPool and VirtualArray.  If no set is found,
     * an error is returned.
     *
     * @param virtualArrayId ID of Virtual Array
     * @param virtualPoolId  ID of VirtualPool
     * @return  list of asset options for catalog service order form
     */
    @Asset("remoteReplicationGroup")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool" })
    public List<AssetOption> getRemoteReplicationGroups(AssetOptionsContext ctx,
            URI virtualArrayId, URI virtualPoolId) {

        RemoteReplicationSetRestRep rrSet = getRrSet(ctx,virtualArrayId, virtualPoolId);

        return createNamedResourceOptions(setsForPoolVarray.
                getGroupsForSet(rrSet.getId()).getRemoteReplicationGroups());
    }

    /**
     * Return menu options for the storage system types (e.g.: VMAX, VNX, etc)
     * of discovered storage systems
     *
     * @return  list of asset options for catalog service order form
     */
    @Asset("storageSystemType")
    public List<AssetOption> getStorageSystemType(AssetOptionsContext ctx) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = new ArrayList<>();

        List<StorageSystemRestRep> storageSystems = client.storageSystems().getAll();

        Map<String,String> typeIds = getStorageSystemTypeMap(client);

        for (StorageSystemRestRep storageSystem : storageSystems) {
            String type = storageSystem.getSystemType();
            if(typeIds.containsKey(type)) {
                options.add(new AssetOption(typeIds.get(type),type.toUpperCase()));
                typeIds.remove(type); // to prevent duplicates
            }
        }
        return options;
    }

    /**
     * Return menu options for storage systems to allow user to select one as a
     * source for remote replication.  Options include only storage systems
     * matching the selected type (VMAX, VNX, etc)
     *
     * @param storageSystemTypeId ID of the storage system type
     * @return  list of asset options for catalog service order form
     */
    @Asset("sourceStorageSystem")
    @AssetDependencies("storageSystemType")
    public List<AssetOption> getSourceStorageSystem(AssetOptionsContext ctx, String storageSystemTypeId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        List<StorageSystemRestRep> storageSystems = client.storageSystems().getAll();
        Map<String,String> typeIds = getStorageSystemTypeMap(client);
        for (StorageSystemRestRep storageSystem : storageSystems) {
            if(storageSystem.getSystemType() != null &&
                    storageSystem.getSystemType().equals(typeIds.get(storageSystemTypeId))) {
                options.add(new AssetOption(storageSystem.getId(), storageSystem.getName()));
            }
        }
        return options;
    }

    /**
     * Return menu options for storage systems to allow user to select one as a
     * target for remote replication.  Options include only storage systems
     * matching the selected type (VMAX, VNX, etc).  System selected as source
     * is not included
     *
     * @param storageSystemTypeId ID of the storage system type
     * @param sourceStorageSystemId ID of the storage system selected as the source
     * @return  list of asset options for catalog service order form
     */
    @Asset("targetStorageSystem")
    @AssetDependencies({"storageSystemType","sourceStorageSystem"})
    public List<AssetOption> getTargetStorageSystem(AssetOptionsContext ctx,
            String storageSystemTypeId, URI sourceStorageSystemId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        List<StorageSystemRestRep> storageSystems = client.storageSystems().getAll();
        Map<String,String> typeIds = getStorageSystemTypeMap(client);
        for (StorageSystemRestRep storageSystem : storageSystems) {
            if( !storageSystem.getId().equals(sourceStorageSystemId) && // skip src array
                    (storageSystem.getSystemType() != null) &&
                    storageSystem.getSystemType().equals(typeIds.get(storageSystemTypeId))) {
                options.add(new AssetOption(storageSystem.getId(), storageSystem.getName()));
            }
        }
        return options;
    }

    /**
     * Return menu options for storage ports associated with the selected source storage system
     *
     * @param sourceStorageSystemId ID of the selected source storage system
     * @return  list of asset options for catalog service order form
     */
    @Asset("sourceStoragePorts")
    @AssetDependencies("sourceStorageSystem")
    public List<AssetOption> getSourceStoragePorts(AssetOptionsContext ctx, URI sourceStorageSystemId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        List<StoragePortRestRep> storagePorts = client.storagePorts().getByStorageSystem(sourceStorageSystemId);
        for (StoragePortRestRep storagePort : storagePorts) {
            options.add(new AssetOption(storagePort.getPortNetworkId(), getPortDisplayName(storagePort)));
        }
        return options;
    }

    /**
     * Return menu options for storage ports associated with the selected target storage system
     *
     * @param targetStorageSystemId ID of the selected target storage system
     * @return  list of asset options for catalog service order form
     */
    @Asset("targetStoragePorts")
    @AssetDependencies("targetStorageSystem")
    public List<AssetOption> getTargetStoragePorts(AssetOptionsContext ctx, URI targetStorageSystemId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        List<StoragePortRestRep> storagePorts = client.storagePorts().getByStorageSystem(targetStorageSystemId);
        for (StoragePortRestRep storagePort : storagePorts) {
            options.add(new AssetOption(storagePort.getPortNetworkId(), getPortDisplayName(storagePort)));
        }
        return options;
    }

    /**
     * Return menu options for all replication modes of the remote replication
     *  sets associated with the selected storage system type (VMAX, VNX, etc)
     *
     * @param storageSystemTypeUri The URI of the storage system type (VMAX, VNX, etc.)
     * @return list of asset options for catalog service order form
     */
    @Asset("remoteReplicationModeForArrayType")
    @AssetDependencies("storageSystemType")
    public List<AssetOption> getRemoteReplicationModeForArrayType(AssetOptionsContext ctx,
            URI storageSystemTypeUri) {
        List<AssetOption> options = Lists.newArrayList();

        List<RemoteReplicationSetRestRep> rrSets = api(ctx).remoteReplicationSets().
                listRemoteReplicationSets(storageSystemTypeUri).getRemoteReplicationSets();

        Set<String> allModes = new HashSet<>();
        for(RemoteReplicationSetRestRep rrSet : rrSets) {
            Set<String> modesForSet = rrSet.getSupportedReplicationModes();
            allModes.addAll(modesForSet);
        }

        for(String mode : allModes) {
            options.add(new AssetOption(mode,mode.toUpperCase()));
        }

        return options;
    }

    /* Retrieve a map of the storage system IDs & types supported in ViPR.  The map
     * returned contains entries with type names as keys, as well as the same
     * entries with ID as key to allow lookups in both directions.
     */
    private Map<String,String> getStorageSystemTypeMap(ViPRCoreClient client) {

        List<StorageSystemTypeRestRep> storageSystemTypes =
                client.storageSystemType().listStorageSystemTypes("block").getStorageSystemTypes();

        List<StorageSystemTypeRestRep> storageSystemFileTypes =
                client.storageSystemType().listStorageSystemTypes("file").getStorageSystemTypes();

        storageSystemTypes.addAll(storageSystemFileTypes);

        Map<String,String> typeIds = new HashMap<>();

        for(StorageSystemTypeRestRep type : storageSystemTypes) {
            typeIds.put(type.getStorageTypeName(),type.getStorageTypeId()); // store names as keys
            typeIds.put(type.getStorageTypeId(),type.getStorageTypeName()); // also store IDs as keys
        }
        return typeIds;
    }

    /*
     * Get a pretty name for a storage port
     */
    private String getPortDisplayName(StoragePortRestRep port) {
        StringBuilder portName = new StringBuilder();
        if (port.getPortName() != null) {
            portName.append(port.getPortName()); // add port name
        }
        if(port.getPortNetworkId() != null) {
            if (portName.length() == 0) {
                portName.append(port.getPortNetworkId()); // set to net ID
            } else {
                portName.append(" (").append(port.getPortNetworkId()).append(")"); // add net ID
            }
        }
        if(portName.length() == 0) {
            if (port.getName() != null && !port.getName().isEmpty()) {
                portName.append(port.getName()); // else use name
            } else {
                portName.append(port.getId()); // use ID if all else fails
            }
        }
        return portName.toString();
    }

    /*
     * Get the Remote Replication Set for the given VirtualPool & VirtualArray.Throws
     * exception if no (or more than one) set is found.
     */
    private RemoteReplicationSetRestRep getRrSet(AssetOptionsContext ctx,URI virtualArrayId, URI virtualPoolId) {

        setsForPoolVarray = api(ctx).remoteReplicationSets();

        BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);
        if ((vpool == null) || (vpool.getProtection().getRemoteReplicationParam() == null)) {
            return null;
        }

        List<RemoteReplicationSetRestRep> rrSets = setsForPoolVarray.
                listRemoteReplicationSets(virtualArrayId,virtualPoolId).getRemoteReplicationSets();

        if ((rrSets == null) || rrSets.isEmpty()) {
            throw new IllegalStateException("No RemoteReplicationSet was found for the selected " +
                    "VirtualArray and VirtualPool");
        }

        if (rrSets.size() > 1) {
            throw new IllegalStateException("Invalid number of RemoteReplicationSets (" + rrSets.size() +
                    ") found for VirtualArray (" + virtualArrayId + ") and VirtualPool (" + virtualPoolId +
                    ").  RemoteReplicationSets found were: " + rrSets);
        }

        return rrSets.get(0);
    }

}
