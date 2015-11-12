/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import static com.emc.vipr.client.core.filters.CompatibilityFilter.INCOMPATIBLE;
import static com.emc.vipr.client.core.filters.RegistrationFilter.REGISTERED;
import static com.emc.vipr.client.core.filters.StorageSystemTypeFilter.BLOCK;
import static com.emc.vipr.client.core.filters.StorageSystemTypeFilter.FILE;
import static com.emc.vipr.client.core.util.UnmanagedHelper.NATIVE_ID;
import static com.emc.vipr.client.core.util.UnmanagedHelper.PROVISIONED_CAPACITY;
import static com.emc.vipr.client.core.util.UnmanagedHelper.getInfoField;
import static com.emc.vipr.client.core.util.UnmanagedHelper.getLabel;
import static com.emc.vipr.client.core.util.UnmanagedHelper.getVpoolsForUnmanaged;
import static com.emc.vipr.client.core.util.UnmanagedHelper.isClone;
import static com.emc.vipr.client.core.util.UnmanagedHelper.isMirror;
import static com.emc.vipr.client.core.util.UnmanagedHelper.isNonRPExported;
import static com.emc.vipr.client.core.util.UnmanagedHelper.isSnapShot;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
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
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.util.IngestionMethodEnum;
import com.emc.sa.util.SizeUtils;
import com.emc.sa.util.StringComparator;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
@AssetNamespace("vipr")
public class VirtualDataCenterProvider extends BaseAssetOptionsProvider {

    final static int VOLUME_PAGE_ALL = -1;
    final static int VOLUME_PAGE_SIZE = 500;

