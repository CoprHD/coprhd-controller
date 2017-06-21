/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.model.NamedRelatedResourceRep;
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

    public final static String RR_PAIR = "Remote Replication Pair";
    public final static String CONSISTENCY_GROUP = "Consistency Group";
    public final static String NO_GROUP = "None";

    /**
     * Return menu options for replication modes supported by the remote replication
     * set(s) matching the given VirtualPool and VirtualArray.  If no set is found,
     * an error is returned.
     *
     * @param virtualArrayId ID of Virtual Array
     * @param virtualPoolId  ID of VirtualPool
     * @return  list of asset options for catalog service order form
     */
    @Asset("remoteReplicationModeForVarrayVpool")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool" })
    public List<AssetOption> getRemoteReplicationModes(AssetOptionsContext ctx,
            URI virtualArrayId, URI virtualPoolId) {
        List<AssetOption> options = new ArrayList<>();

        NamedRelatedResourceRep rrSet = getRrSet(ctx,virtualArrayId, virtualPoolId);

        if (rrSet == null) {
            return options; // no sets or remote replication not supported
        }

        RemoteReplicationSetRestRep rrSetObj = getClient(ctx).
                getRemoteReplicationSetsRestRep(rrSet.getId().toString());

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
    @Asset("remoteReplicationGroupForVarrayVpool")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool" })
    public List<AssetOption> getRemoteReplicationGroupForVarrayVpool(AssetOptionsContext ctx,
            URI virtualArrayId, URI virtualPoolId) {

        NamedRelatedResourceRep rrSet = getRrSet(ctx,virtualArrayId, virtualPoolId);

        if (rrSet == null) {
            return new ArrayList<>(); // no sets or remote replication not supported
        }

        return createNamedResourceOptions(getClient(ctx).
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
        validateStorageSystemTypes(options,ctx,null);
        return options;
    }

    /**
     * Return menu options for the storage system types (e.g.: VNX, etc)
     * of discovered storage systems, but exclude VMAX
     *
     * @return  list of asset options for catalog service order form
     */
    @Asset("storageSystemTypeNoVmax")
    public List<AssetOption> getStorageSystemTypeNoVmax(AssetOptionsContext ctx) {
        List<AssetOption> options = getStorageSystemType(ctx); // use existing provider method
        String vmaxTypeName = StorageSystem.Type.vmax.name();
        Iterator<AssetOption> itr = options.iterator();
        while (itr.hasNext()) {
            if (itr.next().value.equalsIgnoreCase(vmaxTypeName)) {
                itr.remove();
                break;
            }
        }
        validateStorageSystemTypes(options,ctx,Arrays.asList(vmaxTypeName));
        return options;
    }

    /*
     * Throw exception if no Types available
     */
    private void validateStorageSystemTypes(List<AssetOption> options, AssetOptionsContext ctx, List<String> typesToOmit) {
        if (options.isEmpty()) {
            StringBuffer types = new StringBuffer();
            for (StorageSystemTypeRestRep type: getSupportedStorageSystemTypes(api(ctx))) {
                if ((typesToOmit == null) || !typesToOmit.contains(type.getStorageTypeName())) {
                    types.append(type.getStorageTypeName() + ", ");
                }
            }
            throw new IllegalStateException("No supported storage systems present. Supported types are " +
                    types.substring(0, types.length()-2));
        }
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
    @AssetDependencies("storageSystemTypeNoVmax")
    public List<AssetOption> getSourceStorageSystem(AssetOptionsContext ctx,
            String storageSystemTypeId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        List<StorageSystemRestRep> allStorageSystems = client.storageSystems().getAll();
        Map<String,String> typeIds = getStorageSystemTypeMap(client);

        // find source systems in all sets
        List<NamedRelatedResourceRep> rrSets = getRrSets(ctx);
        List<String> sourceSystemsFromSets = new ArrayList<>();
        for(NamedRelatedResourceRep rrSet: rrSets) {

            RemoteReplicationSetRestRep rrSetObj = client.remoteReplicationSets().
                    getRemoteReplicationSetsRestRep(rrSet.getId().toString());

            if(rrSetObj.getSourceSystems() != null) {
                sourceSystemsFromSets.addAll(rrSetObj.getSourceSystems());
            }
        }

        // make options for storage systems of desired type in source system list
        for (StorageSystemRestRep storageSystem : allStorageSystems) {
            if(storageSystem.getSystemType() != null &&
                    storageSystem.getSystemType().equals(typeIds.get(storageSystemTypeId)) &&
                    sourceSystemsFromSets.contains(storageSystem.getId().toString())) {
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
    @AssetDependencies({"storageSystemTypeNoVmax","sourceStorageSystem"})
    public List<AssetOption> getTargetStorageSystem(AssetOptionsContext ctx,
            String storageSystemTypeId, URI sourceStorageSystemId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        List<StorageSystemRestRep> storageSystems = client.storageSystems().getAll();
        Map<String,String> typeIds = getStorageSystemTypeMap(client);

        // find target systems in same set as source system
        List<NamedRelatedResourceRep> rrSets = getRrSets(ctx);
        List<String> targetSystems = new ArrayList<>();
        for(NamedRelatedResourceRep rrSet: rrSets) {

            RemoteReplicationSetRestRep rrSetObj = client.remoteReplicationSets().
                    getRemoteReplicationSetsRestRep(rrSet.getId().toString());

            if ((rrSetObj.getSourceSystems() != null) &&
                    (rrSetObj.getSourceSystems().contains(sourceStorageSystemId.toString())) &&
                    (rrSetObj.getTargetSystems() != null)) {
                targetSystems.addAll(rrSetObj.getTargetSystems());
            }
        }

        // make options for systems of desired type, that are in target system list
        for (StorageSystemRestRep storageSystem : storageSystems) {
            if( !storageSystem.getId().equals(sourceStorageSystemId) && // skip src array
                    (storageSystem.getSystemType() != null) &&
                    storageSystem.getSystemType().equals(typeIds.get(storageSystemTypeId)) &&
                    targetSystems.contains(storageSystem.getId().toString())) {
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
    @AssetDependencies("storageSystemTypeNoVmax")
    public List<AssetOption> getRemoteReplicationModeForArrayType(AssetOptionsContext ctx,
            URI storageSystemTypeUri) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        List<NamedRelatedResourceRep> rrSets = getRrSets(ctx,storageSystemTypeUri);

        Set<String> allModes = new HashSet<>();
        for(NamedRelatedResourceRep rrSet : rrSets) {

            RemoteReplicationSetRestRep rrSetObj = client.remoteReplicationSets().
                    getRemoteReplicationSetsRestRep(rrSet.getId().toString());

            Set<String> modesForSet = rrSetObj.getSupportedReplicationModes();
            allModes.addAll(modesForSet);
        }

        for(String mode : allModes) {
            options.add(new AssetOption(mode,mode.toUpperCase()));
        }

        return options;
    }

    /**
     * Return menu options for all remote replication sets
     *  with the selected storage system type (VMAX, VNX, etc)
     *
     * @param storageSystemTypeUri The URI of the storage system type (VMAX, VNX, etc.)
     * @return list of asset options for catalog service order form
     */
    @Asset("remoteReplicationSetsForArrayType")
    @AssetDependencies("storageSystemType")
    public List<AssetOption> getRemoteReplicationSetsForArrayType(AssetOptionsContext ctx,
            URI storageSystemTypeUri) {
        List<NamedRelatedResourceRep> rrSets = getRrSets(ctx,storageSystemTypeUri);
        return createNamedResourceOptions(rrSets);
    }

    /**
     * Return menu options for groupings of pairs (either CG or Pair)
     *
     * @return list of asset options for catalog service order form
     */
    @Asset("remoteReplicationCgOrPair")
    public List<AssetOption> getRemoteReplicationCgOrPair(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        options.add(new AssetOption(CONSISTENCY_GROUP,CONSISTENCY_GROUP));
        options.add(new AssetOption(RR_PAIR,RR_PAIR));
        return options;
    }

    /**
     * Return menu options for all RR groups in Set
     *
     * @return list of asset options for catalog service order form
     */
    @Asset("remoteReplicationGroupForSet")
    @AssetDependencies({"remoteReplicationSetsForArrayType"})
    public List<AssetOption> getRemoteReplicationGroupForSet(AssetOptionsContext ctx,
            URI rrSet) {
        List<NamedRelatedResourceRep> groups = getClient(ctx).getGroupsForSet(rrSet).
                getRemoteReplicationGroups();
        List<AssetOption>  options = new ArrayList<>();
        options.add(new AssetOption(NO_GROUP,NO_GROUP));
        options.addAll(createNamedResourceOptions(groups));
        return options;
    }

    /**
     * Return menu options for remote replication pairs
     *
     * @return list of asset options for catalog service order form
     */
    @Asset("remoteReplicationPairsOrCGs")
    @AssetDependencies({"remoteReplicationSetsForArrayType","remoteReplicationGroupForSet","remoteReplicationCgOrPair"})
    public List<AssetOption> getRemoteReplicationPair(AssetOptionsContext ctx,
            URI setId, String groupId, String cgOrPairs) {
        if (CONSISTENCY_GROUP.equals(cgOrPairs)) {
            if(NO_GROUP.equals(groupId)) {
                // get CGs in set
                return createNamedResourceOptions(api(ctx).remoteReplicationSets().
                        listRemoteReplicationSetCGs(setId).getConsistencyGroupList());
            } else {
                // get CGs in specified group
                return createNamedResourceOptions(api(ctx).remoteReplicationGroups().
                        listConsistencyGroups(groupId).getConsistencyGroupList());
            }
        }
        if (RR_PAIR.equals(cgOrPairs)) {
            if(NO_GROUP.equals(groupId)) {
                // get pairs in set
                return createNamedResourceOptions(api(ctx).remoteReplicationSets().
                        listRemoteReplicationPairs(setId).getRemoteReplicationPairs());
            } else {
                // get pairs in the selected group
                return createNamedResourceOptions(api(ctx).remoteReplicationGroups().
                        listRemoteReplicationPairs(groupId).getRemoteReplicationPairs());
            }
        }
        throw new IllegalStateException("Select either Consistency Groups or Pairs");
    }

    /*
     * Get RR sets
     */
    private List<NamedRelatedResourceRep> getRrSets(AssetOptionsContext ctx) {

        List<NamedRelatedResourceRep> rrSets = getClient(ctx).
                listRemoteReplicationSets().getRemoteReplicationSets();
        return validateRrSets(ctx,rrSets);
    }

     /*
      *  Get RR sets for storage type
      */
    private List<NamedRelatedResourceRep> getRrSets(AssetOptionsContext ctx,
            URI storageSystemTypeUri) {
        List<NamedRelatedResourceRep> rrSets = getClient(ctx).
                listRemoteReplicationSets(storageSystemTypeUri).getRemoteReplicationSets();
        return validateRrSets(ctx,rrSets);
    }

    /*
     * Return valid RR Sets.
     */
    private List<NamedRelatedResourceRep> validateRrSets(AssetOptionsContext ctx,
            List<NamedRelatedResourceRep> rrSets) {

        removeUnreachableSets(rrSets,ctx);

        if ((rrSets == null) || rrSets.isEmpty()) {
            throw new IllegalStateException("No Remote Replication Set was found containing storage systems.");
        }

        return rrSets;
    }

    /* Retrieve a map of the storage system IDs & types supported in ViPR.  The map
     * returned contains entries with type names as keys, as well as the same
     * entries with ID as key (to allow lookups in both directions).
     */
    private Map<String,String> getStorageSystemTypeMap(ViPRCoreClient client) {
        List<StorageSystemTypeRestRep> storageSystemTypes = getSupportedStorageSystemTypes(client);
        Map<String,String> typeIds = new HashMap<>();
        for(StorageSystemTypeRestRep type : storageSystemTypes) {
            typeIds.put(type.getStorageTypeName(),type.getStorageTypeId()); // store names as keys
            typeIds.put(type.getStorageTypeId(),type.getStorageTypeName()); // also store IDs as keys
        }
        return typeIds;
    }

    /*
     * Get supported Storage System types for RR operations
     */
    private List<StorageSystemTypeRestRep> getSupportedStorageSystemTypes(ViPRCoreClient client) {
        List<StorageSystemTypeRestRep> storageSystemTypes =
                client.storageSystemType().listStorageSystemTypes("block").getStorageSystemTypes();
        List<StorageSystemTypeRestRep> storageSystemFileTypes =
                client.storageSystemType().listStorageSystemTypes("file").getStorageSystemTypes();
        storageSystemTypes.addAll(storageSystemFileTypes);
        return storageSystemTypes;
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
     * Get the reachable Remote Replication Set for the given VirtualPool & VirtualArray. Throws
     * exception if no (or more than one) set is found.
     */
    private NamedRelatedResourceRep getRrSet(AssetOptionsContext ctx,URI virtualArrayId, URI virtualPoolId) {

        BlockVirtualPoolRestRep vpool = api(ctx).blockVpools().get(virtualPoolId);
        if ((vpool == null) || (vpool.getProtection().getRemoteReplicationParam() == null)) {
            return null;
        }

        List<NamedRelatedResourceRep> rrSets = getClient(ctx).
                listRemoteReplicationSets(virtualArrayId,virtualPoolId).getRemoteReplicationSets();

        removeUnreachableSets(rrSets,ctx);

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

    /*
     * remove unreachable sets from list
     */
    private void removeUnreachableSets(List<NamedRelatedResourceRep> rrSets, AssetOptionsContext ctx) {
        ViPRCoreClient client = api(ctx);
        Iterator<NamedRelatedResourceRep> it = rrSets.iterator();
        while (it.hasNext()) {

            RemoteReplicationSetRestRep rrSetObj = client.remoteReplicationSets().
                    getRemoteReplicationSetsRestRep(it.next().getId().toString());

            if((rrSetObj != null) && (rrSetObj.getReachable() != null) && !rrSetObj.getReachable()) {
                it.remove();
            }
        }
    }

    /*
     * Get client for set operations
     */
    RemoteReplicationSets getClient(AssetOptionsContext ctx) {
        return api(ctx).remoteReplicationSets();
    }

}
