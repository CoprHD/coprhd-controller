/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import static com.emc.vipr.client.core.filters.CompatibilityFilter.INCOMPATIBLE;
import static com.emc.vipr.client.core.filters.RegistrationFilter.REGISTERED;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject.ExportType;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeList;
import com.emc.storageos.model.storagesystem.type.StorageSystemTypeRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.StorageSystemTypeFilter;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.core.util.UnmanagedHelper;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
@AssetNamespace("vipr")
public class VirtualDataCenterProvider extends BaseAssetOptionsProvider {

    final static int VOLUME_PAGE_ALL = -1;
    final static int VOLUME_PAGE_SIZE = 500;

    private static StorageSystemTypeFilter BLOCK;
    private static StorageSystemTypeFilter FILE;

    private static final String TRUE_STR = "true";
    private static final String FALSE_STR = "false";

    @Autowired
    private CustomConfigHandler customConfigHandler;

    public CustomConfigHandler getCustomConfigHandler() {
        return customConfigHandler;
    }

    @Asset("blockStorageSystem")
    public List<AssetOption> getBlockStorageSystem(AssetOptionsContext ctx) {
        BLOCK = new StorageSystemTypeFilter(getStorageSystemType(ctx, "block"));
        return createBaseResourceOptions(
                api(ctx).storageSystems().getAll(BLOCK.and(REGISTERED).and(INCOMPATIBLE.not())));
    }

    @Asset("unmanagedBlockStorageSystem")
    public List<AssetOption> getUnmanagedBlockStorageSystem(AssetOptionsContext ctx) {
        BLOCK = new StorageSystemTypeFilter(getStorageSystemType(ctx, "block"));
        BLOCK.addType("driversystem"); // to make driversystem systems visible for selection if present in db
        return createBaseResourceOptions(
                api(ctx).storageSystems().getAll(BLOCK.and(REGISTERED).and(INCOMPATIBLE.not())));
    }

    @Asset("unmanagedBlockProtectionSystem")
    public List<AssetOption> getUnmanagedBlockProtectionSystem(AssetOptionsContext ctx) {
        return createBaseResourceOptions(api(ctx).protectionSystems().getAll(REGISTERED.and(INCOMPATIBLE.not())));
    }