    @Asset("blockStorageSystem")
    public List<AssetOption> getBlockStorageSystem(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).storageSystems().getAll(BLOCK.and(REGISTERED).and(INCOMPATIBLE.not())));
    }

    @Asset("unmanagedBlockStorageSystem")
    public List<AssetOption> getUnmanagedBlockStorageSystem(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).storageSystems().getAll(BLOCK.and(REGISTERED).and(INCOMPATIBLE.not())));
    }

    @Asset("unmanagedBlockProtectionSystem")
    public List<AssetOption> getUnmanagedBlockProtectionSystem(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).protectionSystems().getAll(REGISTERED.and(INCOMPATIBLE.not())));
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

    @Asset("unexportedIngestionMethod")
    @AssetDependencies({ "unmanagedBlockStorageSystem" })
    public List<AssetOption> getUnexportedIngestionMethod(AssetOptionsContext ctx, URI storageSystemId) {
        ViPRCoreClient client = api(ctx);
        StorageSystemRestRep storageSystemRestRep = client.storageSystems().get(storageSystemId);

        List<AssetOption> options = Lists.newArrayList();
        options.add(newAssetOption(IngestionMethodEnum.FULL.toString(), "unmanagedVolume.ingestMethod.full"));
        if (BlockProviderUtils.isVplex(storageSystemRestRep)) {
            options.add(newAssetOption(IngestionMethodEnum.VIRTUAL_VOLUMES_ONLY.toString(),
                    "unmanagedVolume.ingestMethod.virtualVolumesOnly"));
        }
        return options;
    }

    @Asset("exportedIngestionMethod")
    @AssetDependencies({ "unmanagedBlockVirtualPool" })
    public List<AssetOption> getExportedIngestionMethod(AssetOptionsContext ctx, URI virtualPoolId) {
        ViPRCoreClient client = api(ctx);
        BlockVirtualPoolRestRep virtualPoolRestRep = client.blockVpools().get(virtualPoolId);

        List<AssetOption> options = Lists.newArrayList();
        options.add(newAssetOption(IngestionMethodEnum.FULL.toString(), "unmanagedVolume.ingestMethod.full"));
        if (virtualPoolRestRep.getHighAvailability() != null) {
            options.add(newAssetOption(IngestionMethodEnum.VIRTUAL_VOLUMES_ONLY.toString(),
                    "unmanagedVolume.ingestMethod.virtualVolumesOnly"));
        }
        return options;
    }

    @Asset("volumeFilter")
    @AssetDependencies({ "host", "unmanagedBlockVirtualPool" })
    public List<AssetOption> getUnmanagedVolumeFilter(AssetOptionsContext ctx, URI host, URI vpool) {
        List<String> volumeNames = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumesByHost(ctx, host)) {
            if (matchesVpool(volume, vpool)) {
                volumeNames.add(getLabel(volume));
            }
        }
        Collections.sort(volumeNames, new StringComparator(false));
        return getVolumeFilterOptions(volumeNames);
    }

    @Asset("unmanagedVolume")
    @AssetDependencies({ "host", "unmanagedBlockVirtualPool", "volumeFilter" })
    public List<AssetOption> getUnmanagedVolume(AssetOptionsContext ctx, URI host, URI vpool, int volumePage) {

        List<AssetOption> options = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumesByHost(ctx, host)) {
            if (matchesVpool(volume, vpool)) {
                options.add(toAssetOption(volume));
            }
        }

        AssetOptionsUtils.sortOptionsByLabel(options);
        return getVolumeSublist(volumePage, options);
    }

    public static List<AssetOption> getVolumeFilterOptions(List<String> volumeNames) {
        List<AssetOption> options = Lists.newArrayList();
        int volumeNamesSize = volumeNames.size();
        int currentPage = 0;

        options.add(new AssetOption(new Integer(VOLUME_PAGE_ALL).toString(), "All Volumes"));
        for (int i = 0; i < volumeNames.size(); i += VOLUME_PAGE_SIZE) {
            if (i + VOLUME_PAGE_SIZE > (volumeNamesSize - 1)) {
                options.add(new AssetOption(new Integer(currentPage).toString(), volumeNames.get(i) + " - "
                        + volumeNames.get(volumeNamesSize - 1)));
            } else {
                options.add(new AssetOption(new Integer(currentPage).toString(), volumeNames.get(i) + " - "
                        + volumeNames.get(i + VOLUME_PAGE_SIZE)));
            }
            currentPage++;
        }
        return options;
    }

    public static List<AssetOption> getVolumeSublist(int volumePage, List<AssetOption> options) {
        if (volumePage != VOLUME_PAGE_ALL) {
            int optionsSize = options.size();
            options = options.subList(VOLUME_PAGE_SIZE * volumePage, (VOLUME_PAGE_SIZE * (volumePage + 1)) >= optionsSize ? optionsSize
                    : (VOLUME_PAGE_SIZE * (volumePage + 1)));
        }
        return options;
    }

    @Asset("volumeFilter")
    @AssetDependencies({ "unmanagedBlockStorageSystem", "blockVirtualPool" })
    public List<AssetOption> getVolumeFilter(AssetOptionsContext ctx, URI storageSystemId, URI vpool) {
        List<String> volumeNames = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumes(ctx, storageSystemId, vpool)) {
            if (!isNonRPExported(volume.getVolumeCharacteristics())) {
                volumeNames.add(getLabel(volume));
            }
        }
        Collections.sort(volumeNames, new StringComparator(false));
        return getVolumeFilterOptions(volumeNames);
    }

    @Asset("unmanagedVolumeByStorageSystemVirtualPool")
    @AssetDependencies({ "unmanagedBlockStorageSystem", "blockVirtualPool" })
    public List<AssetOption> getUnmanagedVolumeByStorageSystemVirtualPool(AssetOptionsContext ctx, URI storageSystemId, URI vpool) {
        List<AssetOption> options = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumes(ctx, storageSystemId, vpool)) {
            if (!isNonRPExported(volume.getVolumeCharacteristics())) {
                options.add(toAssetOption(volume));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedVolumeByStorageSystemVirtualPool")
    @AssetDependencies({ "unmanagedBlockStorageSystem", "blockVirtualPool", "volumeFilter" })
    public List<AssetOption> getUnmanagedVolumeByStorageSystemVirtualPool(AssetOptionsContext ctx, URI storageSystemId, URI vpool,
            int volumePage) {
        List<AssetOption> options = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumes(ctx, storageSystemId, vpool)) {
            if (!isNonRPExported(volume.getVolumeCharacteristics())) {
                options.add(toAssetOption(volume));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return getVolumeSublist(volumePage, options);
    }

    @Asset("unmanagedVolumeByStorageSystem")
    @AssetDependencies({ "unmanagedBlockStorageSystem", "unmanagedBlockVirtualPool" })
    public List<AssetOption> getUnmanagedVolumeByStorageSystem(AssetOptionsContext ctx, URI storageSystemId, URI vpool) {

        List<AssetOption> options = Lists.newArrayList();
        for (UnManagedVolumeRestRep volume : listUnmanagedVolumes(ctx, storageSystemId)) {
            if (matchesVpool(volume, vpool) && !isNonRPExported(volume.getVolumeCharacteristics())) {
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

    private List<UnManagedVolumeRestRep> listUnmanagedVolumes(AssetOptionsContext ctx, URI storageSystem, URI virtualPool) {
        return api(ctx).unmanagedVolumes().getByStorageSystemVirtualPool(storageSystem, virtualPool);
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
        Set<URI> vpools = getVpoolsForUnmanaged(volume.getVolumeCharacteristics(), volume.getSupportedVPoolUris());
        return vpools.contains(vpool);
    }

    protected static Map<URI, Integer> getBlockVirtualPools(List<UnManagedVolumeRestRep> volumes) {
        Map<URI, Integer> map = Maps.newLinkedHashMap();
        for (UnManagedVolumeRestRep volume : volumes) {
            Set<URI> vpools = getVpoolsForUnmanaged(volume.getVolumeCharacteristics(), volume.getSupportedVPoolUris());
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
            Set<URI> vpools = getVpoolsForUnmanaged(fileSystem.getFileSystemCharacteristics(), fileSystem.getSupportedVPoolUris());
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
