/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Component
@AssetNamespace("vipr")
public class VirtualArrayProvider extends BaseAssetOptionsProvider {
    @Asset("virtualArray")
    public List<AssetOption> getVirtualArray(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).varrays().getByTenant(ctx.getTenant()));
    }

    @Asset("virtualArray")
    @AssetDependencies("unmanagedBlockStorageSystem")
    public List<AssetOption> getBlockVirtualArrays(AssetOptionsContext ctx, URI storageSystem) {
        return getVirtualArrayForStorageSystem(ctx, storageSystem);
    }

    @Asset("virtualArray")
    @AssetDependencies({ "mobilityGroupMethod", "project" })
    public List<AssetOption> getBlockVirtualArrays(AssetOptionsContext ctx, String mobilityGroupMethod, URI project) {
        if (mobilityGroupMethod.equalsIgnoreCase(BlockProvider.INGEST_AND_MIGRATE_OPTION_KEY)) {
            return createBaseResourceOptions(api(ctx).varrays().getByTenant(ctx.getTenant()));
        } else {
            return Lists.newArrayList();
        }
    }

    @Asset("virtualArray")
    @AssetDependencies("fileStorageSystem")
    public List<AssetOption> getFileVirtualArrays(AssetOptionsContext ctx, URI storageSystem) {
        return getVirtualArrayForStorageSystem(ctx, storageSystem);
    }

    protected List<AssetOption> getVirtualArrayForStorageSystem(AssetOptionsContext context, URI storageSystem) {
        ViPRCoreClient client = api(context);
        Set<String> virtualArrayIds = Sets.newHashSet();

        for (StoragePortRestRep storagePortRestRep : client.storagePorts().getByStorageSystem(storageSystem)) {
            virtualArrayIds.addAll(storagePortRestRep.getAssignedVirtualArrays());
            virtualArrayIds.addAll(storagePortRestRep.getConnectedVirtualArrays());
        }

        List<VirtualArrayRestRep> virtualArrays = client.varrays().getByIds(ResourceUtils.uris(virtualArrayIds));
        filterByContextTenant(virtualArrays, client.varrays().getByTenant(context.getTenant()));
        return createBaseResourceOptions(virtualArrays);
    }

    @Asset("virtualArray")
    @AssetDependencies({ "linuxHost" })
    public List<AssetOption> getVirtualArrayForLinux(AssetOptionsContext context, URI linuxHostOrCluster) {
        return getVirtualArray(context, linuxHostOrCluster);
    }

    @Asset("virtualArray")
    @AssetDependencies({ "aixHost" })
    public List<AssetOption> getVirtualArrayForAix(AssetOptionsContext context, URI aixHostOrCluster) {
        return getVirtualArray(context, aixHostOrCluster);
    }

    @Asset("virtualArray")
    @AssetDependencies({ "windowsHost" })
    public List<AssetOption> getVirtualArrayForWindows(AssetOptionsContext context, URI windowsHostOrCluster) {
        return getVirtualArray(context, windowsHostOrCluster);
    }

    @Asset("virtualArray")
    @AssetDependencies({ "host" })
    public List<AssetOption> getVirtualArray(AssetOptionsContext context, URI hostOrClusterId) {
        ViPRCoreClient client = api(context);
        List<URI> hostIds = HostProvider.getHostIds(client, hostOrClusterId);
        Map<URI, VirtualArrayRestRep> virtualArrays = null;
        Map<URI, VirtualArrayRestRep> allVirtualArrays = Maps.newHashMap();
        for (URI hostId : hostIds) {
            Map<URI, VirtualArrayRestRep> connectedVirtualArrays = ResourceUtils.mapById(client.varrays()
                    .findByConnectedHost(hostId));
            if (virtualArrays == null) {
                virtualArrays = connectedVirtualArrays;
            } else {
                virtualArrays.keySet().retainAll(connectedVirtualArrays.keySet());
            }
            allVirtualArrays.putAll(connectedVirtualArrays);
        }

        // Creates options for the virtual arrays, showing an indication whether the virtual array is only
        // partially connected to the cluster
        List<AssetOption> fullyConnectedOptions = new ArrayList<>();
        List<AssetOption> partiallyConnectedOptions = new ArrayList<>();

        List<VirtualArrayRestRep> varraysByTenant = client.varrays().getByTenant(context.getTenant());

        for (VirtualArrayRestRep varray : allVirtualArrays.values()) {
            if (!contains(varray.getId(), varraysByTenant)) {
                continue;
            }

            boolean fullyConnected = virtualArrays.containsKey(varray.getId());
            if (fullyConnected) {
                fullyConnectedOptions.add(new AssetOption(varray.getId(), varray.getName()));
            } else {
                String label = getMessage("virtualArray.partiallyConnected", varray.getName());
                partiallyConnectedOptions.add(new AssetOption(varray.getId(), label));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(fullyConnectedOptions);
        AssetOptionsUtils.sortOptionsByLabel(partiallyConnectedOptions);
        // Place the fully connected options first
        List<AssetOption> options = new ArrayList<>();
        options.addAll(fullyConnectedOptions);
        options.addAll(partiallyConnectedOptions);
        return options;
    }

    @Asset("virtualArray")
    @AssetDependencies({ "esxHost" })
    public List<AssetOption> getVirtualArrayForVMware(AssetOptionsContext context, URI vmwareHostOrCluster) {
        return getVirtualArray(context, vmwareHostOrCluster);
    }

    @Asset("fileVirtualArray")
    public List<AssetOption> getFileVirtualArrays(AssetOptionsContext context) {
        ViPRCoreClient client = api(context);
        // Get the set of virtual arrays that are associated with file vpools
        Set<URI> varrayIds = new HashSet<>();
        for (FileVirtualPoolRestRep vpool : client.fileVpools().getByTenant(context.getTenant())) {
            varrayIds.addAll(ResourceUtils.refIds(vpool.getVirtualArrays()));
        }
        filterByContextTenant(varrayIds, client.varrays().getByTenant(context.getTenant()));
        return createBaseResourceOptions(client.varrays().getByIds(varrayIds));
    }

    @Asset("fileTargetVirtualArray")
    @AssetDependencies({ "fileFilePolicy", "fileFilesystemAssociation" })
    public List<AssetOption> getFileTargetVirtualArrays(AssetOptionsContext context, URI filePolicy, URI fsId) {
        ViPRCoreClient client = api(context);
        FilePolicyRestRep policyRest = client.fileProtectionPolicies().getFilePolicy(filePolicy);
        if (policyRest.getType().equals("file_snapshot")) {
            VirtualArrayRestRep vArray = null;

            List<AssetOption> options = Lists.newArrayList();
            FileShareRestRep fsObj = client.fileSystems().get(fsId);
            if (fsObj != null) {
                vArray = client.varrays().get(fsObj.getVirtualArray().getId());
                options.add(createBaseResourceOption(vArray));
            }
            return options;
        } else {
            return getFileVirtualArrays(context);
        }
    }

    @Asset("fileTargetVirtualArray")
    @AssetDependencies({ "fileFilePolicy", "unprotectedFilesystem" })
    public List<AssetOption> getTargetVirtualArrays(AssetOptionsContext context, URI filePolicy, URI fsId) {
        ViPRCoreClient client = api(context);
        FilePolicyRestRep policyRest = client.fileProtectionPolicies().getFilePolicy(filePolicy);
        if (policyRest.getType().equals("file_snapshot")) {
            VirtualArrayRestRep vArray = null;

            List<AssetOption> options = Lists.newArrayList();
            FileShareRestRep fsObj = client.fileSystems().get(fsId);
            if (fsObj != null) {
                vArray = client.varrays().get(fsObj.getVirtualArray().getId());
                options.add(createBaseResourceOption(vArray));
            }
            return options;
        } else {
            return getFileVirtualArrays(context);
        }
    }

    @Asset("blockVirtualArray")
    public List<AssetOption> getBlockVirtualArrays(AssetOptionsContext context) {
        ViPRCoreClient client = api(context);
        // Get the set of virtual arrays that are associated with block vpools
        Set<URI> varrayIds = new HashSet<>();
        for (BlockVirtualPoolRestRep vpool : client.blockVpools().getByTenant(context.getTenant())) {
            varrayIds.addAll(ResourceUtils.refIds(vpool.getVirtualArrays()));
        }
        filterByContextTenant(varrayIds, client.varrays().getByTenant(context.getTenant()));
        return createBaseResourceOptions(client.varrays().getByIds(varrayIds));
    }

    @Asset("virtualArrayForMultipleComputeResource")
    @AssetDependencies({ "host" })
    public List<AssetOption> getVirtualArrayforMultipleHost(AssetOptionsContext context, String hostOrClusterIds) {
        Set<AssetOption> result = Sets.newConcurrentHashSet();
        if (hostOrClusterIds != null && !hostOrClusterIds.isEmpty()) {
            info("Host/Cluster ids selected by user: %s", hostOrClusterIds);
            List<String> parsedHostClusterIds = TextUtils.parseCSV(hostOrClusterIds);
            for (String id : parsedHostClusterIds) {
                result.addAll(getVirtualArray(context, uri(id)));
            }
        }
        return Lists.newArrayList(result);
    }

    @Asset("virtualArrayForMultipleComputeResource")
    @AssetDependencies({ "cluster" })
    public List<AssetOption> getVirtualArrayForMultipleCluster(AssetOptionsContext context, String clusterIds) {
        return getVirtualArrayforMultipleHost(context, clusterIds);
    }

    @Asset("objectVirtualArray")
    public List<AssetOption> getObjectVirtualArrays(AssetOptionsContext context) {
        ViPRCoreClient client = api(context);
        // Get the set of virtual arrays that are associated with object vpools
        Set<URI> varrayIds = new HashSet<>();
        for (ObjectVirtualPoolRestRep vpool : client.objectVpools().getByTenant(context.getTenant())) {
            varrayIds.addAll(ResourceUtils.refIds(vpool.getVirtualArrays()));
        }
        filterByContextTenant(varrayIds, client.varrays().getByTenant(context.getTenant()));
        return createBaseResourceOptions(client.varrays().getByIds(varrayIds));
    }

    /**
     * remove any varrays, which context's tenant doesn't have access to, from inputArrays
     *
     * @param inputArrays
     */
    private void filterByContextTenant(List<VirtualArrayRestRep> inputArrays, List<VirtualArrayRestRep> virtualArraysByTenant) {
        Iterator<VirtualArrayRestRep> iterator = inputArrays.iterator();
        while (iterator.hasNext()) {
            VirtualArrayRestRep rep = iterator.next();
            if (!contains(rep.getId(), virtualArraysByTenant)) {
                iterator.remove();
            }
        }
    }

    private void filterByContextTenant(Set<URI> inputArrays, List<VirtualArrayRestRep> virtualArraysByTenant) {
        Iterator<URI> iterator = inputArrays.iterator();
        while (iterator.hasNext()) {
            URI rep = iterator.next();
            if (!contains(rep, virtualArraysByTenant)) {
                iterator.remove();
            }
        }
    }

    private boolean contains(URI varrayID, List<VirtualArrayRestRep> varrayList) {
        for (VirtualArrayRestRep rep : varrayList) {
            if (rep.getId().toString().equalsIgnoreCase(varrayID.toString())) {
                return true;
            }
        }

        return false;
    }
}
