/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import static com.emc.vipr.client.core.filters.CompatibilityFilter.INCOMPATIBLE;
import static com.emc.vipr.client.core.filters.RegistrationFilter.REGISTERED;
import static com.emc.vipr.client.core.filters.StorageSystemTypeFilter.BLOCK;
import static com.emc.vipr.client.core.filters.StorageSystemTypeFilter.FILE;
import static com.emc.vipr.client.core.util.UnmanagedHelper.*;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.util.SizeUtils;

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
@AssetNamespace("vipr")
public class VirtualDataCenterProvider extends BaseAssetOptionsProvider {

    @Asset("blockStorageSystem")
    public List<AssetOption> getBlockStorageSystem(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).storageSystems().getAll(BLOCK.and(REGISTERED).and(INCOMPATIBLE.not())));
    }

    @Asset("unmanagedBlockStorageSystem")
    public List<AssetOption> getUnmanagedBlockStorageSystem(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).storageSystems().getAll(BLOCK.and(REGISTERED).and(INCOMPATIBLE.not())));
    }

    @Asset("fileStorageSystem")
    public List<AssetOption> getFileStorageSystem(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).storageSystems().getAll(FILE.and(REGISTERED).and(INCOMPATIBLE.not())));
    }

    // For locked fields
    @Asset("unmanagedBlockVirtualPool")
    public List<AssetOption> getUnmanagedVolumeVirtualPools(AssetOptionsContext ctx) {
        Collection<BlockVirtualPoolRestRep> virtualPools = api(ctx).blockVpools().getAll();
        return createBaseResourceOptions(virtualPools);
    }

    @Asset("unmanagedBlockVirtualPool")
    @AssetDependencies({ "unmanagedBlockStorageSystem" })
    public List<AssetOption> getUnmanagedVolumeVirtualPools(AssetOptionsContext ctx, URI storageSystem) {
        Map<URI, Integer> vpools = getBlockVirtualPools(listUnmanagedVolumes(ctx, storageSystem));
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = BlockProvider.getBlockVirtualPools(api(ctx), vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            BlockVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (vpool != null) {
                options.add(newAssetOption(vpool.getId().toString(), "block.virtualPool.unmanaged", vpool.getName(), entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedBlockVirtualPool")
    @AssetDependencies({ "unmanagedBlockStorageSystem", "virtualArray" })
    public List<AssetOption> getUnmanagedVolumeVirtualPools(AssetOptionsContext ctx, URI storageSystem, URI virtualArray) {
        Map<URI, Integer> vpools = getBlockVirtualPools(listUnmanagedVolumes(ctx, storageSystem));
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = BlockProvider.getBlockVirtualPools(api(ctx), vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            BlockVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (isVirtualPoolInVirtualArray(vpool, virtualArray)) {
                options.add(newAssetOption(vpool.getId().toString(), "block.virtualPool.unmanaged", vpool.getName(), entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedBlockVirtualPool")
    @AssetDependencies({ "host", "virtualArray" })
    public List<AssetOption> getUnmanagedVolumeVirtualPoolsForHost(AssetOptionsContext ctx, URI host, URI virtualArray) {
        Map<URI, Integer> vpools = getBlockVirtualPools(listUnmanagedVolumesByHost(ctx, host));
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = BlockProvider.getBlockVirtualPools(api(ctx), vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            BlockVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (isVirtualPoolInVirtualArray(vpool, virtualArray)) {
                options.add(newAssetOption(vpool.getId().toString(), "block.virtualPool.unmanaged", vpool.getName(), entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedVolume")
    @AssetDependencies({ "host", "unmanagedBlockVirtualPool" })
    public List<AssetOption> getUnmanagedVolume(AssetOptionsContext ctx, URI host, URI vpool) {

        List<AssetOption> options = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumesByHost(ctx, host)) {
            if (matchesVpool(volume, vpool)) {
                options.add(toAssetOption(volume));
            }
        }

        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedVolumeByStorageSystem")
    @AssetDependencies({ "unmanagedBlockStorageSystem", "unmanagedBlockVirtualPool" })
    public List<AssetOption> getUnmanagedVolumeByStorageSystem(AssetOptionsContext ctx, URI storageSystemId, URI vpool) {

        List<AssetOption> options = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumes(ctx, storageSystemId)) {
            if (matchesVpool(volume, vpool) && !isVolumeExported(volume.getVolumeCharacteristics())) {
                options.add(toAssetOption(volume));
            }
        }

        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected AssetOption toAssetOption(UnManagedVolumeRestRep volume) {
        String nativeId = getInfoField(volume, NATIVE_ID);
        String label = getLabel(volume);
        long provisionedSize = getInfoField(volume, PROVISIONED_CAPACITY, 0L);

        String resource = "block.unmanaged.volume";
        if (isSnapShot(volume.getVolumeCharacteristics())) {
            resource = "block.unmanaged.volume.withSnapshot";
        } else if (isClone(volume.getVolumeCharacteristics())) {
            resource = "block.unmanaged.volume.withClone";
        } else if (isMirror(volume.getVolumeCharacteristics())) {
            resource = "block.unmanaged.volume.withMirror";
        }

        return newAssetOption(volume.getId(), resource, label, nativeId, SizeUtils.humanReadableByteCount(provisionedSize));
    }

    protected boolean isVirtualPoolInVirtualArray(VirtualPoolCommonRestRep vpool, URI virtualArrayId) {
        if (vpool != null && virtualArrayId != null) {
            List<URI> ids = ResourceUtils.refIds(vpool.getVirtualArrays());
            return ids.contains(virtualArrayId);
        }
        return false;
    }

    // For locked fields
    @Asset("unmanagedFileVirtualPool")
    public List<AssetOption> getUnmanagedFileVirtualPools(AssetOptionsContext ctx) {
        Collection<FileVirtualPoolRestRep> virtualPools = api(ctx).fileVpools().getAll();
        return createBaseResourceOptions(virtualPools);
    }

    @Asset("unmanagedFileVirtualPool")
    @AssetDependencies({ "fileStorageSystem" })
    public List<AssetOption> getUnmanagedFileVirtualPools(AssetOptionsContext ctx, URI storageSystem) {
        Map<URI, Integer> vpools = getFileVirtualPools(listUnmanagedFilesystems(ctx, storageSystem));
        Map<URI, FileVirtualPoolRestRep> virtualPoolMap = FileProvider.getFileVirtualPools(api(ctx), vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            FileVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (vpool != null) {
                options.add(newAssetOption(vpool.getId().toString(), "file.virtualPool.unmanaged", vpool.getName(), entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedFileVirtualPool")
    @AssetDependencies({ "fileStorageSystem", "virtualArray" })
    public List<AssetOption> getUnmanagedFileSystemVirtualPools(AssetOptionsContext ctx, URI storageSystem, URI virtualArray) {
        Map<URI, Integer> vpools = getFileVirtualPools(listUnmanagedFilesystems(ctx, storageSystem));
        Map<URI, FileVirtualPoolRestRep> virtualPoolMap = FileProvider.getFileVirtualPools(api(ctx), vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            FileVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (isVirtualPoolInVirtualArray(vpool, virtualArray)) {
                options.add(newAssetOption(vpool.getId().toString(), "file.virtualPool.unmanaged", vpool.getName(), entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    private List<UnManagedVolumeRestRep> listUnmanagedVolumes(AssetOptionsContext ctx, URI storageSystem) {
        return api(ctx).unmanagedVolumes().getByStorageSystem(storageSystem);
    }

    private List<UnManagedVolumeRestRep> listUnmanagedVolumesByHost(AssetOptionsContext ctx, URI host) {
        if (BlockStorageUtils.isHost(host)) {
            return api(ctx).unmanagedVolumes().getByHost(host);
        }
        // We have a cluster
        else {
            return api(ctx).unmanagedVolumes().getByCluster(host);
        }
    }

    private List<UnManagedFileSystemRestRep> listUnmanagedFilesystems(AssetOptionsContext ctx, URI storageSystem) {
        return api(ctx).unmanagedFileSystems().getByStorageSystem(storageSystem);
    }

    protected static boolean matchesVpool(UnManagedVolumeRestRep volume, URI vpool) {
        Set<URI> vpools = getVpoolsForUnmanaged(volume.getVolumeInformation(), volume.getVolumeCharacteristics());
        return vpools.contains(vpool);
    }

    protected static Map<URI, Integer> getBlockVirtualPools(List<UnManagedVolumeRestRep> volumes) {
        Map<URI, Integer> map = Maps.newLinkedHashMap();
        for (UnManagedVolumeRestRep volume : volumes) {
            Set<URI> vpools = getVpoolsForUnmanaged(volume.getVolumeInformation(), volume.getVolumeCharacteristics());
            for (URI vpool : vpools) {
                if (map.containsKey(vpool)) {
                    map.put(vpool, map.get(vpool).intValue() + 1);
                }
                else {
                    map.put(vpool, 1);
                }
            }
        }
        return map;
    }

    protected static Map<URI, Integer> getFileVirtualPools(List<UnManagedFileSystemRestRep> fileSystems) {
        Map<URI, Integer> map = Maps.newLinkedHashMap();
        for (UnManagedFileSystemRestRep fileSystem : fileSystems) {
            Set<URI> vpools = getVpoolsForUnmanaged(fileSystem.getFileSystemInformation(), fileSystem.getFileSystemCharacteristics());
            for (URI vpool : vpools) {
                if (map.containsKey(vpool)) {
                    map.put(vpool, map.get(vpool).intValue() + 1);
                }
                else {
                    map.put(vpool, 1);
                }
            }
        }
        return map;
    }
}