    @Asset("fileStorageSystem")
    public List<AssetOption> getFileStorageSystem(AssetOptionsContext ctx) {
        FILE = new StorageSystemTypeFilter(getStorageSystemType(ctx, "file"));
        return createBaseResourceOptions(
                api(ctx).storageSystems().getAll(FILE.and(REGISTERED).and(INCOMPATIBLE.not())));
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
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = BlockProvider.getBlockVirtualPools(api(ctx),
                vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            BlockVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (vpool != null) {
                options.add(newAssetOption(vpool.getId().toString(), "block.virtualPool.unmanaged", vpool.getName(),
                        entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedBlockVirtualPool")
    @AssetDependencies({ "unmanagedBlockStorageSystem", "virtualArray" })
    public List<AssetOption> getUnmanagedVolumeVirtualPools(AssetOptionsContext ctx, URI storageSystem,
            URI virtualArray) {
        Map<URI, Integer> vpools = getBlockVirtualPools(listUnmanagedVolumes(ctx, storageSystem));
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = BlockProvider.getBlockVirtualPools(api(ctx),
                vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            BlockVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (isVirtualPoolInVirtualArray(vpool, virtualArray)) {
                options.add(newAssetOption(vpool.getId().toString(), "block.virtualPool.unmanaged", vpool.getName(),
                        entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedBlockVirtualPool")
    @AssetDependencies({ "host", "virtualArray" })
    public List<AssetOption> getUnmanagedVolumeVirtualPoolsForHost(AssetOptionsContext ctx, URI host,
            URI virtualArray) {
        Map<URI, Integer> vpools = getBlockVirtualPools(listUnmanagedVolumesByHost(ctx, host));
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = BlockProvider.getBlockVirtualPools(api(ctx),
                vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            BlockVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (isVirtualPoolInVirtualArray(vpool, virtualArray)) {
                options.add(newAssetOption(vpool.getId().toString(), "block.virtualPool.unmanaged", vpool.getName(),
                        entry.getValue()));
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
                options.add(new AssetOption(new Integer(currentPage).toString(),
                        volumeNames.get(i) + " - " + volumeNames.get(volumeNamesSize - 1)));
            } else {
                options.add(new AssetOption(new Integer(currentPage).toString(),
                        volumeNames.get(i) + " - " + volumeNames.get(i + VOLUME_PAGE_SIZE)));
            }
            currentPage++;
        }
        return options;
    }

    public static List<AssetOption> getVolumeSublist(int volumePage, List<AssetOption> options) {
        if (volumePage != VOLUME_PAGE_ALL) {
            int optionsSize = options.size();
            options = options.subList(VOLUME_PAGE_SIZE * volumePage,
                    (VOLUME_PAGE_SIZE * (volumePage + 1)) >= optionsSize ? optionsSize
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
    public List<AssetOption> getUnmanagedVolumeByStorageSystemVirtualPool(AssetOptionsContext ctx, URI storageSystemId,
            URI vpool) {
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
    public List<AssetOption> getUnmanagedVolumeByStorageSystemVirtualPool(AssetOptionsContext ctx, URI storageSystemId,
            URI vpool, int volumePage) {
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
    public List<AssetOption> getUnmanagedVolumeByStorageSystem(AssetOptionsContext ctx, URI storageSystemId,
            URI vpool) {

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

        return newAssetOption(volume.getId(), resource, label, nativeId,
                SizeUtils.humanReadableByteCount(provisionedSize));
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
                options.add(newAssetOption(vpool.getId().toString(), "file.virtualPool.unmanaged", vpool.getName(),
                        entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedFileVirtualPool")
    @AssetDependencies({ "fileStorageSystem", "virtualArray" })
    public List<AssetOption> getUnmanagedFileSystemVirtualPools(AssetOptionsContext ctx, URI storageSystem,
            URI virtualArray) {
        Map<URI, Integer> vpools = getFileVirtualPools(listUnmanagedFilesystems(ctx, storageSystem));
        Map<URI, FileVirtualPoolRestRep> virtualPoolMap = FileProvider.getFileVirtualPools(api(ctx), vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            FileVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (isVirtualPoolInVirtualArray(vpool, virtualArray)) {
                options.add(newAssetOption(vpool.getId().toString(), "file.virtualPool.unmanaged", vpool.getName(),
                        entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("unmanagedFileVirtualPool")
    @AssetDependencies({ "fileStorageSystem", "virtualArray", "fileIngestExportType" })
    public List<AssetOption> getUnmanagedFileSystemVirtualPools(AssetOptionsContext ctx, URI storageSystem,
            URI virtualArray, String ingestExportType) {
        Map<URI, Integer> vpools = getFileVirtualPools(listUnmanagedFilesystems(ctx, storageSystem), ingestExportType);
        Map<URI, FileVirtualPoolRestRep> virtualPoolMap = FileProvider.getFileVirtualPools(api(ctx), vpools.keySet());

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<URI, Integer> entry : vpools.entrySet()) {
            FileVirtualPoolRestRep vpool = virtualPoolMap.get(entry.getKey());
            if (isVirtualPoolInVirtualArray(vpool, virtualArray)) {
                options.add(newAssetOption(vpool.getId().toString(), "file.virtualPool.unmanaged", vpool.getName(),
                        entry.getValue()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    /**
     * Action listener on Ingest Unmanaged File Systems for Project dropdown that refreshes the list of unmanaged filesystems
     * 
     * @param ctx
     * @param fileStorageSystem
     * @param virtualArray
     * @param fileIngestExportType
     * @param unmanagedFileVirtualPool
     * @param projectUri
     * @return
     */
    @Asset("unmanagedFileSystemsByStorageSystemVirtualPool")
    @AssetDependencies({ "fileStorageSystem", "virtualArray", "fileIngestExportType", "unmanagedFileVirtualPool", "project" })
    public List<AssetOption> getUnmanagedFileSystemByStorageSystemVirtualPool(AssetOptionsContext ctx, URI fileStorageSystem,
            URI virtualArray, String fileIngestExportType, URI unmanagedFileVirtualPool, URI projectUri) {

        boolean shareVNASWithMultipleProjects = Boolean.valueOf(customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.SHARE_VNAS_WITH_MULTIPLE_PROJECTS, "global", null));

        List<AssetOption> options = Lists.newArrayList();
        FileVirtualPoolRestRep vpool = getFileVirtualPool(ctx, unmanagedFileVirtualPool);

        /*
         * Set<String> projectVnas = null;
         * if (!shareVNASWithMultipleProjects) {
         * ProjectRestRep project = getProject(ctx, projectUri);
         * if (project != null) {
         * projectVnas = project.getAssignedVNasServers();
         * }
         * }
         */

        if (vpool != null && isVirtualPoolInVirtualArray(vpool, virtualArray)) {
            for (UnManagedFileSystemRestRep umfs : listUnmanagedFilesystems(ctx, fileStorageSystem, vpool.getId(), fileIngestExportType)) {

                if (shareVNASWithMultipleProjects || checkProjectVnas(projectUri, ctx, umfs)) {
                    options.add(toAssetOption(umfs));
                }
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return getVolumeSublist(VOLUME_PAGE_ALL, options);
    }

    protected AssetOption toAssetOption(UnManagedFileSystemRestRep umfs) {

        String path = getInfoField(umfs, SupportedFileSystemInformation.PATH.toString());
        String capacity = getInfoField(umfs, SupportedFileSystemInformation.PROVISIONED_CAPACITY.toString());
        String deviceLabel = getInfoField(umfs, SupportedFileSystemInformation.DEVICE_LABEL.toString());

        Long provisionedSize = 0L;
        if (capacity != null && !capacity.isEmpty()) {
            provisionedSize = Long.valueOf(capacity);
        }

        String resource = "file.unmanaged.filesystem";
        if (UnmanagedHelper.isReplicationSource(umfs.getFileSystemCharacteristics())) {
            resource = "file.unmanaged.filesystemSource";
        } else if (UnmanagedHelper.isReplicationTarget(umfs.getFileSystemCharacteristics())) {
            resource = "file.unmanaged.filesystemTarget";
        }
        return newAssetOption(umfs.getId(), resource, deviceLabel, path,
                SizeUtils.humanReadableByteCount(provisionedSize));
    }

    // Get virtual pool details!!
    private FileVirtualPoolRestRep getFileVirtualPool(AssetOptionsContext ctx, URI id) {
        return api(ctx).fileVpools().get(id);
    }

    private ProjectRestRep getProject(AssetOptionsContext ctx, URI id) {
        return api(ctx).projects().get(id);
    }

    private VirtualNASRestRep getVnas(AssetOptionsContext ctx, URI id) {
        return api(ctx).virtualNasServers().get(id);
    }

    private List<UnManagedVolumeRestRep> listUnmanagedVolumes(AssetOptionsContext ctx, URI storageSystem) {
        return api(ctx).unmanagedVolumes().getByStorageSystem(storageSystem);
    }

    private List<UnManagedVolumeRestRep> listUnmanagedVolumes(AssetOptionsContext ctx, URI storageSystem,
            URI virtualPool) {
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

    private List<UnManagedFileSystemRestRep> listUnmanagedFilesystems(AssetOptionsContext ctx, URI storageSystem, URI virtualPool,
            String expType) {
        boolean exported = StringUtils.equalsIgnoreCase(expType, FileProvider.EXPORTED_TYPE);
        return api(ctx).unmanagedFileSystems().getByStorageSystemVirtualPool(storageSystem, virtualPool, exported, null);
    }

    private List<String> getStorageSystemType(AssetOptionsContext ctx, String storagetype) {
        List<String> storagesystemtypes = new ArrayList<String>();
        StorageSystemTypeList storagetypelist = api(ctx).storageSystemType().listStorageSystemTypes("all");
        for (StorageSystemTypeRestRep storagetypeRest : storagetypelist.getStorageSystemTypes()) {
            if (storagetypeRest.getMetaType().equals(storagetype)
                    || storagetypeRest.getMetaType().contains(storagetype)) {
                storagesystemtypes.add(storagetypeRest.getStorageTypeName());
            }
        }
        return storagesystemtypes;
    }

    public static boolean matchesVpool(UnManagedVolumeRestRep volume, URI vpool) {
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
                } else {
                    map.put(vpool, 1);
                }
            }
        }
        return map;
    }

    protected static Map<URI, Integer> getFileVirtualPools(List<UnManagedFileSystemRestRep> fileSystems) {
        Map<URI, Integer> map = Maps.newLinkedHashMap();
        for (UnManagedFileSystemRestRep fileSystem : fileSystems) {
            Set<URI> vpools = getVpoolsForUnmanaged(fileSystem.getFileSystemCharacteristics(),
                    fileSystem.getSupportedVPoolUris());
            for (URI vpool : vpools) {
                if (map.containsKey(vpool)) {
                    map.put(vpool, map.get(vpool).intValue() + 1);
                } else {
                    map.put(vpool, 1);
                }
            }
        }
        return map;
    }

    protected static Map<URI, Integer> getFileVirtualPools(List<UnManagedFileSystemRestRep> fileSystems, String exportType) {
        Map<URI, Integer> map = Maps.newLinkedHashMap();
        String isExportedSelected = FALSE_STR;
        if (exportType != null) {
            isExportedSelected = exportType.equalsIgnoreCase(ExportType.EXPORTED.name()) ? TRUE_STR
                    : FALSE_STR;
        }
        for (UnManagedFileSystemRestRep fileSystem : fileSystems) {
            Set<URI> vpools = getVpoolsForUnmanaged(fileSystem.getFileSystemCharacteristics(),
                    fileSystem.getSupportedVPoolUris(), isExportedSelected);
            for (URI vpool : vpools) {
                if (map.containsKey(vpool)) {
                    map.put(vpool, map.get(vpool).intValue() + 1);
                } else {
                    map.put(vpool, 1);
                }
            }
        }
        return map;
    }

    private boolean checkProjectVnas(URI projectUri, AssetOptionsContext ctx, UnManagedFileSystemRestRep umfs) {

        String umfsNas = UnmanagedHelper.getInfoField(umfs, "NAS");
        // If nas of umfs is virtual then compare the vnas else add the umfs
        if (umfsNas != null && umfsNas.contains("VirtualNAS")) {

            // Get vnas object and its associated projects
            VirtualNASRestRep vnasRestResp = getVnas(ctx, URIUtil.uri(umfsNas));
            if (vnasRestResp != null) {
                Set<String> vnasProj = vnasRestResp.getAssociatedProjects();
                // If vnas doesnt have projects then allow ingestion
                if (vnasProj != null && !vnasProj.isEmpty() && projectUri != null) {
                    if (vnasProj.contains(projectUri.toString())) {
                        return true;
                    } else {
                        // Umfs -> vnas -> associated projects doesnt match with current project - skip ingestion
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
