/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import static com.emc.sa.asset.providers.BlockProviderUtils.isLocalMirrorSupported;
import static com.emc.sa.asset.providers.BlockProviderUtils.isLocalSnapshotSupported;
import static com.emc.sa.asset.providers.BlockProviderUtils.isRPSourceVolume;
import static com.emc.sa.asset.providers.BlockProviderUtils.isRPTargetVolume;
import static com.emc.sa.asset.providers.BlockProviderUtils.isRemoteSnapshotSupported;
import static com.emc.sa.asset.providers.BlockProviderUtils.isSnapshotRPBookmark;
import static com.emc.sa.asset.providers.BlockProviderUtils.isSnapshotSessionSupportedForCG;
import static com.emc.sa.asset.providers.BlockProviderUtils.isSnapshotSessionSupportedForVolume;
import static com.emc.sa.asset.providers.BlockProviderUtils.isVpoolProtectedByVarray;
import static com.emc.vipr.client.core.util.ResourceUtils.name;
import static com.emc.vipr.client.core.util.ResourceUtils.stringId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.util.CatalogSerializationUtils;
import com.emc.sa.util.ResourceType;
import com.emc.sa.util.StringComparator;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.SnapshotList;
import com.emc.storageos.model.StringHashMapEntry;
import com.emc.storageos.model.VirtualArrayRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupCopySetParam;
import com.emc.storageos.model.application.VolumeGroupList;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionList;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep.MirrorRestRep;
import com.emc.storageos.model.block.VolumeRestRep.ProtectionRestRep;
import com.emc.storageos.model.block.VolumeRestRep.SRDFRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewParam;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.block.export.InitiatorPortMapRestRep;
import com.emc.storageos.model.customconfig.SimpleValueRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.portgroup.StoragePortGroupChangeRep;
import com.emc.storageos.model.portgroup.StoragePortGroupRestRep;
import com.emc.storageos.model.portgroup.StoragePortGroupRestRepList;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.protection.ProtectionSetRestRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.model.vpool.VirtualPoolChangeRep;
import com.emc.storageos.model.vpool.VirtualPoolCommonRestRep;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.BlockVolumeBootVolumeFilter;
import com.emc.vipr.client.core.filters.BlockVolumeConsistencyGroupFilter;
import com.emc.vipr.client.core.filters.ConsistencyGroupFilter;
import com.emc.vipr.client.core.filters.DefaultResourceFilter;
import com.emc.vipr.client.core.filters.ExportHostOrClusterFilter;
import com.emc.vipr.client.core.filters.ExportVirtualArrayFilter;
import com.emc.vipr.client.core.filters.FilterChain;
import com.emc.vipr.client.core.filters.RecoverPointPersonalityFilter;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.filters.SRDFSourceFilter;
import com.emc.vipr.client.core.filters.SRDFTargetFilter;
import com.emc.vipr.client.core.filters.SourceTargetVolumesFilter;
import com.emc.vipr.client.core.filters.VplexVolumeFilter;
import com.emc.vipr.client.core.filters.VplexVolumeVirtualPoolFilter;
import com.emc.vipr.client.core.impl.SearchConstants;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Component
@AssetNamespace("vipr")
public class BlockProvider extends BaseAssetOptionsProvider {

    private static final Logger log = LoggerFactory.getLogger(BlockProvider.class);

    public static final String EXCLUSIVE_STORAGE = "exclusive";
    public static final String SHARED_STORAGE = "shared";
    public static final String RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_VALUE = "rp";
    public static final String LOCAL_ARRAY_SNAPSHOT_TYPE_VALUE = "local";
    public static final String SNAPSHOT_SESSION_TYPE_VALUE = "session";
    public static final String SNAPSHOT_TARGET_TYPE_VALUE = "target";
    public static final String CG_SNAPSHOT_TYPE_VALUE = "cg";
    public static final String CG_SNAPSHOT_SESSION_TYPE_VALUE = "cgsession";

    public static final String VOLUME_OPTION_KEY = "volume";
    public static final String CONSISTENCY_GROUP_OPTION_KEY = "consistencygroup";

    public static final String LINKED_SNAPSHOT_COPYMODE_VALUE = "copy";
    public static final String LINKED_SNAPSHOT_NOCOPYMODE_VALUE = "nocopy";

    public static final String YES_VALUE = "yes";
    public static final String NO_VALUE = "no";

    public static final String MIGRATE_ONLY_OPTION_KEY = "migrate_only";
    public static final String INGEST_AND_MIGRATE_OPTION_KEY = "ingest_and_migrate";

    private static final AssetOption MIGRATE_ONLY_OPTION = newAssetOption(MIGRATE_ONLY_OPTION_KEY, "Migrate Only");
    private static final AssetOption INGEST_AND_MIGRATE_OPTION = newAssetOption(INGEST_AND_MIGRATE_OPTION_KEY,
            "Ingest and Migrate");

    private static final AssetOption VOLUME_OPTION = newAssetOption(VOLUME_OPTION_KEY, "block.storage.type.volume");
    private static final AssetOption CONSISTENCY_GROUP_OPTION = newAssetOption(CONSISTENCY_GROUP_OPTION_KEY,
            "block.storage.type.consistencygroup");

    // Failover option keys
    public static final String LATEST_IMAGE_OPTION_KEY = "latest";
    public static final String PIT_IMAGE_OPTION_KEY = "pit";

    // SRDF METRO filter
    public static final String ACTIVE = "ACTIVE";

    private static final AssetOption LATEST_IMAGE_OPTION = newAssetOption(LATEST_IMAGE_OPTION_KEY, "failover.image.type.latest");
    private static final AssetOption PIT_IMAGE_OPTION = newAssetOption(PIT_IMAGE_OPTION_KEY, "failover.image.type.pit");

    private static final AssetOption EXCLUSIVE_STORAGE_OPTION = newAssetOption(EXCLUSIVE_STORAGE, "block.storage.type.exclusive");
    private static final AssetOption SHARED_STORAGE_OPTION = newAssetOption(SHARED_STORAGE, "block.storage.type.shared");
    private static final AssetOption RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_OPTION = newAssetOption(RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_VALUE,
            "block.snapshot.type.rp_bookmark");
    private static final AssetOption LOCAL_ARRAY_SNAPSHOT_TYPE_OPTION = newAssetOption(LOCAL_ARRAY_SNAPSHOT_TYPE_VALUE,
            "block.snapshot.type.local");
    private static final AssetOption SNAPSHOT_SESSION_TYPE_OPTION = newAssetOption(SNAPSHOT_SESSION_TYPE_VALUE,
            "block.snapshot.type.session");
    private static final AssetOption SNAPSHOT_TARGET_TYPE_OPTION = newAssetOption(SNAPSHOT_TARGET_TYPE_VALUE,
            "block.snapshot.type.target");

    private static final AssetOption CG_SNAPSHOT_TYPE_OPTION = newAssetOption(CG_SNAPSHOT_TYPE_VALUE,
            "block.snapshot.type.cg");
    private static final AssetOption CG_SNAPSHOT_SESSION_TYPE_OPTION = newAssetOption(CG_SNAPSHOT_SESSION_TYPE_VALUE,
            "block.snapshot.type.cg.session");
    private static final AssetOption LINKED_SNAPSHOT_COPYMODE_OPTION = newAssetOption(LINKED_SNAPSHOT_COPYMODE_VALUE,
            "block.snapshot.linked.copymode");
    private static final AssetOption LINKED_SNAPSHOT_NOCOPYMODE_OPTION = newAssetOption(LINKED_SNAPSHOT_NOCOPYMODE_VALUE,
            "block.snapshot.linked.nocopymode");
    private static final AssetOption DISPLAY_JOURNALS_TRUE_OPTION = newAssetOption(YES_VALUE, "choice.yes");
    private static final AssetOption DISPLAY_JOURNALS_FALSE_OPTION = newAssetOption(NO_VALUE, "choice.no");

    private static List<AssetOption> NTFS_OPTIONS = Lists.newArrayList(newAssetOption("DEFAULT", "Default"),
            newAssetOption("512", "512"),
            newAssetOption("1024", "1k"),
            newAssetOption("2048", "2k"),
            newAssetOption("4096", "4k"),
            newAssetOption("8192", "8k"),
            newAssetOption("16k", "16k"),
            newAssetOption("32k", "32k"),
            newAssetOption("64k", "64k"));

    private static List<AssetOption> FAT32_OPTIONS = Lists.newArrayList(newAssetOption("DEFAULT", "Default"),
            newAssetOption("512", "512"),
            newAssetOption("1024", "1k"),
            newAssetOption("2048", "2k"),
            newAssetOption("4096", "4k"),
            newAssetOption("8192", "8k"));

    private static List<AssetOption> ALL_BLOCKSIZE_OPTIONS = Lists.newArrayList(NTFS_OPTIONS);

    private static List<AssetOption> WINDOWS_FILESYSTEM_TYPES = Lists.newArrayList(
            newAssetOption("ntfs", "ntfs"),
            newAssetOption("fat32", "fat32"));

    private static final String BLOCK_CONSISTENCY_GROUP_TYPE = "BlockConsistencyGroup";

    private static final String VOLUME_TYPE = "Volume";

    private static final String NONE_TYPE = "None";
    private static final String IBMXIV_SYSTEM_TYPE = "ibmxiv";

    private static final int EXPORT_PATH_MIN = 1;
    private static final int EXPORT_PATH_MAX = 32;

    private static final String VMAX_PORT_GROUP_ENABLED = "VMAXUsePortGroupEnabled/value";
    private static final String VMAX = "vmax";

    private static final String EMPTY_STRING = "";

    public static boolean isExclusiveStorage(String storageType) {
        return EXCLUSIVE_STORAGE.equals(storageType);
    }

    public static boolean isSharedStorage(String storageType) {
        return SHARED_STORAGE.equals(storageType);
    }

    public static boolean isVolumeType(String type) {
        return VOLUME_OPTION_KEY.equals(type);
    }

    public static boolean isConsistencyGroupType(String type) {
        return CONSISTENCY_GROUP_OPTION_KEY.equals(type);
    }

    public static boolean checkTypeConsistency(URI volumeId, String volumeOrConsistencyType) {
        if (volumeId == null || volumeOrConsistencyType == null) {
            return false;
        } else if (isVolumeType(volumeOrConsistencyType) && !BlockProviderUtils.isType(volumeId, VOLUME_TYPE)) {
            return false;
        } else if (isConsistencyGroupType(volumeOrConsistencyType) && !BlockProviderUtils.isType(volumeId, BLOCK_CONSISTENCY_GROUP_TYPE)) {
            return false;
        }
        return true;
    }

    static boolean isConsistencyGroupType(URI urlId) {
        return BlockProviderUtils.isType(urlId, BLOCK_CONSISTENCY_GROUP_TYPE);
    }

    protected static boolean isInConsistencyGroup(BlockObjectRestRep volume) {
        return volume.getConsistencyGroup() != null;
    }

    @Asset("blockVolumeOrConsistencyType")
    @AssetDependencies("project")
    public List<AssetOption> getblockVolumeOrConsistencyType(AssetOptionsContext ctx, URI project) {
        return Lists.newArrayList(VOLUME_OPTION, CONSISTENCY_GROUP_OPTION);
    }

    @Asset("imageToAccess")
    @AssetDependencies("protectedBlockVolume")
    public List<AssetOption> getImageToAccess(AssetOptionsContext ctx, URI protectedBlockVolume) {
        List<AssetOption> options = Lists.newArrayList();
        options = Lists.newArrayList(LATEST_IMAGE_OPTION, PIT_IMAGE_OPTION);

        return options;
    }

    @Asset("blockStorageType")
    public List<AssetOption> getStorageTypes(AssetOptionsContext ctx) {
        return Lists.newArrayList(EXCLUSIVE_STORAGE_OPTION, SHARED_STORAGE_OPTION);
    }

    @Asset("sourceBlockVolume")
    @AssetDependencies("project")
    public List<AssetOption> getSourceVolumes(AssetOptionsContext ctx, URI project) {
        debug("getting source block volumes (project=%s)", project);
        return createVolumeOptions(null, listSourceVolumes(api(ctx), project));
    }

    @Asset("sourceBlockVolumeForAddToApplication")
    @AssetDependencies({ "consistencyGroupAll" })
    public List<AssetOption> getApplicationSourceVolumes(AssetOptionsContext ctx, URI cg) {

        final ViPRCoreClient client = api(ctx);
        VolumeGroupList applications = client.application().getApplications();
        List<URI> applicationIds = new ArrayList<URI>();
        for (NamedRelatedResourceRep volumeGroup : applications.getVolumeGroups()) {
            VolumeGroupRestRep vg = client.application().getApplication(volumeGroup.getId());
            if (vg.getRoles().contains("COPY")) {
                applicationIds.add(volumeGroup.getId());
            }
        }

        ResourceFilter<VolumeRestRep> cgFilter = new BlockVolumeConsistencyGroupFilter(cg, false);

        List<ProjectRestRep> projects = api(ctx).projects().getByTenant(ctx.getTenant());
        List<VolumeRestRep> volumes = new ArrayList<VolumeRestRep>();
        for (ProjectRestRep project : projects) {
            volumes.addAll(listSourceVolumes(api(ctx), project.getId(), cgFilter));
        }
        List<VolumeRestRep> volumesNotInApplication = new ArrayList<VolumeRestRep>();
        for (VolumeRestRep volume : volumes) {
            if (volume.getVolumeGroups() != null && !volume.getVolumeGroups().isEmpty()) {
                boolean volumeIsInApplication = false;
                for (RelatedResourceRep rep : volume.getVolumeGroups()) {
                    if (applicationIds.contains(rep.getId())) {
                        volumeIsInApplication = true;
                        break;
                    }
                }
                if (!volumeIsInApplication) {
                    volumesNotInApplication.add(volume);
                }
            } else {
                volumesNotInApplication.add(volume);
            }
        }

        return createVolumeOptions(null, volumesNotInApplication);
    }

    @Asset("sourceBlockVolumeInConsistencyGroup")
    @AssetDependencies({ "project", "consistencyGroup" })
    public List<AssetOption> getSourceVolumesWithoutConsistencyGroup(AssetOptionsContext ctx, URI project, URI consistencyGroup) {
        debug("getting source block volumes in consistency group or no consistency group (project=%s, consistency group=%s)", project,
                consistencyGroup);
        return createVolumeOptions(null,
                listSourceVolumes(api(ctx), project, new BlockVolumeConsistencyGroupFilter(consistencyGroup, true)));
    }

    /**
     * Get source volumes for a specific project. If the deletionType is VIPR_ONLY, create
     * a filter that only retrieves Volumes with Host Exports
     * 
     * @param ctx
     * @param project
     * @param deletionType
     * @return
     */
    @Asset("sourceBlockVolumeWithDeletion")
    @AssetDependencies({ "project", "deletionType" })
    public List<AssetOption> getSourceVolumesWithDeletion(final AssetOptionsContext ctx, URI project, String deletionType) {
        debug("getting source block volumes (project=%s)", project);
        FilterChain<VolumeRestRep> filters = new BlockVolumeMountPointFilter().not();
        filters.and(new BlockVolumeVMFSDatastoreFilter().not());
        List<VolumeRestRep> volumes = listSourceVolumes(api(ctx), project, filters);
        return createVolumeOptions(null, volumes);
    }

    @Asset("unexportedSourceBlockVolumeWithDeletion")
    @AssetDependencies({ "project", "deletionType" })
    public List<AssetOption> getUnexportedSourceVolumesWithDeletion(final AssetOptionsContext ctx, URI project, String deletionType) {
        debug("getting unexported source block volumes (project=%s)", project);
        final ViPRCoreClient client = api(ctx);
        Set<URI> volumeIds = new HashSet<URI>(getExportedVolumeIds(ctx, project));
        UnexportedBlockResourceFilter<VolumeRestRep> unexportedFilter = new UnexportedBlockResourceFilter<VolumeRestRep>(
                volumeIds);

        List<VolumeRestRep> volumes = listSourceVolumes(client, project, unexportedFilter);
        return createVolumeOptions(null, volumes);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "host", "project" })
    public List<AssetOption> getExportVolumePortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        return getExportVolumePortGroups(ctx, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "unassignedBlockVolume", "host", "project" })
    public List<AssetOption> getExportVolumePortGroups(AssetOptionsContext ctx, String selectedVolumes, URI hostOrClusterId,
            URI projectId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        SimpleValueRep value = client.customConfigs().getCustomConfigTypeValue(VMAX_PORT_GROUP_ENABLED, VMAX);
        if (value.getValue().equalsIgnoreCase("true")) {
            List<URI> volumeIds = Lists.newArrayList();
            info("Volumes selected by user: %s", selectedVolumes);
            List<String> parsedVolumeIds = TextUtils.parseCSV(selectedVolumes);
            for (String id : parsedVolumeIds) {
                volumeIds.add(uri(id));
            }

            List<VolumeRestRep> volumes = client.blockVolumes().getByIds(volumeIds);

            Set<URI> virtualArrays = new HashSet<URI>();
            Set<URI> storageSystems = new HashSet<URI>();
            Set<URI> virtualPools = new HashSet<URI>();
            for (VolumeRestRep volume : volumes) {
                virtualArrays.add(volume.getVirtualArray().getId());
                storageSystems.add(volume.getStorageController());
                virtualPools.add(volume.getVirtualPool().getId());
            }

            if (virtualArrays.size() == 1 && storageSystems.size() == 1 && virtualPools.size() == 1) {
                Iterator<URI> it = virtualArrays.iterator();
                URI varrayId = it.next();

                ExportGroupRestRep export = findExportGroup(hostOrClusterId, projectId, varrayId, client);

                Iterator<URI> ssIt = storageSystems.iterator();
                Iterator<URI> vpIt = virtualPools.iterator();
                StoragePortGroupRestRepList portGroups = client.varrays().getStoragePortGroups(varrayId,
                        export != null ? export.getId() : null, ssIt.next(), vpIt.next(), null, true);
                return createPortGroupOptions(portGroups.getStoragePortGroups());
            }
        }
        return options;
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "unmountedBlockResource", "aixHost", "project" })
    public List<AssetOption> getExportVolumeForAixPortGroups(AssetOptionsContext ctx, String selectedVolumes, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumePortGroups(ctx, selectedVolumes, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "aixHost", "project" })
    public List<AssetOption> getExportVolumeForAixPortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        return getExportVolumePortGroups(ctx, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "unmountedBlockResource", "windowsHost", "project" })
    public List<AssetOption> getExportVolumeForWindowsPortGroups(AssetOptionsContext ctx, String selectedVolumes, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumePortGroups(ctx, selectedVolumes, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "windowsHost", "project" })
    public List<AssetOption> getExportVolumeForWindowsPortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        return getExportVolumePortGroups(ctx, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "unmountedBlockResource", "hpuxHost", "project" })
    public List<AssetOption> getExportVolumeForHPPortGroups(AssetOptionsContext ctx, String selectedVolumes, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumePortGroups(ctx, selectedVolumes, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "hpuxHost", "project" })
    public List<AssetOption> getExportVolumeForHPPortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        return getExportVolumePortGroups(ctx, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "unmountedBlockResource", "linuxHost", "project" })
    public List<AssetOption> getExportVolumeForLinuxPortGroups(AssetOptionsContext ctx, String selectedVolumes, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumePortGroups(ctx, selectedVolumes, hostOrClusterId, projectId);
    }

    @Asset("exportVolumePortGroups")
    @AssetDependencies({ "linuxHost", "project" })
    public List<AssetOption> getExportVolumeForLinuxPortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        return getExportVolumePortGroups(ctx, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportDatastorePortGroups")
    @AssetDependencies({ "esxHost", "project" })
    public List<AssetOption> getExportDatastorePortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        return getExportVolumePortGroups(ctx, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportDatastorePortGroups")
    @AssetDependencies({ "unassignedBlockDatastore", "esxHost", "project" })
    public List<AssetOption> getExportDatastorePortGroups(AssetOptionsContext ctx, String selectedDatastore, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumePortGroups(ctx, selectedDatastore, hostOrClusterId, projectId);
    }

    @Asset("exportDatastorePortGroups")
    @AssetDependencies({ "unmountedBlockVolume", "esxHost", "project" })
    public List<AssetOption> getExportMountDatastorePortGroups(AssetOptionsContext ctx, String selectedDatastore, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumePortGroups(ctx, selectedDatastore, hostOrClusterId, projectId);
    }

    @Asset("exportVolumeForHostPortGroups")
    @AssetDependencies({ "virtualArray", "blockVirtualPool", "host", "project" })
    public List<AssetOption> getExportVolumeForHostPortGroups(AssetOptionsContext ctx, URI vArrayId, URI vpoolId, URI hostOrClusterId,
            URI projectId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        SimpleValueRep value = client.customConfigs().getCustomConfigTypeValue(VMAX_PORT_GROUP_ENABLED, VMAX);
        if (value.getValue().equalsIgnoreCase("true")) {
            ExportGroupRestRep export = findExportGroup(hostOrClusterId, projectId, vArrayId, client);
            StoragePortGroupRestRepList portGroups = client.varrays().getStoragePortGroups(vArrayId,
                    (export != null ? export.getId() : null), null, vpoolId, null, true);
            return createPortGroupOptions(portGroups.getStoragePortGroups());
        }

        return options;
    }

    @Asset("exportVolumeForHostPortGroups")
    @AssetDependencies({ "virtualArray", "blockVirtualPool", "windowsHost", "project" })
    public List<AssetOption> getExportVolumeForWindowsHostPortGroups(AssetOptionsContext ctx, URI vArrayId, URI vpoolId,
            URI hostOrClusterId, URI projectId) {
        return getExportVolumeForHostPortGroups(ctx, vArrayId, vpoolId, hostOrClusterId, projectId);
    }

    @Asset("exportVolumeForHostPortGroups")
    @AssetDependencies({ "virtualArray", "blockVirtualPool", "linuxHost", "project" })
    public List<AssetOption> getExportVolumeForLinuxHostPortGroups(AssetOptionsContext ctx, URI vArrayId, URI vpoolId, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumeForHostPortGroups(ctx, vArrayId, vpoolId, hostOrClusterId, projectId);
    }

    @Asset("exportVolumeForHostPortGroups")
    @AssetDependencies({ "virtualArray", "blockVirtualPool", "aixHost", "project" })
    public List<AssetOption> getExportVolumeForAixHostPortGroups(AssetOptionsContext ctx, URI vArrayId, URI vpoolId, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumeForHostPortGroups(ctx, vArrayId, vpoolId, hostOrClusterId, projectId);
    }

    @Asset("exportVolumeForHostPortGroups")
    @AssetDependencies({ "virtualArray", "blockVirtualPool", "hpuxHost", "project" })
    public List<AssetOption> getExportVolumeForHpuxHostPortGroups(AssetOptionsContext ctx, URI vArrayId, URI vpoolId, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumeForHostPortGroups(ctx, vArrayId, vpoolId, hostOrClusterId, projectId);
    }

    @Asset("exportVolumeForHostPortGroups")
    @AssetDependencies({ "virtualArray", "blockVirtualPool", "esxHost", "project" })
    public List<AssetOption> getExportVolumeForEsxHostPortGroups(AssetOptionsContext ctx, URI vArrayId, URI vpoolId, URI hostOrClusterId,
            URI projectId) {
        return getExportVolumeForHostPortGroups(ctx, vArrayId, vpoolId, hostOrClusterId, projectId);
    }

    @Asset("exportSnapshotForHostPortGroups")
    @AssetDependencies({ "host", "project" })
    public List<AssetOption> getExportSnapshotForHostPortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        return getExportSnapshotForHostPortGroups(ctx, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportVolumeForComputePortGroups")
    @AssetDependencies({ "blockVirtualArray", "blockVirtualPool", "project" })
    public List<AssetOption> getExportVolumeForComputePortGroups(AssetOptionsContext ctx, URI vArrayId, URI vpoolId, URI projectId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        SimpleValueRep value = client.customConfigs().getCustomConfigTypeValue(VMAX_PORT_GROUP_ENABLED, VMAX);
        if (value.getValue().equalsIgnoreCase("true")) {
            StoragePortGroupRestRepList portGroups = client.varrays().getStoragePortGroups(vArrayId, null, null, vpoolId, null, true);
            return createPortGroupOptions(portGroups.getStoragePortGroups());
        }

        return options;
    }

    @Asset("exportSnapshotForHostPortGroups")
    @AssetDependencies({ "unassignedBlockSnapshot", "host", "project" })
    public List<AssetOption> getExportSnapshotForHostPortGroups(AssetOptionsContext ctx, String selectedSnapshots, URI hostOrClusterId,
            URI projectId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        SimpleValueRep value = client.customConfigs().getCustomConfigTypeValue(VMAX_PORT_GROUP_ENABLED, VMAX);
        if (value.getValue().equalsIgnoreCase("true")) {
            List<URI> snapshotIds = Lists.newArrayList();
            info("Snapshots selected by user: %s", selectedSnapshots);
            List<String> parsedSnapshotIds = TextUtils.parseCSV(selectedSnapshots);
            for (String id : parsedSnapshotIds) {
                snapshotIds.add(uri(id));
            }

            List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByIds(snapshotIds);

            Set<URI> virtualArrays = new HashSet<URI>();
            Set<URI> storageSystems = new HashSet<URI>();
            for (BlockSnapshotRestRep snapshot : snapshots) {
                virtualArrays.add(snapshot.getVirtualArray().getId());
                storageSystems.add(snapshot.getStorageController());
            }

            if (virtualArrays.size() == 1 && storageSystems.size() == 1) {
                Iterator<URI> it = virtualArrays.iterator();
                URI varrayId = it.next();

                ExportGroupRestRep export = findExportGroup(hostOrClusterId, projectId, varrayId, client);

                Iterator<URI> ssIt = storageSystems.iterator();
                StoragePortGroupRestRepList portGroups = client.varrays().getStoragePortGroups(varrayId,
                        export != null ? export.getId() : null, ssIt.next(), null, null, true);
                return createPortGroupOptions(portGroups.getStoragePortGroups());
            }
        }
        return options;
    }

    @Asset("exportContinousCopyForHostPortGroups")
    @AssetDependencies({ "volumeWithContinuousCopies", "host", "project" })
    public List<AssetOption> getExportContinousCopyForHostPortGroups(AssetOptionsContext ctx, URI volumeId, URI hostOrClusterId,
            URI projectId) {
        return getExportContinousCopyForHostPortGroups(ctx, volumeId, EMPTY_STRING, hostOrClusterId, projectId);
    }

    @Asset("exportContinousCopyForHostPortGroups")
    @AssetDependencies({ "volumeWithContinuousCopies", "unassignedBlockContinuousCopies", "host", "project" })
    public List<AssetOption> getExportContinousCopyForHostPortGroups(AssetOptionsContext ctx, URI volumeId, String selectedCopies,
            URI hostOrClusterId, URI projectId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        SimpleValueRep value = client.customConfigs().getCustomConfigTypeValue(VMAX_PORT_GROUP_ENABLED, VMAX);
        if (value.getValue().equalsIgnoreCase("true")) {
            List<URI> snapshotIds = Lists.newArrayList();
            info("Continous Copies selected by user: %s", selectedCopies);
            List<String> parsedCopiesIds = TextUtils.parseCSV(selectedCopies);
            for (String id : parsedCopiesIds) {
                snapshotIds.add(uri(id));
            }

            List<BlockMirrorRestRep> copies = client.blockVolumes().getContinuousCopies(volumeId);

            Set<URI> virtualArrays = new HashSet<URI>();
            Set<URI> storageSystems = new HashSet<URI>();
            Set<URI> virtualPools = new HashSet<URI>();
            for (BlockMirrorRestRep copy : copies) {
                virtualArrays.add(copy.getVirtualArray().getId());
                storageSystems.add(copy.getStorageController());
                virtualPools.add(copy.getVirtualPool().getId());
            }

            if (virtualArrays.size() == 1 && storageSystems.size() == 1 && virtualPools.size() == 1) {
                Iterator<URI> it = virtualArrays.iterator();
                URI varrayId = it.next();

                ExportGroupRestRep export = findExportGroup(hostOrClusterId, projectId, varrayId, client);

                Iterator<URI> ssIt = storageSystems.iterator();
                Iterator<URI> vpIt = virtualPools.iterator();
                StoragePortGroupRestRepList portGroups = client.varrays().getStoragePortGroups(varrayId,
                        export != null ? export.getId() : null, ssIt.next(), vpIt.next(), null, true);
                return createPortGroupOptions(portGroups.getStoragePortGroups());
            }
        }
        return options;
    }

    private List<AssetOption> createPortGroupOptions(List<StoragePortGroupRestRep> portGroups) {
        List<AssetOption> options = Lists.newArrayList();
        for (StoragePortGroupRestRep group : portGroups) {
            String portMetric = (group.getPortMetric() != null) ? String.valueOf(Math.round(group.getPortMetric() * 100 / 100)) + "%"
                    : "N/A";
            String volumeCount = (group.getVolumeCount() != null) ? String.valueOf(group.getVolumeCount()) : "N/A";
            String nGuid = group.getNativeGuid();
            String nativeGuid = EMPTY_STRING;
            try {
                // Remove the PG name from the native GUID.
                // Ex:
                // From -> SYMMETRIX+000196801518+PORTGROUP+lglw7136_pg
                // To -> SYMMETRIX+000196801518
                nativeGuid = nGuid.replaceAll("\\+PORTGROUP.*$", "");
            } catch (Exception e) {
                nativeGuid = nGuid;
                warn("Issue encountered parsing native guid %s, exception: %s", nativeGuid, e.getMessage());
            }
            String label = getMessage("exportPortGroup.portGroups", group.getName(), nativeGuid, portMetric, volumeCount);
            options.add(new AssetOption(group.getId(), label));
        }
        return options;
    }

    @Asset("exportedBlockVolume")
    @AssetDependencies("project")
    public List<AssetOption> getExportedVolumes(AssetOptionsContext ctx, URI project) {
        debug("getting source block volumes (project=%s)", project);
        final ViPRCoreClient client = api(ctx);
        // Filter volumes that are not RP Metadata
        List<URI> volumeIds = getExportedVolumeIds(ctx, project);
        FilterChain<VolumeRestRep> filter = new FilterChain<VolumeRestRep>(RecoverPointPersonalityFilter.METADATA.not());
        filter.and(new BlockVolumeVMFSDatastoreFilter().not());
        filter.and(new BlockVolumeBootVolumeFilter().not());
        filter.and(new BlockVolumeMountPointFilter().not());
        List<VolumeRestRep> volumes = client.blockVolumes().getByIds(volumeIds, filter);
        return createVolumeWithVarrayOptions(client, volumes);
    }

    /**
     * Return a list of all exported volume ids for a given project id.
     *
     * @param ctx asset option content
     * @param project id
     * @return list of exported volume ids
     */
    private List<URI> getExportedVolumeIds(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<URI> volumeIds = Lists.newArrayList();
        for (ExportGroupRestRep export : client.blockExports().findByProject(project)) {
            for (ExportBlockParam resource : export.getVolumes()) {
                if (ResourceType.isType(ResourceType.VOLUME, resource.getId())) {
                    volumeIds.add(resource.getId());
                }
            }
        }

        return volumeIds;
    }

    @Asset("deletionType")
    public List<AssetOption> getDeletionType(AssetOptionsContext ctx) {
        List<AssetOption> options = new ArrayList<>();

        options.add(newAssetOption(VolumeDeleteTypeEnum.FULL.toString(), "block.deletion.type.full"));
        options.add(newAssetOption(VolumeDeleteTypeEnum.VIPR_ONLY.toString(), "block.deletion.type.vipr_only"));

        return options;
    }

    @Asset("fullOnlyDeletionType")
    public List<AssetOption> getFullOnlyDeletionType(AssetOptionsContext ctx) {
        List<AssetOption> options = new ArrayList<>();

        options.add(newAssetOption(VolumeDeleteTypeEnum.FULL.toString(), "block.deletion.type.full"));

        return options;
    }

    @Asset("vplexMigrationChangeOperation")
    public List<AssetOption> getVplexMigrationChangeOperations(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        options.add(newAssetOption(VirtualPoolChangeOperationEnum.VPLEX_DATA_MIGRATION.name(), "virtualPoolChange.operation."
                + VirtualPoolChangeOperationEnum.VPLEX_DATA_MIGRATION.name()));
        options.add(newAssetOption(VirtualPoolChangeOperationEnum.VPLEX_LOCAL_TO_DISTRIBUTED.name(), "virtualPoolChange.operation."
                + VirtualPoolChangeOperationEnum.VPLEX_LOCAL_TO_DISTRIBUTED.name()));
        return options;
    }

    @Asset("virtualPoolChangeOperation")
    public List<AssetOption> getVirtualPoolchangeOperations(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (VirtualPoolChangeOperationEnum operation : VirtualPoolChangeOperationEnum.values()) {
            options.add(newAssetOption(operation.name(), "virtualPoolChange.operation." + operation.name()));
        }
        return options;
    }

    @Asset("virtualPoolChangeVolumeWithSource")
    @AssetDependencies({ "project", "blockVirtualPool" })
    public List<AssetOption> getVpoolChangeVolumes(AssetOptionsContext ctx, URI projectId, URI virtualPoolId) {
        return createVolumeOptions(api(ctx), listSourceVolumes(api(ctx), projectId,
                new VirtualPoolFilter(virtualPoolId)));
    }

    @Asset("virtualPoolChangeVolumeWithSource")
    @AssetDependencies({ "project", "blockVirtualPool", "displayJournals" })
    public List<AssetOption> getVpoolChangeVolumes(AssetOptionsContext ctx, URI projectId, URI virtualPoolId, String displayJournals) {
        if (YES_VALUE.equals(displayJournals)) {
            return createVolumeOptions(api(ctx), listJournalVolumes(api(ctx), projectId, new VirtualPoolFilter(virtualPoolId)));
        }
        return createVolumeOptions(api(ctx), listSourceVolumes(api(ctx), projectId, new VirtualPoolFilter(virtualPoolId)));
    }

    @Asset("virtualPoolChangeVolumeWithSourceFilter")
    @AssetDependencies({ "project", "blockVirtualPool", "sourceVolumeFilter" })
    public List<AssetOption> getVpoolChangeVolumes(AssetOptionsContext ctx, URI projectId, URI virtualPoolId, int volumePage) {
        List<AssetOption> options = createVolumeOptions(api(ctx),
                listSourceVolumes(api(ctx), projectId, new VirtualPoolFilter(virtualPoolId)));
        return VirtualDataCenterProvider.getVolumeSublist(volumePage, options);
    }

    @Asset("sourceVolumeFilter")
    @AssetDependencies({ "project", "blockVirtualPool" })
    public List<AssetOption> getVolumeFilter(AssetOptionsContext ctx, URI projectId, URI virtualPoolId) {
        List<String> volumeNames = Lists.newArrayList();
        for (VolumeRestRep volume : listSourceVolumes(api(ctx), projectId, new VirtualPoolFilter(virtualPoolId))) {
            volumeNames.add(volume.getName());
        }
        Collections.sort(volumeNames, new StringComparator(false));
        return VirtualDataCenterProvider.getVolumeFilterOptions(volumeNames);
    }

    @Asset("virtualArrayChangeVolume")
    @AssetDependencies({ "project", "targetVirtualArray" })
    public List<AssetOption> getVirtualArrayChangeCandidateVolumes(AssetOptionsContext ctx, URI projectId, URI varrayId) {
        ViPRCoreClient client = api(ctx);
        NamedVolumesList volumeList = client.blockVolumes().listVirtualArrayChangeCandidates(projectId, varrayId);
        List<VolumeRestRep> volumes = client.blockVolumes().getByRefs(volumeList.getVolumes());
        return createVolumeOptions(client, volumes);
    }

    @Asset("targetVirtualArray")
    @AssetDependencies("project")
    public List<AssetOption> getTargetVplexVolumeVirtualArrays(AssetOptionsContext ctx, URI projectId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> targets = Lists.newArrayList();
        for (VirtualArrayRestRep varray : client.varrays().getByTenant(ctx.getTenant())) {
            targets.add(createBaseResourceOption(varray));
        }
        return targets;
    }

    @Asset("migrationTargetVirtualPool")
    @AssetDependencies({ "project", "blockVirtualPool", "vplexMigrationChangeOperation", "displayJournals" })
    public List<AssetOption> getMigrationTargetVirtualPools(AssetOptionsContext ctx, URI projectId, URI virtualPoolId,
            String vpoolChangeOperation, String displayJournals) {
        return getTargetVirtualPoolsForVpool(ctx, projectId, virtualPoolId, vpoolChangeOperation, null, displayJournals);
    }

    @Asset("mobilityMigrationTargetVirtualPool")
    public List<AssetOption> getMobilityMigrationTargetVirtualPools(AssetOptionsContext ctx) {
        return this.createBaseResourceOptions(api(ctx).blockVpools().getByTenant(ctx.getTenant()));
    }

    @Asset("journalCopyName")
    @AssetDependencies("rpConsistencyGroupByProject")
    public List<AssetOption> getCopyNameByConsistencyGroup(AssetOptionsContext ctx, URI consistencyGroupId) {
        ViPRCoreClient client = api(ctx);
        List<RelatedResourceRep> volumes = client.blockConsistencyGroups().get(consistencyGroupId).getVolumes();
        Set<String> copyNames = Sets.newHashSet();

        if (volumes != null && !volumes.isEmpty()) {
            RelatedResourceRep volume = volumes.get(0);
            VolumeRestRep volumeRep = client.blockVolumes().get(volume);
            if (volumeRep.getProtection() != null && volumeRep.getProtection().getRpRep() != null) {
                if (volumeRep.getProtection().getRpRep().getCopyName() != null) {
                    String copyName = volumeRep.getProtection().getRpRep().getCopyName();
                    copyNames.add(copyName);
                }
                /* getByIds(haVolumeIds, null) is returning a empty list which was the cause of COP-36489.
                Details: 
                getByIds() will eventually call AbstractResources.accept() method. This method has a condition to 
                remove items (HaVolumes) which have their internal flag set to True. HA Volumes have this flag set.
                Hence we can't use getByIds(...) with filter or without it to get HA Volume list. 
                Hence implementing the below code which resolves COP-36489.
                */
                if (volumeRep.getHaVolumes() != null) {
                    List<RelatedResourceRep> haVolumes = volumeRep.getHaVolumes();
                  
                    for (RelatedResourceRep haVolume : haVolumes) {
                    	VolumeRestRep haVolumeRep = client.blockVolumes().get(haVolume);
                    	if (haVolumeRep.getProtection() != null && haVolumeRep.getProtection().getRpRep() != null
                                && haVolumeRep.getProtection().getRpRep().getCopyName() != null) {
                            String copyName = haVolumeRep.getProtection().getRpRep().getCopyName();
                            copyNames.add(copyName);
                        }
                    }
                   
                }
                if (volumeRep.getProtection().getRpRep().getRpTargets() != null) {
                    List<VirtualArrayRelatedResourceRep> targetVolumes = volumeRep.getProtection().getRpRep().getRpTargets();
                    List<URI> targetVolumeIds = new ArrayList<URI>();
                    for (VirtualArrayRelatedResourceRep targetVolume : targetVolumes) {
                        targetVolumeIds.add(targetVolume.getId());
                    }
                    List<VolumeRestRep> targetVolumeReps = client.blockVolumes().getByIds(targetVolumeIds, null);
                    for (VolumeRestRep targetVolumeRep : targetVolumeReps) {
                        String copyName = targetVolumeRep.getProtection().getRpRep().getCopyName();
                        copyNames.add(copyName);
                    }
                }
            }
        }
        List<AssetOption> copyNameAssets = Lists.newArrayList();
        for (String copyName : copyNames) {
            copyNameAssets.add(newAssetOption(copyName, copyName));
        }
        AssetOptionsUtils.sortOptionsByLabel(copyNameAssets);
        return copyNameAssets;
    }

    @Asset("virtualArrayByConsistencyGroup")
    @AssetDependencies("rpConsistencyGroupByProject")
    public List<AssetOption> getVirtualArrayByConsistencyGroup(AssetOptionsContext ctx, URI consistencyGroupId) {
        ViPRCoreClient client = api(ctx);
        List<RelatedResourceRep> volumes = client.blockConsistencyGroups().get(consistencyGroupId).getVolumes();
        List<AssetOption> targets = Lists.newArrayList();

        if (!volumes.isEmpty()) {
            RelatedResourceRep varrayId = client.blockVolumes().get(volumes.get(0)).getVirtualArray();
            VirtualArrayRestRep virtualArray = client.varrays().get(varrayId);
            targets.add(createBaseResourceOption(virtualArray));
        }
        return targets;
    }

    @Asset("targetVirtualPool")
    @AssetDependencies({ "sourceBlockVolume", "virtualPoolChangeOperation" })
    public List<AssetOption> getTargetVirtualPools(AssetOptionsContext ctx, URI volumeId, String vpoolChangeOperation) {
        List<VirtualPoolChangeRep> vpoolChanges = api(ctx).blockVolumes().listVirtualPoolChangeCandidates(volumeId);
        return createVpoolChangeOptions(vpoolChangeOperation, vpoolChanges);
    }

    @Asset("targetVirtualPool")
    @AssetDependencies({ "project", "blockVirtualPool", "virtualPoolChangeOperation" })
    public List<AssetOption> getTargetVirtualPoolsForVpool(AssetOptionsContext ctx, URI projectId, URI virtualPoolId,
            String vpoolChangeOperation) {
        // This is an intermediate asset that is evaluated while waiting for the "virtualPoolChangeVolumeWithSourceFilter"
        // asset dependency to be filled in by the user. The desired behaviour is that the below "targetVirtualPool"
        // asset with the dependency on "virtualPoolChangeVolumeWithSourceFilter" will be fired instead.
        //
        // The "virtualPoolChangeVolumeWithSourceFilter" asset is a select-many component. When there
        // are NO values selected, the asset is not sent down at all and this "targetVirtualPool" asset will
        // be evaluated instead and return an empty list.
        //
        // Once the user selects at least 1 volume in the "virtualPoolChangeVolumeWithSourceFilter" asset then the below
        // "targetVirtualPool" asset will be evaluated and the drop down will be populated.
        info("No filtered volumes selected, returning empty list.");
        return Collections.emptyList();
    }

    @Asset("targetVirtualPool")
    @AssetDependencies({ "project", "blockVirtualPool", "virtualPoolChangeOperation", "virtualPoolChangeVolumeWithSourceFilter" })
    public List<AssetOption> getTargetVirtualPoolsForVpool(AssetOptionsContext ctx, URI projectId, URI virtualPoolId,
            String vpoolChangeOperation, String filteredVolumes) {
        return getTargetVirtualPoolsForVpool(ctx, projectId, virtualPoolId, vpoolChangeOperation, filteredVolumes, null);
    }

    @Asset("targetVirtualPool")
    @AssetDependencies({ "project", "blockVirtualPool", "virtualPoolChangeOperation",
            "virtualPoolChangeVolumeWithSourceFilter", "displayJournals" })
    public List<AssetOption> getTargetVirtualPoolsForVpool(AssetOptionsContext ctx, URI projectId, URI virtualPoolId,
            String vpoolChangeOperation, String filteredVolumes, String displayJournals) {
        List<URI> volumeIds = Lists.newArrayList();
        if (filteredVolumes != null && !filteredVolumes.isEmpty()) {
            // Best case we are passed in the volumes selected by the user
            // as comma delimited string so we do not have to load them from
            // the API.
            info("Filtered volumes selected by user: %s", filteredVolumes);
            List<String> parsedVolumeIds = TextUtils.parseCSV(filteredVolumes);
            for (String id : parsedVolumeIds) {
                volumeIds.add(uri(id));
            }
        } else {
            // Worst case we need to get the volumes from the API.
            info("Loading all volumes for vpool: %s", virtualPoolId.toString());
            List<VolumeRestRep> volumes = null;
            if (YES_VALUE.equals(displayJournals)) {
                volumes = listJournalVolumes(api(ctx), projectId, new VirtualPoolFilter(virtualPoolId));
            } else {
                volumes = listSourceVolumes(api(ctx), projectId, new VirtualPoolFilter(virtualPoolId));
            }
            for (VolumeRestRep volume : volumes) {
                volumeIds.add(volume.getId());
            }
        }

        if (CollectionUtils.isNotEmpty(volumeIds)) {
            BulkIdParam input = new BulkIdParam();
            input.setIds(volumeIds);
            List<VirtualPoolChangeRep> vpoolChanges = api(ctx).blockVpools().listVirtualPoolChangeCandidates(virtualPoolId, input);
            return createVpoolChangeOptions(vpoolChangeOperation, vpoolChanges);
        }
        return Collections.emptyList();
    }

    @Asset("volumeExport")
    @AssetDependencies("blockVolume")
    public List<AssetOption> getExportsForVolume(AssetOptionsContext ctx, URI volumeId) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForVolume(ctx, volumeId);
        return createNamedResourceOptions(exports);
    }

    @Asset("snapshotExport")
    @AssetDependencies("exportedBlockSnapshot")
    public List<AssetOption> getExportsForSnapshot(AssetOptionsContext ctx, URI snapshotId) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForSnapshot(ctx, snapshotId);
        return createNamedResourceOptions(exports);
    }

    @Asset("continuousCopyExport")
    @AssetDependencies("exportedBlockContinuousCopy")
    public List<AssetOption> getExportsForContinuousCopy(AssetOptionsContext ctx, URI copyId) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForVolume(ctx, copyId);
        return createNamedResourceOptions(exports);
    }

    @Asset("volumeExport")
    @AssetDependencies("sourceBlockVolume")
    public List<AssetOption> getExportsForSourceVolume(AssetOptionsContext ctx, URI volumeId) {
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForVolume(ctx, volumeId);
        return createNamedResourceOptions(exports);
    }

    @Asset("volumeExport")
    @AssetDependencies("exportedBlockVolume")
    public List<AssetOption> getExportsForExportedVolume(AssetOptionsContext ctx, URI volumeId) {
        final ViPRCoreClient client = api(ctx);
        Set<NamedRelatedResourceRep> exports = getUniqueExportsForVolume(ctx, volumeId);
        return createExportWithVarrayOptions(client, client.blockExports().getByRefs(exports));
    }

    /**
     * Gets the set of unique exports for a given volume.
     * 
     * @param ctx
     *            the asset options context
     * @param volumeId
     *            the volume id.
     * @return the export resource IDs.
     */
    protected Set<NamedRelatedResourceRep> getUniqueExportsForVolume(AssetOptionsContext ctx, URI volumeId) {
        return convertITLListToRelatedResourceRestReps(api(ctx).blockVolumes().getExports(volumeId));
    }

    /** Gets the set of unique exports for a given snapshot */
    protected Set<NamedRelatedResourceRep> getUniqueExportsForSnapshot(AssetOptionsContext ctx, URI snapshotId) {
        return convertITLListToRelatedResourceRestReps(api(ctx).blockSnapshots().listExports(snapshotId));
    }

    /** Find the unique exports for the given */
    protected Set<NamedRelatedResourceRep> convertITLListToRelatedResourceRestReps(List<ITLRestRep> exports) {
        Set<NamedRelatedResourceRep> relatedRestReps = Sets.newHashSet();
        for (ITLRestRep export : exports) {
            relatedRestReps.add(export.getExport());
        }
        return relatedRestReps;
    }

    protected static List<AssetOption> createExportWithVarrayOptions(ViPRCoreClient client,
            Collection<? extends ExportGroupRestRep> exportObjects) {
        List<URI> varrayIds = getExportVirtualArrayIds(exportObjects);
        Map<URI, VirtualArrayRestRep> varrayNames = getVirutalArrayNames(client, varrayIds);
        List<AssetOption> options = Lists.newArrayList();
        for (ExportGroupRestRep export : exportObjects) {
            options.add(createExportWithVarrayOption(export, varrayNames));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected static AssetOption createExportWithVarrayOption(ExportGroupRestRep export, Map<URI, VirtualArrayRestRep> varrayNames) {
        String varrayName = varrayNames.get(export.getVirtualArray().getId()).getName();
        String label = getMessage("block.unexport.export", export.getName(), varrayName);
        return new AssetOption(export.getId(), label);
    }

    private static List<URI> getExportVirtualArrayIds(Collection<? extends ExportGroupRestRep> exportObjects) {
        List<URI> varrayIds = Lists.newArrayList();
        for (ExportGroupRestRep export : exportObjects) {
            varrayIds.add(export.getVirtualArray().getId());
        }
        return varrayIds;
    }

    @Asset("exportPathVirtualArray")
    public List<AssetOption> getExportPathVirtualArray(AssetOptionsContext ctx) {
        ViPRCoreClient client = api(ctx);
        return createBaseResourceOptions(client.varrays().getByTenant(ctx.getTenant()));
    }

    @Asset("exportPathVirtualArray")
    @AssetDependencies({ "exportPathExport", "exportPathStorageSystem" })
    public List<AssetOption> getExportPathVirtualArray(AssetOptionsContext ctx, URI exportId, URI storageSystemId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        ExportGroupRestRep export = client.blockExports().get(exportId);

        List<URI> vArrayIds = new ArrayList<URI>();
        vArrayIds.add(export.getVirtualArray().getId());
        List<StringHashMapEntry> altArrays = export.getAltVirtualArrays() != null ? export.getAltVirtualArrays()
                : new ArrayList<StringHashMapEntry>();
        for (StringHashMapEntry altArray : altArrays) {
            if (altArray.getName().equalsIgnoreCase(storageSystemId.toString())) {
                vArrayIds.add(URI.create(altArray.getValue()));
            }
        }

        List<VirtualArrayRestRep> vArrays = client.varrays().getByIds(vArrayIds);

        for (VirtualArrayRestRep vArray : vArrays) {
            options.add(new AssetOption(vArray.getId(), vArray.getName()));
        }

        return options;
    }

    @Asset("exportPathExport")
    @AssetDependencies({ "project", "host" })
    public List<AssetOption> getExportPathExports(AssetOptionsContext ctx, URI projectId, URI hostOrClusterId) {
        ViPRCoreClient client = api(ctx);
        List<ExportGroupRestRep> exports = findExportGroups(hostOrClusterId, projectId, null, client);
        return createExportWithVarrayOptions(client, exports);
    }

    @Asset("exportPathMinPathsOptions")
    public List<AssetOption> getExportPathMinOptions(AssetOptionsContext ctx) {
        return generatePathOptions(EXPORT_PATH_MIN, EXPORT_PATH_MAX);
    }

    @Asset("exportPathMaxPathsOptions")
    public List<AssetOption> getExportPathMaxOptions(AssetOptionsContext ctx) {
        return generatePathOptions(EXPORT_PATH_MIN, EXPORT_PATH_MAX);
    }

    @Asset("exportPathMaxPathsOptions")
    @AssetDependencies({ "exportPathMinPathsOptions" })
    public List<AssetOption> getExportPathMaxOptions(AssetOptionsContext ctx, int min) {
        return generatePathOptions(min, EXPORT_PATH_MAX);
    }

    @Asset("exportPathPathsPerInitiatorOptions")
    public List<AssetOption> getExportPathPathsPerOptions(AssetOptionsContext ctx) {
        return generatePathOptions(EXPORT_PATH_MIN, EXPORT_PATH_MAX);
    }

    @Asset("exportPathPathsPerInitiatorOptions")
    @AssetDependencies({ "exportPathMaxPathsOptions" })
    public List<AssetOption> getExportPathPathsPerOptions(AssetOptionsContext ctx, int max) {
        return generatePathOptions(EXPORT_PATH_MIN, max);
    }

    private List<AssetOption> generatePathOptions(int min, int max) {
        List<AssetOption> options = Lists.newArrayList();

        for (int i = min; i <= max; ++i) {
            options.add(new AssetOption(Integer.toString(i), Integer.toString(i)));
        }

        return options;
    }

    @Asset("exportPathExistingPath")
    public List<AssetOption> getExportPathExistingPath(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();

        options.add(new AssetOption(YES_VALUE, YES_VALUE));
        options.add(new AssetOption(NO_VALUE, NO_VALUE));

        return options;
    }

    @Asset("exportPathStorageSystem")
    public List<AssetOption> getExportPathStorageSystem(AssetOptionsContext ctx) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        List<StorageSystemRestRep> storageSystems = client.storageSystems().getAll();

        for (StorageSystemRestRep storageSystem : storageSystems) {
            String systemType = storageSystem.getSystemType();
            if (Type.vmax.name().equalsIgnoreCase(systemType) ||
                    Type.vplex.name().equalsIgnoreCase(systemType)) {
                options.add(new AssetOption(storageSystem.getId(), storageSystem.getName()));
            }
        }

        return options;
    }

    @SuppressWarnings("incomplete-switch")
    @Asset("exportPathStorageSystem")
    @AssetDependencies({ "exportPathExport" })
    public List<AssetOption> getExportPathStorageSystem(AssetOptionsContext ctx, URI exportId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        List<URI> storageSystemIds = new ArrayList<URI>();

        ExportGroupRestRep export = client.blockExports().get(exportId);
        List<ExportBlockParam> volumes = export.getVolumes();
        for (ExportBlockParam volume : volumes) {
            URI resourceId = volume.getId();
            ResourceType volumeType = ResourceType.fromResourceId(resourceId.toString());
            switch (volumeType) {
                case VOLUME:
                    VolumeRestRep v = client.blockVolumes().get(resourceId);
                    if (v != null) {
                        storageSystemIds.add(v.getStorageController());
                    }
                    break;
                case BLOCK_SNAPSHOT:
                    BlockSnapshotRestRep s = client.blockSnapshots().get(resourceId);
                    if (s != null) {
                        storageSystemIds.add(s.getStorageController());
                    }
                    break;
            }
        }

        List<StorageSystemRestRep> storageSystems = client.storageSystems().getByIds(storageSystemIds);

        for (StorageSystemRestRep storageSystem : storageSystems) {
            String systemType = storageSystem.getSystemType();
            if (Type.vmax.name().equalsIgnoreCase(systemType) ||
                    Type.vplex.name().equalsIgnoreCase(systemType)) {
                options.add(new AssetOption(storageSystem.getId(), storageSystem.getName()));
            }
        }

        return options;
    }

    @Asset("exportPathPorts")
    @AssetDependencies({ "exportPathVirtualArray", "exportPathStorageSystem", "exportPathExport" })
    public List<AssetOption> getExportPathPorts(AssetOptionsContext ctx, URI vArrayId, URI storageSystemId,
            URI exportId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        // Get all the PGs for the varray/storage system/EG combo then check to
        // see if there are any non-mutable PGs;
        // if there are the storage ports displayed to the user would be limited
        // to just those ones.
        StoragePortGroupRestRepList portGroupsRestRep = client.varrays().getStoragePortGroups(vArrayId, exportId,
                storageSystemId, null, null, false);

        // Keep a list of ports from the non-mutable PGs. This could remain
        // empty if there are no PGs or none that are non-mutable.
        List<URI> nonMutablePGPortURIs = new ArrayList<URI>();

        if (portGroupsRestRep != null) {
            // Drill down to get the PG and the storage ports
            List<StoragePortGroupRestRep> portGroups = portGroupsRestRep.getStoragePortGroups();
            if (!CollectionUtils.isEmpty(portGroups)) {
                for (StoragePortGroupRestRep pg : portGroups) {
                    // Check to see if the PG is non-mutable
                    if (!pg.getMutable()) {
                        // Keep track of these storage ports, they will be used
                        // to filter out
                        // other storage ports.
                        StoragePortList pgPortsList = pg.getStoragePorts();
                        List<NamedRelatedResourceRep> pgPorts = pgPortsList.getPorts();
                        for (NamedRelatedResourceRep pgPort : pgPorts) {
                            nonMutablePGPortURIs.add(pgPort.getId());
                        }
                    }
                }
            }
        }

        List<StoragePortRestRep> ports = client.storagePorts().getByVirtualArray(vArrayId);

        for (StoragePortRestRep port : ports) {
            // Check to see if this port needs to be filtered out.
            boolean filterOutPortBasedOnPG = (!nonMutablePGPortURIs.isEmpty()) ? !nonMutablePGPortURIs.contains(port.getId()) : false;

            if (!filterOutPortBasedOnPG) {
                if (port.getPortType().equals(StoragePort.PortType.frontend.toString()) &&
                        port.getStorageDevice().getId().equals(storageSystemId) &&
                        port.getOperationalStatus().equals(StoragePort.OperationalStatus.OK.toString())) {
                    if (port.getNetwork() != null) {
                        String portPercentBusy = (port.getPortPercentBusy() != null)
                                ? String.valueOf(Math.round(port.getPortPercentBusy() * 100 / 100)) + "%" : "N/A";
                        String networkName = client.networks().get(port.getNetwork().getId()).getName();
                        String label = getMessage("exportPathAdjustment.ports", port.getPortName(), networkName,
                                port.getPortNetworkId(), portPercentBusy);
                        options.add(new AssetOption(port.getId(), label));
                    }
                }
            }
        }

        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("exportPathPreviewStorageSystem")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath", "exportPathStorageSystem" })
    public List<AssetOption> getPreviewStorageSystem(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId) {

        return getPreviewStorageSystem(ctx, hostOrClusterId, vArrayId, exportId, minPaths, maxPaths, pathsPerInitiator,
                useExisting, storageSystemId, EMPTY_STRING);
    }

    @Asset("exportPathPreviewStorageSystem")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath",
            "exportPathStorageSystem", "exportPathPorts" })
    public List<AssetOption> getPreviewStorageSystem(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId, String ports) {
        List<AssetOption> options = Lists.newArrayList();
        List<URI> exportPathPorts = parseExportPathPorts(ports);
        ExportPathsAdjustmentPreviewRestRep portPreview = generateExportPathPreview(ctx, hostOrClusterId, vArrayId,
                exportId, minPaths, maxPaths, pathsPerInitiator, useExisting, storageSystemId, exportPathPorts);
        options.add(new AssetOption(portPreview.getStorageSystem(), portPreview.getStorageSystem()));
        return options;
    }

    @Asset("exportPathResultingPaths")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath", "exportPathStorageSystem" })
    public List<AssetOption> getResultingPaths(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId) {

        return getResultingPaths(ctx, hostOrClusterId, vArrayId, exportId, minPaths, maxPaths, pathsPerInitiator,
                useExisting, storageSystemId, EMPTY_STRING);
    }

    @Asset("exportPathResultingPaths")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath",
            "exportPathStorageSystem", "exportPathPorts" })
    public List<AssetOption> getResultingPaths(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId, String ports) {
        List<URI> exportPathPorts = parseExportPathPorts(ports);
        ExportPathsAdjustmentPreviewRestRep portPreview = generateExportPathPreview(ctx, hostOrClusterId, vArrayId,
                exportId, minPaths, maxPaths, pathsPerInitiator, useExisting, storageSystemId, exportPathPorts);
        List<InitiatorPortMapRestRep> addedList = portPreview.getAdjustedPaths();

        return buildPathOptions(ctx, addedList, "exportPathAdjustment.resultingPaths");
    }

    @Asset("exportPathRemovedPaths")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath", "exportPathStorageSystem" })
    public List<AssetOption> getRemovedPaths(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId) {

        return getRemovedPaths(ctx, hostOrClusterId, vArrayId, exportId, minPaths, maxPaths, pathsPerInitiator,
                useExisting, storageSystemId, EMPTY_STRING);
    }

    @Asset("exportPathRemovedPaths")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath",
            "exportPathStorageSystem", "exportPathPorts" })
    public List<AssetOption> getRemovedPaths(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId, String ports) {
        List<URI> exportPathPorts = parseExportPathPorts(ports);
        ExportPathsAdjustmentPreviewRestRep portPreview = generateExportPathPreview(ctx, hostOrClusterId, vArrayId,
                exportId, minPaths, maxPaths, pathsPerInitiator, useExisting, storageSystemId, exportPathPorts);
        List<InitiatorPortMapRestRep> removedList = portPreview.getRemovedPaths();

        return buildPathOptions(ctx, removedList, "exportPathAdjustment.removedPaths");
    }

    private List<URI> parseExportPathPorts(String input) {
        List<URI> ports = new ArrayList<URI>();

        List<String> parsedIds = TextUtils.parseCSV(input);
        for (String id : parsedIds) {
            ports.add(uri(id));
        }
        return ports;
    }

    /* helper for building the list of asset option for affected and removed paths */
    private List<AssetOption> buildPathOptions(AssetOptionsContext ctx, List<InitiatorPortMapRestRep> list, String message) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        for (InitiatorPortMapRestRep ipm : list) {
            InitiatorRestRep initiator = ipm.getInitiator();
            List<NamedRelatedResourceRep> ports = ipm.getStoragePorts();

            String portList = new String(" ");
            List<URI> portURIs = new ArrayList<URI>();
            for (NamedRelatedResourceRep port : ports) {
                StoragePortRestRep p = client.storagePorts().get(port.getId());
                portList += p.getPortName() + " ";
                portURIs.add(port.getId());
            }

            Map<URI, List<URI>> key = new HashMap<URI, List<URI>>();
            key.put(initiator.getId(), portURIs);

            String label = getMessage(message, initiator.getHostName(), initiator.getName(), portList);
            String keyString = EMPTY_STRING;
            try {
                keyString = CatalogSerializationUtils.serializeToString(key);
            } catch (Exception ex) {

            }
            options.add(new AssetOption(keyString, label));
        }

        return options;
    }

    @Asset("exportPathAffectedExports")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath",
            "exportPathStorageSystem" })
    public List<AssetOption> getAffectedExports(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId) {

        return getAffectedExports(ctx, hostOrClusterId, vArrayId, exportId, minPaths, maxPaths, pathsPerInitiator,
                useExisting, storageSystemId, EMPTY_STRING);
    }

    @Asset("exportPathAffectedExports")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportPathExport", "exportPathMinPathsOptions",
            "exportPathMaxPathsOptions", "exportPathPathsPerInitiatorOptions", "exportPathExistingPath",
            "exportPathStorageSystem", "exportPathPorts" })
    public List<AssetOption> getAffectedExports(AssetOptionsContext ctx, URI hostOrClusterId, URI vArrayId, URI exportId,
            Integer minPaths, Integer maxPaths, Integer pathsPerInitiator, String useExisting, URI storageSystemId, String ports) {
        ViPRCoreClient client = api(ctx);
        // the asset option list
        List<AssetOption> options = Lists.newArrayList();

        List<URI> exportPathPorts = parseExportPathPorts(ports);
        ExportPathsAdjustmentPreviewRestRep portPreview = generateExportPathPreview(ctx, hostOrClusterId, vArrayId,
                exportId, minPaths, maxPaths, pathsPerInitiator, useExisting, storageSystemId, exportPathPorts);

        List<NamedRelatedResourceRep> affectedList = portPreview.getAffectedExportGroups();
        List<URI> exportIds = new ArrayList<URI>();

        for (NamedRelatedResourceRep affected : affectedList) {
            exportIds.add(affected.getId());
        }

        List<ExportGroupRestRep> exports = client.blockExports().getByIds(exportIds);

        for (ExportGroupRestRep export : exports) {
            // TODO: need to optimize the way that we retrieve the project name.
            String projectName = client.projects().get(export.getProject().getId()).getName();
            String label = getMessage("exportPathAdjustment.affectedExports", projectName, export.getName());
            options.add(new AssetOption(export.getName(), label));
        }

        return options;
    }

    private ExportPathsAdjustmentPreviewRestRep generateExportPathPreview(AssetOptionsContext ctx, URI hostOrClusterId,
            URI vArrayId, URI exportId, Integer minPaths, Integer maxPaths, Integer pathsPerInitiator,
            String useExisting, URI storageSystemId, List<URI> ports) {
        ViPRCoreClient client = api(ctx);
        ExportPathsAdjustmentPreviewParam param = new ExportPathsAdjustmentPreviewParam();
        ExportPathParameters exportPathParameters = new ExportPathParameters();

        exportPathParameters.setMinPaths(minPaths);
        exportPathParameters.setMaxPaths(maxPaths);
        exportPathParameters.setPathsPerInitiator(pathsPerInitiator);
        exportPathParameters.setStoragePorts(ports);

        param.setUseExistingPaths(useExisting.equalsIgnoreCase(YES_VALUE) ? true : false);

        param.setVirtualArray(vArrayId);
        param.setStorageSystem(storageSystemId);

        param.setExportPathParameters(exportPathParameters);

        return client.blockExports().getExportPathAdjustmentPreview(exportId, param);
    }

    @Asset("exportCurrentPortGroup")
    @AssetDependencies({ "host", "exportPathExport", "exportPathVirtualArray" })
    public List<AssetOption> getCurrentExportPortGroups(AssetOptionsContext ctx, URI hostOrClusterId,
            URI exportId, URI varrayId) {
        final ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        SimpleValueRep value = client.customConfigs().getCustomConfigTypeValue(VMAX_PORT_GROUP_ENABLED, VMAX);
        if (value.getValue().equalsIgnoreCase("true")) {

            ExportGroupRestRep exportGroup = client.blockExports().get(exportId);
            List<StoragePortGroupRestRep> storagePortGroups = new ArrayList<StoragePortGroupRestRep>();
            List<ExportBlockParam> blockParams = exportGroup.getVolumes();
            Set<URI> storageSystemURISet = new HashSet<URI>();
            if (!CollectionUtils.isEmpty(blockParams)) {
                for (ExportBlockParam blockParam : blockParams) {
                    // Get each volume in the export group to get the storage system ID
                    if (blockParam != null) {
                        URI resourceId = blockParam.getId();
                        URI storageDeviceURI = null;
                        if (ResourceType.isType(ResourceType.VOLUME, resourceId)) {
                            VolumeRestRep volume = client.blockVolumes().get(resourceId);
                            storageDeviceURI = volume.getStorageController();
                        } else if (ResourceType.isType(ResourceType.BLOCK_SNAPSHOT, resourceId)) {
                            BlockSnapshotRestRep snapshot = client.blockSnapshots().get(resourceId);
                            storageDeviceURI = snapshot.getStorageController();
                        }
                        if (storageDeviceURI != null) {
                            storageSystemURISet.add(storageDeviceURI);
                        }
                    } else {
                        log.error("Block param not found in export group: {}", exportId);
                    }
                }
            }

            if (!CollectionUtils.isEmpty(storageSystemURISet)) {
                Set<URI> portGroupSet = new HashSet<URI>();
                for (URI storageSystemURI : storageSystemURISet) {
                    // Now use the storage system ID as well to query the storage port groups
                    StoragePortGroupRestRepList portGroups = client.varrays().getStoragePortGroups(varrayId,
                            exportId, storageSystemURI, null, null, false);
                    List<StoragePortGroupRestRep> portGroupList = portGroups.getStoragePortGroups();
                    if (!CollectionUtils.isEmpty(portGroupList)) {
                        for (StoragePortGroupRestRep portGroup : portGroupList) {
                            if (portGroupSet.add(portGroup.getId())) {
                                storagePortGroups.add(portGroup);
                            }
                        }
                    }
                }
            }
            return createPortGroupOptions(storagePortGroups);
        }
        return options;
    }

    @Asset("exportChangePortGroup")
    @AssetDependencies({ "host", "exportPathVirtualArray", "exportCurrentPortGroup" })
    public List<AssetOption> getExportPortGroups(AssetOptionsContext ctx, URI hostOrClusterId, URI varrayId, URI exportCurrentPortGroupId) {
        final ViPRCoreClient client = api(ctx);
        List<StoragePortGroupChangeRep> portGroupsRestRep = client.blockExports().getPortGroupsForPortGroupChange(exportCurrentPortGroupId,
                hostOrClusterId, varrayId);
        return createPortGroupChangeOptions(portGroupsRestRep);
    }

    @Asset("unassignedBlockVolume")
    @AssetDependencies({ "host", "project", "blockStorageType" })
    public List<AssetOption> getBlockVolumes(AssetOptionsContext ctx, URI hostOrClusterId,
            final URI projectId, String blockStorageType) {
        ViPRCoreClient client = api(ctx);
        Set<URI> exportedBlockResources = BlockProvider.getExportedVolumes(client, projectId, hostOrClusterId, null);
        UnexportedBlockResourceFilter<VolumeRestRep> unexportedFilter = new UnexportedBlockResourceFilter<VolumeRestRep>(
                exportedBlockResources);
        SourceTargetVolumesFilter sourceTargetVolumesFilter = new SourceTargetVolumesFilter();
        BlockVolumeBootVolumeFilter bootVolumeFilter = new BlockVolumeBootVolumeFilter();
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(projectId,
                unexportedFilter.and(sourceTargetVolumesFilter).and(bootVolumeFilter.not()));

        // get varray IDs for host/cluster
        List<VirtualArrayRestRep> varrays = new ArrayList<>();
        if (EXCLUSIVE_STORAGE_OPTION.key.equals(blockStorageType) &&
                URIUtil.isType(hostOrClusterId, Host.class)) {
            varrays = client.varrays().findByConnectedHost(hostOrClusterId);
        } else if (SHARED_STORAGE_OPTION.key.equals(blockStorageType) &&
                URIUtil.isType(hostOrClusterId, Cluster.class)) {
            varrays = client.varrays().findByConnectedCluster(hostOrClusterId);
        }
        List<URI> varrayIds = new ArrayList<>();
        for (VirtualArrayRestRep varray : varrays) {
            varrayIds.add(varray.getId());
        }

        // remove volumes not in hosts/cluster's varray
        Iterator<VolumeRestRep> itr = volumes.iterator();
        while (itr.hasNext()) {
            if (!varrayIds.contains(itr.next().getVirtualArray().getId())) {
                itr.remove();
            }
        }

        return createVolumeOptions(client, volumes);
    }

    @Asset("unassignedVplexBlockVolume")
    @AssetDependencies({ "project", "host", "virtualArray" })
    public List<AssetOption> getBlockVolumes(AssetOptionsContext ctx, final URI projectId, URI hostOrClusterId, URI virtualArrayId) {
        // get a list of all 'source' volumes that :
        // - are in this project and not exported to the given host/cluster
        // - are a 'source' volume
        // - are vplex volumes
        ViPRCoreClient client = api(ctx);
        Set<URI> exportedBlockResources = BlockProvider.getExportedVolumes(client, projectId, hostOrClusterId, virtualArrayId);
        UnexportedBlockResourceFilter<VolumeRestRep> unexportedFilter = new UnexportedBlockResourceFilter<VolumeRestRep>(
                exportedBlockResources);
        SourceTargetVolumesFilter sourceTargetVolumesFilter = new SourceTargetVolumesFilter();
        VplexVolumeFilter vplexVolumeFilter = new VplexVolumeFilter();

        CachedResources<BlockVirtualPoolRestRep> blockVpools = new CachedResources<>(client.blockVpools());
        VplexVolumeVirtualPoolFilter virtualPoolFilter = new VplexVolumeVirtualPoolFilter(blockVpools);

        BlockVolumeBootVolumeFilter bootVolumeFilter = new BlockVolumeBootVolumeFilter();

        FilterChain<VolumeRestRep> filter = sourceTargetVolumesFilter.and(unexportedFilter).and(bootVolumeFilter.not())
                .and(vplexVolumeFilter.or(virtualPoolFilter));

        // perform the query and apply the filter
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(projectId, filter);

        // get a list of all volumes from our list that are in the target VArray, or use the target VArray for protection
        List<BlockObjectRestRep> acceptedVolumes = getVPlexVolumesInTargetVArray(client, virtualArrayId, volumes);

        return createVolumeOptions(client, acceptedVolumes);
    }

    @Asset("unassignedBlockSnapshot")
    @AssetDependencies({ "host", "project" })
    public List<AssetOption> getBlockSnapshots(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId) {
        // get a list of all snapshots that are in this project that are not exported to the given host/cluster
        Set<URI> exportedBlockResources = getExportedVolumes(api(ctx), projectId, hostOrClusterId, null);
        UnexportedBlockResourceFilter<BlockSnapshotRestRep> unexportedSnapshotFilter = new UnexportedBlockResourceFilter<BlockSnapshotRestRep>(
                exportedBlockResources);
        return getSnapshotOptionsForProject(ctx, projectId, unexportedSnapshotFilter);
    }

    @Asset("unassignedBlockContinuousCopies")
    @AssetDependencies({ "host", "project", "volumeWithContinuousCopies" })
    public List<AssetOption> getBlockContinuousCopy(AssetOptionsContext ctx, URI hostOrClusterId, URI projectId, URI volume) {
        // get a list of all continuous copies that are in this project that are not exported to the given host/cluster
        Set<URI> exportedBlockResources = getExportedVolumes(api(ctx), projectId, hostOrClusterId, null);
        FilterChain<BlockMirrorRestRep> unexportedCopyFilter = new UnexportedBlockResourceFilter<BlockMirrorRestRep>(exportedBlockResources)
                .and(new DefaultResourceFilter<BlockMirrorRestRep>() {
                    @Override
                    public boolean acceptId(URI id) {
                        return ResourceType.isType(ResourceType.BLOCK_CONTINUOUS_COPY, id);
                    }
                });
        return getContinuousCopyOptionsForProject(ctx, projectId, volume, unexportedCopyFilter);
    }

    @Asset("exportedBlockContinuousCopy")
    @AssetDependencies({ "project", "volumeWithContinuousCopies" })
    public List<AssetOption> getExportedBlockContinuousCopyByVolume(AssetOptionsContext ctx, URI project, URI volume) {
        debug("getting exported blockContinuousCopy (project=%s) (parent=%s)", project, volume);
        final ViPRCoreClient client = api(ctx);
        Set<URI> exportedMirrors = new HashSet<URI>();
        for (ExportGroupRestRep export : client.blockExports().findByProject(project)) {
            for (ExportBlockParam resource : export.getVolumes()) {
                if (ResourceType.isType(ResourceType.BLOCK_CONTINUOUS_COPY, resource.getId())) {
                    exportedMirrors.add(resource.getId());
                }
            }
        }

        ExportedBlockResourceFilter<BlockMirrorRestRep> exportedMirrorFilter = new ExportedBlockResourceFilter<BlockMirrorRestRep>(
                exportedMirrors);

        List<BlockMirrorRestRep> mirrors = client.blockVolumes().getContinuousCopies(volume, exportedMirrorFilter);

        return createVolumeOptions(client, mirrors);
    }

    @Asset("vplexVolumeWithSnapshots")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getVplexSnapshotVolumes(AssetOptionsContext ctx, URI project, String volumeOrConsistencyType) {
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            Set<URI> volIdSet = new HashSet<>();
            List<BlockSnapshotRestRep> snapshots = findSnapshotsByProject(client, project);
            for (BlockSnapshotRestRep s : snapshots) {
                volIdSet.add(s.getParent().getId());
            }
            // Have to get volumes just as it needs vol's mount point which snapshot doesn't have.
            List<VolumeRestRep> volumes = getVolumesByIds(client, volIdSet);
            List<VolumeRestRep> filteredVols = new ArrayList<>();
            for (VolumeRestRep vol : volumes) {
                if (vol.getHaVolumes() != null && !vol.getHaVolumes().isEmpty() && !isInConsistencyGroup(vol)) {
                    filteredVols.add(vol);
                }
            }
            return createVolumeOptions(client, filteredVols);
        } else {
            List<BlockConsistencyGroupRestRep> consistencyGroups = client.blockConsistencyGroups().findByProject(project,
                    new DefaultResourceFilter<BlockConsistencyGroupRestRep>() {
                        @Override
                        public boolean accept(BlockConsistencyGroupRestRep cg) {
                            if (cg.getTypes() != null && cg.getTypes().contains(Types.VPLEX.name())) {
                                return true;
                            } else {
                                return false;
                            }
                        }

                    });
            return createBaseResourceOptions(consistencyGroups);
        }
    }

    private List<VolumeRestRep> getVolumesByIds(ViPRCoreClient client, Set<URI> vols) {
        log.info("Getting volumes: [{}]", vols.size());
        List<VolumeRestRep> volumes = client.blockVolumes().getByIds(vols, null);
        log.info("Got volumens [{}]", volumes.size());
        return volumes;
    }

    private List<BlockSnapshotRestRep> findSnapshotsByProject(ViPRCoreClient client, URI project) {
        log.info("Finding snapshots by project {}", project);
        List<SearchResultResourceRep> snapshotRefs = client.blockSnapshots().performSearchBy(SearchConstants.PROJECT_PARAM, project);
        List<URI> ids = new ArrayList<>();
        for (SearchResultResourceRep ref : snapshotRefs) {
            ids.add(ref.getId());
        }

        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByIds(ids, null);
        log.info("Got snapshots: [{}]", snapshots.size());
        return snapshots;
    }

    @Asset("vplexBlockSnapshot")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType", "vplexVolumeWithSnapshots" })
    public List<AssetOption> getVplexBlockSnapshots(AssetOptionsContext ctx, URI projectId, String type, URI volumeOrCGId) {
        if (isVolumeType(type) && BlockProviderUtils.isType(volumeOrCGId, VOLUME_TYPE)) {
            List<BlockSnapshotRestRep> snapshots = api(ctx).blockSnapshots().getByVolume(volumeOrCGId);
            return constructSnapshotOptions(api(ctx), projectId, snapshots);
        } else if (isConsistencyGroupType(type) && BlockProviderUtils.isType(volumeOrCGId, BLOCK_CONSISTENCY_GROUP_TYPE)) {
            return getConsistencyGroupSnapshots(ctx, volumeOrCGId);
        } else {
            return new ArrayList<AssetOption>();
        }
    }

    public static class UnexportedBlockResourceFilter<T extends BlockObjectRestRep> extends DefaultResourceFilter<T> {

        /** The list of block resources ids that have been exported to this host/cluster */
        private final Set<URI> exportedBlockResources;

        public UnexportedBlockResourceFilter(Set<URI> exportedBlockResources) {
            this.exportedBlockResources = exportedBlockResources;
        }

        @Override
        public boolean acceptId(URI resourceId) {
            // accept this volume if it hasn't been exported to this host/cluster
            return !exportedBlockResources.contains(resourceId);
        }

    }

    public static class ExportedBlockResourceFilter<T extends BlockObjectRestRep> extends DefaultResourceFilter<T> {

        /** The list of block resources ids that have been exported */
        private final Set<URI> exportedBlockResources;

        public ExportedBlockResourceFilter(Set<URI> exportedBlockResources) {
            this.exportedBlockResources = exportedBlockResources;
        }

        @Override
        public boolean acceptId(URI resourceId) {
            // accept this volume if it has been exported
            return exportedBlockResources.contains(resourceId);
        }
    }

    @Asset("blockVirtualPool")
    public List<AssetOption> getBlockVirtualPools(AssetOptionsContext ctx) {
        debug("getting blockVirtualPools");
        return createBaseResourceOptions(api(ctx).blockVpools().getByTenant(ctx.getTenant()));
    }

    @Asset("blockVirtualPoolFilter")
    public List<AssetOption> getBlockVirtualPoolFilters(AssetOptionsContext ctx) {
        List<AssetOption> options = createBaseResourceOptions(api(ctx).blockVpools().getAll());
        options.add(0, new AssetOption("All", "All"));
        return options;
    }

    /**
     * Returns the virtual pools for a given virtualArray (initially added for the Create Volume service)
     * 
     * @param ctx
     * @param virtualArray
     * @return
     */
    @Asset("blockVirtualPool")
    @AssetDependencies({ "virtualArray" })
    public List<AssetOption> getVirtualPoolsForVirtualArray(AssetOptionsContext ctx, URI virtualArray) {
        debug("getting virtualPoolsForVirtualArray(virtualArray=%s)", virtualArray);
        List<BlockVirtualPoolRestRep> virtualPools = api(ctx).blockVpools().getByVirtualArrayAndTenant(virtualArray, ctx.getTenant());
        return createVirtualPoolResourceOptions(virtualPools);
    }

    @Asset("blockVirtualPool")
    @AssetDependencies({ "blockVirtualArray" })
    public List<AssetOption> getVirtualPoolsForBlockVirtualArray(AssetOptionsContext ctx, URI virtualArray) {
        debug("getting getVirtualPoolsForBlockVirtualArray(virtualArray=%s)", virtualArray);
        return getVirtualPoolsForVirtualArray(ctx, virtualArray);
    }

    @Asset("blockVirtualPool")
    @AssetDependencies({ "virtualArrayByConsistencyGroup" })
    public List<AssetOption> getVirtualPoolsForVirtualArrayByCG(AssetOptionsContext ctx, URI virtualArray) {
        return getVirtualPoolsForVirtualArray(ctx, virtualArray);
    }

    @Asset("blockVolume")
    @AssetDependencies("project")
    public List<AssetOption> getVolumes(AssetOptionsContext ctx, URI project) {
        debug("getting volumes (project=%s)", project);
        ViPRCoreClient client = api(ctx);
        return createVolumeOptions(client, listVolumes(client, project));
    }
    
    @Asset("sourceBlockSnapshot")
    @AssetDependencies("project")
    public List<AssetOption> getSnapshotsToExpand(AssetOptionsContext ctx, URI project) {
        debug("getting Snapshots (project=%s)", project);
        return getSnapshotOptionsForProject(ctx, project);
    }

    @Asset("blockVolumeByType")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getVolumesByType(AssetOptionsContext ctx, URI project, String type) {
        debug("getting volumes (project=%s)", project);
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(type)) {
            return createVolumeOptions(client, listVolumesWithoutConsistencyGroup(client, project));
        } else {
            List<BlockConsistencyGroupRestRep> consistencyGroups = api(ctx).blockConsistencyGroups()
                    .search()
                    .byProject(project)
                    .run();
            return createBaseResourceOptions(consistencyGroups);
        }
    }

    @Asset("protectedBlockVolume")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getProtectedVolumes(AssetOptionsContext ctx, URI project, String volumeOrConsistencyType) {
        ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            debug("getting protected volumes (project=%s)", project);
            // Allow recoverpoint or SRDF sources
            ResourceFilter<VolumeRestRep> filter = RecoverPointPersonalityFilter.SOURCE.or(new SRDFSourceFilter());
            List<VolumeRestRep> volumes = client.blockVolumes().findByProject(project, filter);
            // We need to filter out SRDF Metro volumes as they are not eligible for FAILOVER or SWAP
            List<VolumeRestRep> filteredVols = new ArrayList<>();
            for (VolumeRestRep currentVolume : volumes) {
                ProtectionRestRep protection = currentVolume.getProtection();
                if (protection != null && protection.getSrdfRep() != null && protection.getSrdfRep().getSRDFTargetVolumes() != null
                        && !protection.getSrdfRep().getSRDFTargetVolumes().isEmpty()) {
                    for (VolumeRestRep srdfTarget : client.blockVolumes().getByRefs(protection.getSrdfRep().getSRDFTargetVolumes(),
                            new SRDFTargetFilter())) {
                        SRDFRestRep srdf = (srdfTarget.getProtection() != null) ? srdfTarget.getProtection().getSrdfRep() : null;
                        if (srdf != null && (srdf.getAssociatedSourceVolume() != null) &&
                                (srdf.getSrdfCopyMode() != null) && (!ACTIVE.equalsIgnoreCase(srdf.getSrdfCopyMode()))) {
                            filteredVols.add(currentVolume);
                            break;
                        }
                    }
                } else { // Add the volume as before
                    filteredVols.add(currentVolume);
                }
            }
            return createVolumeOptions(client, filteredVols);
        } else {
            debug("getting protected consistency groups (project=%s)", project);
            // Allow recoverpoint or SRDF sources
            ResourceFilter<BlockConsistencyGroupRestRep> filter = new ConsistencyGroupFilter(BlockConsistencyGroup.Types.RP.name(),
                    false).or(new ConsistencyGroupFilter(BlockConsistencyGroup.Types.SRDF.name(),
                            false));
            List<BlockConsistencyGroupRestRep> consistencyGroups = client.blockConsistencyGroups()
                    .search()
                    .byProject(project)
                    .filter(filter)
                    .run();
            // We need to filter out SRDF Metro volumes as they are not eligible for FAILOVER or SWAP
            List<BlockConsistencyGroupRestRep> filteredCgs = new ArrayList<>();
            for (BlockConsistencyGroupRestRep cg : consistencyGroups) {
                // Get SRDF source volumes
                if (cg.getTypes().contains(BlockConsistencyGroup.Types.SRDF.name())) {
                    List<VolumeRestRep> srcVolumes = client.blockVolumes().getByRefs(cg.getVolumes(), new SRDFSourceFilter());
                    if (srcVolumes != null && !srcVolumes.isEmpty()) {
                        // Get the first source volume and obtain its target references
                        VolumeRestRep srcVolume = srcVolumes.get(0);
                        for (VolumeRestRep srdfTarget : client.blockVolumes().getByRefs(
                                srcVolume.getProtection().getSrdfRep().getSRDFTargetVolumes(),
                                new SRDFTargetFilter())) {
                            SRDFRestRep srdf = (srdfTarget.getProtection() != null) ? srdfTarget.getProtection().getSrdfRep() : null;
                            if (srdf != null && (srdf.getAssociatedSourceVolume() != null) &&
                                    (srdf.getSrdfCopyMode() != null) && (!ACTIVE.equalsIgnoreCase(srdf.getSrdfCopyMode()))) {
                                filteredCgs.add(cg);
                                break;
                            }
                        }
                    }
                } else { // Add the cg as before
                    filteredCgs.add(cg);
                }
            }
            return createBaseResourceOptions(filteredCgs);
        }
    }

    @Asset("failoverTarget")
    @AssetDependencies("protectedBlockVolume")
    public List<AssetOption> getFailoverTarget(AssetOptionsContext ctx, URI protectedBlockVolume) {
        if (protectedBlockVolume != null) {
            ViPRCoreClient client = api(ctx);

            if (BlockProviderUtils.isType(protectedBlockVolume, VOLUME_TYPE)) {
                debug("getting failoverTargets (protectedBlockVolume=%s)", protectedBlockVolume);
                VolumeRestRep volume = client.blockVolumes().get(protectedBlockVolume);

                ProtectionRestRep protection = volume.getProtection();
                if (protection != null) {
                    // RecoverPoint protection
                    if (protection.getRpRep() != null && protection.getRpRep().getProtectionSet() != null) {
                        return getRpFailoverTargets(client, volume);
                    }
                    // VMAX SRDF protection
                    if (protection.getSrdfRep() != null && protection.getSrdfRep().getSRDFTargetVolumes() != null
                            && !protection.getSrdfRep().getSRDFTargetVolumes().isEmpty()) {
                        return getSrdfFailoverTargets(client, volume);
                    }
                }
            } else if (BlockProviderUtils.isType(protectedBlockVolume, BLOCK_CONSISTENCY_GROUP_TYPE)) {
                debug("getting failoverTargets for consistency group %s", protectedBlockVolume);
                BlockConsistencyGroupRestRep cg = client.blockConsistencyGroups().get(protectedBlockVolume);

                List<VolumeRestRep> srcVolumes = null;
                // Get RP source volumes
                if (cg.getTypes().contains(BlockConsistencyGroup.Types.RP.name())) {
                    srcVolumes = client.blockVolumes().getByRefs(cg.getVolumes(), RecoverPointPersonalityFilter.SOURCE);
                }
                // Get SRDF source volumes
                if (cg.getTypes().contains(BlockConsistencyGroup.Types.SRDF.name())) {
                    srcVolumes = client.blockVolumes().getByRefs(cg.getVolumes(), new SRDFSourceFilter());
                }

                if (srcVolumes != null && !srcVolumes.isEmpty()) {
                    // Get the first source volume and obtain its target references
                    VolumeRestRep srcVolume = srcVolumes.get(0);

                    if (cg.getTypes() != null) {
                        Map<String, String> targetVolumes = Maps.newLinkedHashMap();
                        CachedResources<VirtualArrayRestRep> virtualArrays = new CachedResources<VirtualArrayRestRep>(client.varrays());
                        List<VirtualArrayRelatedResourceRep> targets = new ArrayList<VirtualArrayRelatedResourceRep>();

                        // Process the RP targets
                        if (cg.getTypes().contains(BlockConsistencyGroup.Types.RP.name())) {
                            targets = srcVolume.getProtection().getRpRep().getRpTargets();
                        }
                        // Process the SRDF targets
                        if (cg.getTypes().contains(BlockConsistencyGroup.Types.SRDF.name())) {
                            targets = srcVolume.getProtection().getSrdfRep().getSRDFTargetVolumes();
                        }

                        for (VolumeRestRep targetVolume : client.blockVolumes().getByRefs(targets)) {
                            VirtualArrayRestRep virtualArray = virtualArrays.get(targetVolume.getVirtualArray());
                            String label = getMessage(name(virtualArray));
                            targetVolumes.put(stringId(virtualArray), label);
                        }

                        List<AssetOption> options = Lists.newArrayList();
                        for (Map.Entry<String, String> entry : targetVolumes.entrySet()) {
                            options.add(new AssetOption(entry.getKey(), entry.getValue()));
                        }
                        AssetOptionsUtils.sortOptionsByLabel(options);
                        return options;
                    }
                }
            }
        }

        return Lists.newArrayList();
    }

    protected List<AssetOption> getRpFailoverTargets(ViPRCoreClient client, VolumeRestRep volume) {
        Map<String, String> targetVolumes = Maps.newLinkedHashMap();
        URI protectionSetId = volume.getProtection().getRpRep().getProtectionSet().getId();
        ProtectionSetRestRep localProtectionSet = client.blockVolumes().getProtectionSet(volume.getId(),
                protectionSetId);
        String sourceSiteName = volume.getProtection().getRpRep().getInternalSiteName();
        CachedResources<VirtualArrayRestRep> virtualArrays = new CachedResources<VirtualArrayRestRep>(client.varrays());

        List<RelatedResourceRep> rpTargets = localProtectionSet.getVolumes();
        for (VolumeRestRep protectionSetVolume : client.blockVolumes().getByRefs(rpTargets, RecoverPointPersonalityFilter.TARGET)) {
            String targetSiteName = protectionSetVolume.getProtection().getRpRep().getInternalSiteName();
            boolean isLocal = StringUtils.equals(sourceSiteName, targetSiteName);
            String rpType = isLocal ? "local" : "remote";
            VirtualArrayRestRep virtualArray = virtualArrays.get(protectionSetVolume.getVirtualArray());
            String label = getMessage("recoverpoint.target", name(protectionSetVolume), rpType, name(virtualArray));
            targetVolumes.put(stringId(protectionSetVolume), label);
        }

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<String, String> entry : targetVolumes.entrySet()) {
            options.add(new AssetOption(entry.getKey(), entry.getValue()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> getSrdfFailoverTargets(ViPRCoreClient client, VolumeRestRep volume) {
        Map<String, String> targetVolumes = Maps.newLinkedHashMap();
        CachedResources<VirtualArrayRestRep> virtualArrays = new CachedResources<VirtualArrayRestRep>(client.varrays());

        List<VirtualArrayRelatedResourceRep> srdfTargets = volume.getProtection().getSrdfRep().getSRDFTargetVolumes();
        for (VolumeRestRep protectionSetVolume : client.blockVolumes().getByRefs(srdfTargets, new SRDFTargetFilter())) {
            VirtualArrayRestRep virtualArray = virtualArrays.get(protectionSetVolume.getVirtualArray());
            String label = getMessage("srdf.target", name(protectionSetVolume), name(virtualArray));
            targetVolumes.put(stringId(protectionSetVolume), label);
        }

        List<AssetOption> options = Lists.newArrayList();
        for (Map.Entry<String, String> entry : targetVolumes.entrySet()) {
            options.add(new AssetOption(entry.getKey(), entry.getValue()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("blockSnapshot")
    @AssetDependencies({ "project" })
    public List<AssetOption> getBlockSnapshotsByVolume(AssetOptionsContext ctx, URI project) {
        debug("getting blockSnapshots (project=%s)", project);
        return getSnapshotOptionsForProject(ctx, project);
    }

    @Asset("exportedBlockSnapshot")
    @AssetDependencies({ "project" })
    public List<AssetOption> getExportedBlockSnapshotsByVolume(AssetOptionsContext ctx, URI project) {
        debug("getting exported blockSnapshots (project=%s)", project);
        final ViPRCoreClient client = api(ctx);
        List<URI> snapshotIds = Lists.newArrayList();
        for (ExportGroupRestRep export : client.blockExports().findByProject(project)) {
            for (ExportBlockParam resource : export.getVolumes()) {
                if (ResourceType.isType(ResourceType.BLOCK_SNAPSHOT, resource.getId())) {
                    snapshotIds.add(resource.getId());
                }
            }
        }
        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByIds(snapshotIds);
        return createVolumeWithVarrayOptions(client, snapshots);
    }

    private List<AssetOption> getVolumeSnapshotOptionsForProject(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<BlockSnapshotRestRep> snapshots = findSnapshotsByProject(client, project);
        List<BlockSnapshotRestRep> filteredSnap = new ArrayList<>();
        for (BlockSnapshotRestRep snapshot : snapshots) {
            if (!isSnapshotRPBookmark(snapshot)) {
                filteredSnap.add(snapshot);
            }
        }

        return constructSnapshotOptions(client, project, filteredSnap);
    }

    private List<AssetOption> getVolumeRPSnapshotOptionsForProject(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<BlockSnapshotRestRep> snapshots = findSnapshotsByProject(client, project);
        List<BlockSnapshotRestRep> filteredSnap = new ArrayList<>();
        for (BlockSnapshotRestRep snapshot : snapshots) {
            if (isSnapshotRPBookmark(snapshot)) {
                filteredSnap.add(snapshot);
            }
        }

        return constructSnapshotOptions(client, project, filteredSnap);
    }

    /**
     * get single volume snapshot sessions for a project
     * 
     * @param ctx
     * @param project
     * @return
     */
    private List<AssetOption> getVolumeSnapshotSessionOptionsForProject(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<BlockSnapshotSessionRestRep> snapshotSessions = client.blockSnapshotSessions().findByProject(project);
        List<BlockSnapshotSessionRestRep> singleVolumeSnapshotSessions = new ArrayList<BlockSnapshotSessionRestRep>();
        for (BlockSnapshotSessionRestRep snapshotSession : snapshotSessions) {
            if (snapshotSession.getReplicationGroupInstance() == null) {
                singleVolumeSnapshotSessions.add(snapshotSession);
            }
        }
        return constructSnapshotSessionOptions(client, project, singleVolumeSnapshotSessions);
    }

    @Asset("blockSnapshotOrConsistencyGroup")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType", "blockSnapshotType", "consistencyGroupByProjectAndType" })
    public List<AssetOption> getBlockSnapshotsByVolume(AssetOptionsContext ctx, URI project, String storageType, String snapshotType,
            URI consistencyGroupId) {
        if (NONE_TYPE.equals(storageType)) {
            return new ArrayList<AssetOption>();
        } else {
            if (CG_SNAPSHOT_TYPE_VALUE.equals(snapshotType)
                    || CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(snapshotType)) {
                if (consistencyGroupId == null) {
                    error("Consistency type invalid : %s", consistencyGroupId);
                    return new ArrayList<AssetOption>();
                }
                if (!BlockProviderUtils.isType(consistencyGroupId, BLOCK_CONSISTENCY_GROUP_TYPE)) {
                    error("Consistency Group field is required for Storage Type [%s, %s]", storageType, consistencyGroupId);
                    return new ArrayList<AssetOption>();
                }
                if (CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(snapshotType)) {
                    return getConsistencyGroupSnapshotSessions(ctx, consistencyGroupId);
                } else {
                    return getConsistencyGroupSnapshots(ctx, consistencyGroupId);
                }
            } else if (SNAPSHOT_SESSION_TYPE_VALUE.equals(snapshotType)) {
                info("getting blockSnapshotSessions (project=%s)", project);
                return getVolumeSnapshotSessionOptionsForProject(ctx, project);
            } else if (RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_VALUE.equals(snapshotType)) {
                info("getting rpBookmarks (project=%s)", project);
                return getVolumeRPSnapshotOptionsForProject(ctx, project);
            } else {
                info("getting blockSnapshots (project=%s)", project);
                return getVolumeSnapshotOptionsForProject(ctx, project);
            }
        }
    }

    @Asset("snapshotAvailableForResynchronize")
    @AssetDependencies({ "blockVolumeWithSnapshot", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getSnapshotSynchronize(AssetOptionsContext ctx, URI volumeId, String volumeOrConsistencyType) {
        if (!checkTypeConsistency(volumeId, volumeOrConsistencyType)) {
            warn("Inconsistent types, %s and %s, return empty results", volumeId, volumeOrConsistencyType);
            return new ArrayList<AssetOption>();
        }
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByVolume(volumeId,
                    new DefaultResourceFilter<BlockSnapshotRestRep>() {

                        @Override
                        public boolean accept(BlockSnapshotRestRep snapshot) {
                            String replicaState = snapshot.getReplicaState();
                            return replicaState != null &&
                                    !(replicaState.equals(ReplicationState.DETACHED.name())) &&
                                    !(replicaState.equals(ReplicationState.INACTIVE.name()));
                        }
                    });

            return constructSnapshotOptions(snapshots);
        } else {
            return getConsistencyGroupSnapshots(ctx, volumeId);
        }
    }
    
    @Asset("snapshotAvailableForExpand")
    @AssetDependencies({ "blockVolumeWithSnapshot", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getSnapshotExpand(AssetOptionsContext ctx, URI volumeId, String volumeOrConsistencyType) {
        if (!checkTypeConsistency(volumeId, volumeOrConsistencyType)) {
            warn("Inconsistent types, %s and %s, return empty results", volumeId, volumeOrConsistencyType);
            return new ArrayList<AssetOption>();
        }
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().getByVolume(volumeId);
            return constructSnapshotOptions(snapshots);
        } else {
            return getConsistencyGroupSnapshots(ctx, volumeId);
        }
    }

    @Asset("consistencyGroupByProjectAndType")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getAllConsistencyGroups(final AssetOptionsContext ctx, URI projectId, String type) {

        if (isConsistencyGroupType(type)) {
            List<BlockConsistencyGroupRestRep> consistencyGroups = api(ctx).blockConsistencyGroups()
                    .search()
                    .byProject(projectId)
                    .run();
            return createBaseResourceOptions(consistencyGroups);
        } else {
            return Lists.newArrayList(newAssetOption(NONE_TYPE, "None"));
        }
    }

    @Asset("applicationSnapshotType")
    public List<AssetOption> getApplicationSnapshotTypes(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        options.add(SNAPSHOT_TARGET_TYPE_OPTION);
        options.add(SNAPSHOT_SESSION_TYPE_OPTION);
        return options;
    }

    @Asset("applicationSnapshotTargetType")
    public List<AssetOption> getApplicationSnapshotTargetType(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        options.add(SNAPSHOT_TARGET_TYPE_OPTION);
        return options;
    }

    @Asset("applicationSnapshotSessionCopySets")
    @AssetDependencies({ "application" })
    public List<AssetOption> getApplicationSnapshotSessionCopySets(AssetOptionsContext ctx, URI application) {
        return createOptions(api(ctx).application().getVolumeGroupSnapsetSessionSets(application).getCopySets().toArray());
    }

    @Asset("applicationSnapshotCopySets")
    @AssetDependencies({ "application" })
    public List<AssetOption> getApplicationSnapshotCopySets(AssetOptionsContext ctx, URI application) {
        return createOptions(api(ctx).application().getVolumeGroupSnapshotSets(application).getCopySets().toArray());
    }

    private Set<String> getApplicationSnapshotCopySetsForRestore(AssetOptionsContext ctx, URI application) {
        Set<String> restoreCopySets = new HashSet<String>();

        Set<String> copySetNames = api(ctx).application().getVolumeGroupSnapshotSets(application).getCopySets();

        boolean isRP = false;
        NamedVolumesList volsInApp = api(ctx).application().getVolumeByApplication(application);
        if (volsInApp != null && volsInApp.getVolumes() != null && !volsInApp.getVolumes().isEmpty()) {
            VolumeRestRep firstVol = api(ctx).blockVolumes().get(volsInApp.getVolumes().get(0).getId());
            isRP = BlockStorageUtils.isRPVolume(firstVol);
        }

        if (isRP) {
            for (String copySetName : copySetNames) {
                VolumeGroupCopySetParam input = new VolumeGroupCopySetParam();
                input.setCopySetName(copySetName);

                SnapshotList snapshots = api(ctx).application().getVolumeGroupSnapshotsForSet(application, input);
                if (snapshots != null && snapshots.getSnapList() != null && !snapshots.getSnapList().isEmpty()) {
                    BlockSnapshotRestRep snapRep = api(ctx).blockSnapshots().get(snapshots.getSnapList().get(0));
                    if (snapRep != null) {
                        VolumeRestRep parentVol = api(ctx).blockVolumes().get(snapRep.getParent());
                        if (BlockStorageUtils.isRPSourceVolume(parentVol)) {
                            restoreCopySets.add(copySetName);
                        }
                    }
                }
            }
        } else {
            restoreCopySets.addAll(copySetNames);
        }

        return restoreCopySets;
    }

    private Set<String> getApplicationSnapshotSessionCopySetsForRestore(AssetOptionsContext ctx, URI application) {
        Set<String> restoreCopySets = new HashSet<String>();

        Set<String> copySetNames = api(ctx).application().getVolumeGroupSnapsetSessionSets(application).getCopySets();

        boolean isRP = false;
        NamedVolumesList volsInApp = api(ctx).application().getVolumeByApplication(application);
        if (volsInApp != null && volsInApp.getVolumes() != null && !volsInApp.getVolumes().isEmpty()) {
            VolumeRestRep firstVol = api(ctx).blockVolumes().get(volsInApp.getVolumes().get(0).getId());
            isRP = BlockStorageUtils.isRPVolume(firstVol);
        }

        if (isRP) {
            List<VolumeRestRep> applicationVolumes = api(ctx).blockVolumes().getByRefs(
                    api(ctx).application().getVolumeByApplication(application).getVolumes());
            Set<String> sourceRepGrpNames = new HashSet<String>();
            for (VolumeRestRep volume : applicationVolumes) {
                if (volume.getReplicationGroupInstance() != null && BlockStorageUtils.isRPSourceVolume(volume)) {
                    sourceRepGrpNames.add(volume.getReplicationGroupInstance());
                }
            }
            for (String copySetName : copySetNames) {
                VolumeGroupCopySetParam input = new VolumeGroupCopySetParam();
                input.setCopySetName(copySetName);

                BlockSnapshotSessionList sessions = api(ctx).application().getVolumeGroupSnapshotSessionsByCopySet(application, input);
                if (sessions != null && sessions.getSnapSessionRelatedResourceList() != null
                        && !sessions.getSnapSessionRelatedResourceList().isEmpty()) {
                    BlockSnapshotSessionRestRep sessionRep = api(ctx).blockSnapshotSessions()
                            .get(sessions.getSnapSessionRelatedResourceList().get(0));
                    if (sessionRep != null && sessionRep.getReplicationGroupInstance() != null
                            && sourceRepGrpNames.contains(sessionRep.getReplicationGroupInstance())) {
                        restoreCopySets.add(copySetName);
                    }
                }
            }
        } else {
            restoreCopySets.addAll(copySetNames);
        }

        return restoreCopySets;
    }

    @Asset("applicationRestoreCopySets")
    @AssetDependencies({ "application", "applicationSnapshotType" })
    public List<AssetOption> getApplicationRestoreCopySets(AssetOptionsContext ctx, URI application, String snapshotType) {
        if (snapshotType.equalsIgnoreCase(SNAPSHOT_SESSION_TYPE_VALUE)) {
            return createOptions(getApplicationSnapshotSessionCopySetsForRestore(ctx, application).toArray());
        } else if (snapshotType.equalsIgnoreCase(SNAPSHOT_TARGET_TYPE_VALUE)) {
            return createOptions(getApplicationSnapshotCopySetsForRestore(ctx, application).toArray());
        }
        return Lists.newArrayList();
    }

    @Asset("applicationCopySets")
    @AssetDependencies({ "application", "applicationSnapshotType" })
    public List<AssetOption> getApplicationCopySets(AssetOptionsContext ctx, URI application, String snapshotType) {
        if (snapshotType.equalsIgnoreCase(SNAPSHOT_SESSION_TYPE_VALUE)) {
            return createOptions(api(ctx).application().getVolumeGroupSnapsetSessionSets(application).getCopySets().toArray());
        } else if (snapshotType.equalsIgnoreCase(SNAPSHOT_TARGET_TYPE_VALUE)) {
            return createOptions(api(ctx).application().getVolumeGroupSnapshotSets(application).getCopySets().toArray());
        }
        return Lists.newArrayList();
    }

    protected Set<String> getReplicationGroupsForApplicationFullCopy(ViPRCoreClient client, URI applicationId, String copySet) {
        Set<String> options = Sets.newHashSet();
        VolumeGroupCopySetParam input = new VolumeGroupCopySetParam();
        input.setCopySetName(copySet);

        NamedVolumesList fullCopies = client.application().getVolumeGroupFullCopiesForSet(applicationId, input);
        for (NamedRelatedResourceRep fullCopy : fullCopies.getVolumes()) {
            VolumeRestRep fullCopyRep = client.blockVolumes().get(fullCopy);
            if (fullCopyRep != null && fullCopyRep.getReplicationGroupInstance() != null) {
                options.add(fullCopyRep.getReplicationGroupInstance());
            }
        }
        return options;
    }

    protected Set<String> getReplicationGroupsForApplicationSnapshotSession(ViPRCoreClient client, URI applicationId, String copySet) {
        Set<String> options = Sets.newHashSet();
        VolumeGroupCopySetParam input = new VolumeGroupCopySetParam();
        input.setCopySetName(copySet);

        BlockSnapshotSessionList sessions = client.application().getVolumeGroupSnapshotSessionsByCopySet(applicationId, input);
        for (NamedRelatedResourceRep session : sessions.getSnapSessionRelatedResourceList()) {
            BlockSnapshotSessionRestRep sessionRep = client.blockSnapshotSessions().get(session);
            if (sessionRep != null && sessionRep.getReplicationGroupInstance() != null) {
                options.add(sessionRep.getReplicationGroupInstance());
            }
        }
        return BlockStorageUtils.stripRPTargetFromReplicationGroup(options);
    }

    protected Set<String> getReplicationGroupsForApplicationSnapshot(ViPRCoreClient client, URI applicationId, String copySet) {
        Set<String> options = Sets.newHashSet();
        VolumeGroupCopySetParam input = new VolumeGroupCopySetParam();
        input.setCopySetName(copySet);
        SnapshotList sessions = client.application().getVolumeGroupSnapshotsForSet(applicationId, input);
        for (NamedRelatedResourceRep snap : sessions.getSnapList()) {
            BlockSnapshotRestRep snapRep = client.blockSnapshots().get(snap);
            // TODO get replication group from parent. should the snapshot already contain this?
            VolumeRestRep parentVolume = client.blockVolumes().get(snapRep.getParent());
            if (parentVolume != null && parentVolume.getReplicationGroupInstance() != null) {
                options.add(parentVolume.getReplicationGroupInstance());
            }
        }
        return BlockStorageUtils.stripRPTargetFromReplicationGroup(options);
    }

    @Asset("replicationGroup")
    @AssetDependencies({ "application", "applicationVirtualArray" })
    public List<AssetOption> getApplicationFullCopyReplicationGroups(AssetOptionsContext ctx, URI applicationId,
            String virtualArrayParameter) {
        return getApplicationReplicationGroups(ctx, applicationId, virtualArrayParameter);
    }

    @Asset("replicationGroup")
    @AssetDependencies({ "application", "applicationSnapshotVirtualArray" })
    public List<AssetOption> getApplicationReplicationGroups(AssetOptionsContext ctx, URI applicationId, String virtualArrayParameter) {
        ViPRCoreClient client = api(ctx);
        boolean isTarget = false;
        URI virtualArray = null;

        if (virtualArrayParameter != null && StringUtils.split(virtualArrayParameter, ':')[0].equals("tgt")) {
            virtualArray = URI.create(StringUtils.substringAfter(virtualArrayParameter, ":"));
            isTarget = true;
        } else {
            isTarget = false;
        }

        Set<String> subGroups = Sets.newHashSet();
        NamedVolumesList applicationVolumes = client.application().getVolumeByApplication(applicationId);
        for (NamedRelatedResourceRep volumeId : applicationVolumes.getVolumes()) {
            VolumeRestRep volume = client.blockVolumes().get(volumeId);
            VolumeRestRep parentVolume = volume;
            if (volume.getHaVolumes() != null && !volume.getHaVolumes().isEmpty()) {
                volume = BlockStorageUtils.getVPlexSourceVolume(client, volume);
            }
            if (volume != null && volume.getReplicationGroupInstance() != null) {
                if (isTarget) {
                    if (volume.getVirtualArray().getId().equals(virtualArray)) {
                        subGroups.add(volume.getReplicationGroupInstance());
                    }
                } else {
                    if (!BlockStorageUtils.isRPVolume(parentVolume) || BlockStorageUtils.isRPSourceVolume(parentVolume)) {
                        subGroups.add(volume.getReplicationGroupInstance());
                    }
                }
            }
        }

        return createStringOptions(BlockStorageUtils.stripRPTargetFromReplicationGroup(subGroups));
    }

    @Asset("replicationGroup")
    @AssetDependencies({ "application", "fullCopyName" })
    public List<AssetOption> getApplicationReplicationGroupsForFullCopy(AssetOptionsContext ctx, URI applicationId,
            String copySet) {
        final ViPRCoreClient client = api(ctx);
        return createStringOptions(getReplicationGroupsForApplicationFullCopy(client, applicationId, copySet));
    }

    @Asset("replicationGroup")
    @AssetDependencies({ "application", "applicationSnapshotSessionCopySets" })
    public List<AssetOption> getApplicationReplicationGroupsForSnapshotSession(AssetOptionsContext ctx, URI applicationId,
            String copySet) {
        final ViPRCoreClient client = api(ctx);
        return createStringOptions(getReplicationGroupsForApplicationSnapshotSession(client, applicationId, copySet));
    }

    @Asset("replicationGroup")
    @AssetDependencies({ "application", "applicationSnapshotCopySets" })
    public List<AssetOption> getApplicationReplicationGroupsForSnapshot(AssetOptionsContext ctx, URI applicationId,
            String copySet) {
        final ViPRCoreClient client = api(ctx);
        return createStringOptions(getReplicationGroupsForApplicationSnapshot(client, applicationId, copySet));
    }

    @Asset("replicationGroup")
    @AssetDependencies({ "application", "applicationSnapshotType", "applicationCopySets" })
    public List<AssetOption> getApplicationReplicationGroups(AssetOptionsContext ctx, URI applicationId, String snapshotType,
            String copySet) {
        final ViPRCoreClient client = api(ctx);
        if (snapshotType.equalsIgnoreCase(SNAPSHOT_SESSION_TYPE_VALUE)) {
            return createStringOptions(getReplicationGroupsForApplicationSnapshotSession(client, applicationId, copySet));
        } else if (snapshotType.equalsIgnoreCase(SNAPSHOT_TARGET_TYPE_VALUE)) {
            return createStringOptions(getReplicationGroupsForApplicationSnapshot(client, applicationId, copySet));
        }
        return Lists.newArrayList();
    }

    @Asset("replicationGroup")
    @AssetDependencies({ "application", "applicationSnapshotType", "applicationRestoreCopySets" })
    public List<AssetOption> getApplicationRestoreReplicationGroups(AssetOptionsContext ctx, URI applicationId, String snapshotType,
            String copySet) {
        final ViPRCoreClient client = api(ctx);
        if (snapshotType.equalsIgnoreCase(SNAPSHOT_SESSION_TYPE_VALUE)) {
            return createStringOptions(getReplicationGroupsForApplicationSnapshotSession(client, applicationId, copySet));
        } else if (snapshotType.equalsIgnoreCase(SNAPSHOT_TARGET_TYPE_VALUE)) {
            return createStringOptions(getReplicationGroupsForApplicationSnapshot(client, applicationId, copySet));
        }
        return Lists.newArrayList();
    }

    @Asset("blockSnapshotType")
    @AssetDependencies({ "blockVolumeOrConsistencyType", "snapshotBlockVolume" })
    public List<AssetOption> getBlockSnapshotType(AssetOptionsContext ctx, String storageType, URI blockVolumeOrCG) {
        // These are hard coded values for now. In the future, this may be available through an API
        List<AssetOption> options = Lists.newArrayList();
        if (isConsistencyGroupType(blockVolumeOrCG)) {
            options.add(CG_SNAPSHOT_TYPE_OPTION);
            // Only add cg session option if the CG selected supports it
            ViPRCoreClient client = api(ctx);
            BlockConsistencyGroupRestRep cg = client.blockConsistencyGroups().get(blockVolumeOrCG);
            if (isSnapshotSessionSupportedForCG(cg)) {
                options.add(CG_SNAPSHOT_SESSION_TYPE_OPTION);
            }
        } else {
            debug("getting blockSnapshotTypes (blockVolume=%s)", blockVolumeOrCG);
            ViPRCoreClient client = api(ctx);
            VolumeRestRep volume = client.blockVolumes().get(blockVolumeOrCG);
            BlockVirtualPoolRestRep virtualPool = client.blockVpools().get(volume.getVirtualPool());
            if (isLocalSnapshotSupported(virtualPool)) {
                options.add(LOCAL_ARRAY_SNAPSHOT_TYPE_OPTION);
            }

            if (isRPSourceVolume(volume)) {
                options.add(RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_OPTION);
            }

            if (isSnapshotSessionSupportedForVolume(volume)) {
                options.add(SNAPSHOT_SESSION_TYPE_OPTION);
            }
        }
        return options;
    }

    @Asset("blockSnapshotType")
    @AssetDependencies("blockVolumeOrConsistencyType")
    public List<AssetOption> getBlockSnapshotType(AssetOptionsContext ctx, String storageType) {
        // These are hard coded values for now. In the future, this may be available through an API
        List<AssetOption> options = Lists.newArrayList();

        if (isConsistencyGroupType(storageType)) {
            options.add(CG_SNAPSHOT_TYPE_OPTION);
            options.add(CG_SNAPSHOT_SESSION_TYPE_OPTION);
        } else {
            options.add(LOCAL_ARRAY_SNAPSHOT_TYPE_OPTION);
            options.add(RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_OPTION);
            options.add(SNAPSHOT_SESSION_TYPE_OPTION);
        }
        return options;
    }

    @Asset("linkedSnapshotsForApplicationSnapshotSessionLinkService")
    @AssetDependencies({ "application", "applicationSnapshotSessionCopySets" })
    public List<AssetOption> getLinkedSnapshotsForApplicationSnapshotSessionVolumeNew(AssetOptionsContext ctx, URI application,
            String selectedCopySet) {
        ViPRCoreClient client = api(ctx);
        List<BlockSnapshotRestRep> snapshots = new ArrayList<BlockSnapshotRestRep>();

        Set<String> replicationGroups = getReplicationGroupsForApplicationSnapshotSession(client, application, selectedCopySet);
        Set<String> copySets = client.application().getVolumeGroupSnapsetSessionSets(application).getCopySets();
        List<BlockSnapshotSessionRestRep> snapshotSessions = Lists.newArrayList();

        for (String copySet : copySets) {
            VolumeGroupCopySetParam param = new VolumeGroupCopySetParam();
            param.setCopySetName(copySet);

            BlockSnapshotSessionList snapshotSessionList = client.application().getVolumeGroupSnapshotSessionsByCopySet(application,
                    param);
            List<BlockSnapshotSessionRestRep> snapshotSessionsTmp = client.blockSnapshotSessions().getByRefs(
                    snapshotSessionList.getSnapSessionRelatedResourceList());
            snapshotSessions.addAll(snapshotSessionsTmp);
            for (BlockSnapshotSessionRestRep session : snapshotSessionsTmp) {

                if (replicationGroups
                        .contains(BlockStorageUtils.stripRPTargetFromReplicationGroup(session.getReplicationGroupInstance()))) {
                    for (RelatedResourceRep target : session.getLinkedTarget()) {
                        BlockSnapshotRestRep blockSnapshot = client.blockSnapshots().get(target);
                        snapshots.add(blockSnapshot);
                    }
                }
            }
        }
        return constructSnapshotWithSnapshotSessionOptions(snapshots, snapshotSessions);
    }

    @Asset("linkedSnapshotsForApplicationSnapshotSession")
    @AssetDependencies({ "application", "applicationSnapshotSessionCopySets" })
    public List<AssetOption> getLinkedSnapshotsForApplicationSnapshotSessionVolume(AssetOptionsContext ctx, URI application,
            String copySet) {
        List<BlockSnapshotRestRep> snapshots = new ArrayList<BlockSnapshotRestRep>();

        VolumeGroupCopySetParam param = new VolumeGroupCopySetParam();
        param.setCopySetName(copySet);

        BlockSnapshotSessionList snapshotSessionList = api(ctx).application().getVolumeGroupSnapshotSessionsByCopySet(application, param);
        List<BlockSnapshotSessionRestRep> snapshotSessions = api(ctx).blockSnapshotSessions().getByRefs(
                snapshotSessionList.getSnapSessionRelatedResourceList());
        for (BlockSnapshotSessionRestRep session : snapshotSessions) {
            for (RelatedResourceRep target : session.getLinkedTarget()) {
                BlockSnapshotRestRep blockSnapshot = api(ctx).blockSnapshots().get(target);
                snapshots.add(blockSnapshot);
            }
        }

        return constructSnapshotWithSnapshotSessionOptions(snapshots, snapshotSessions);
    }

    @Asset("linkedSnapshotsForVolume")
    @AssetDependencies({ "snapshotSessionBlockVolume", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getLinkedSnapshotsForSnapshotSessionVolume(AssetOptionsContext ctx, URI volumeOrCGId,
            String volumeOrConsistencyType) {
        if (!checkTypeConsistency(volumeOrCGId, volumeOrConsistencyType)) {
            warn("Inconsistent types, %s and %s, return empty results", volumeOrCGId, volumeOrConsistencyType);
            return new ArrayList<AssetOption>();
        }
        List<BlockSnapshotRestRep> snapshots = new ArrayList<BlockSnapshotRestRep>();
        List<BlockSnapshotSessionRestRep> snapshotSessions = new ArrayList<BlockSnapshotSessionRestRep>();
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            snapshots = client.blockSnapshots().getByVolume(volumeOrCGId, new DefaultResourceFilter<BlockSnapshotRestRep>());
            snapshotSessions = client.blockSnapshotSessions().getByVolume(volumeOrCGId,
                    new DefaultResourceFilter<BlockSnapshotSessionRestRep>());
        } else {
            snapshots = client.blockSnapshots().getByConsistencyGroup(volumeOrCGId, new DefaultResourceFilter<BlockSnapshotRestRep>());
            snapshotSessions = client.blockSnapshotSessions().getByConsistencyGroup(volumeOrCGId,
                    new DefaultResourceFilter<BlockSnapshotSessionRestRep>());
        }
        return constructSnapshotWithSnapshotSessionOptions(snapshots, snapshotSessions);
    }

    @Asset("linkedSnapshotCopyMode")
    public List<AssetOption> getLinkedSnapshotCopyMode(AssetOptionsContext ctx) {
        // These are hard coded values for now. In the future, this may be available through an API
        List<AssetOption> options = Lists.newArrayList();
        options.add(LINKED_SNAPSHOT_COPYMODE_OPTION);
        options.add(LINKED_SNAPSHOT_NOCOPYMODE_OPTION);
        return options;
    }

    @Asset("displayJournals")
    public List<AssetOption> getDisplayJournals(AssetOptionsContext ctx) {
        // These are hard coded values for now. In the future, this may be available through an API
        List<AssetOption> options = Lists.newArrayList();
        options.add(DISPLAY_JOURNALS_FALSE_OPTION);
        options.add(DISPLAY_JOURNALS_TRUE_OPTION);
        return options;
    }

    private List<AssetOption> getBlockVolumesForHost(ViPRCoreClient client, URI tenant, URI host, boolean mounted) {
        return createVolumeOptions(client, null, host, BlockProviderUtils.getBlockVolumes(client, tenant, host, mounted));
    }

    private List<AssetOption> getBlockVolumesForHost(ViPRCoreClient client, URI tenant, URI host, boolean mounted,
            ResourceFilter<BlockObjectRestRep> filter) {
        List<? extends BlockObjectRestRep> resources = BlockProviderUtils.getBlockVolumes(client, tenant, host, mounted);
        ResourceUtils.applyFilter((List<BlockObjectRestRep>) resources, filter);
        return createVolumeOptions(client, null, host, resources);
    }

    private List<AssetOption> getBlockVolumesForHostDatastore(ViPRCoreClient client, URI tenant, URI host, String datastore) {
        return createVolumeOptions(client, null, host, BlockProviderUtils.getBlockVolumesForDatastore(client, tenant, host, datastore));
    }

    private List<AssetOption> getProjectBlockVolumesForHost(ViPRCoreClient client, URI project, URI host,
            boolean mounted) {
        return getProjectBlockVolumesForHost(client, project, host, mounted, null);
    }

    @SuppressWarnings("unchecked")
    private List<AssetOption> getProjectBlockVolumesForHost(ViPRCoreClient client, URI project, URI host,
            boolean mounted, ResourceFilter<BlockObjectRestRep> filter) {
        List<? extends BlockObjectRestRep> resources = getProjectBlockVolumes(client, host, project, mounted);
        ResourceUtils.applyFilter((List<BlockObjectRestRep>) resources, filter);
        return createVolumeOptions(client, project, host, resources);
    }

    private List<AssetOption> getBlockResourcesForHost(ViPRCoreClient client, URI tenant, URI host, boolean mounted) {
        return getBlockResourcesForHost(client, tenant, host, mounted, null);
    }

    private List<AssetOption> getProjectBlockResourcesForHost(ViPRCoreClient client, URI project, URI host,
            boolean mounted) {
        return getProjectBlockResourcesForHost(client, project, host, mounted, null);
    }

    @SuppressWarnings("unchecked")
    private List<AssetOption> getBlockResourcesForHost(ViPRCoreClient client, URI tenant, URI host, boolean mounted,
            ResourceFilter<BlockObjectRestRep> filter) {
        List<? extends BlockObjectRestRep> resources = BlockProviderUtils.getBlockResources(client, tenant, host, mounted);
        ResourceUtils.applyFilter((List<BlockObjectRestRep>) resources, filter);
        return createVolumeOptions(client, null, host, resources);
    }

    @SuppressWarnings("unchecked")
    private List<AssetOption> getProjectBlockResourcesForHost(ViPRCoreClient client, URI project, URI host,
            boolean mounted, ResourceFilter<BlockObjectRestRep> filter) {
        List<? extends BlockObjectRestRep> resources = getProjectBlockResources(client, host, project, mounted);
        ResourceUtils.applyFilter((List<BlockObjectRestRep>) resources, filter);
        return createVolumeOptions(client, project, host, resources);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("host")
    public List<AssetOption> getMountedBlockVolumesForHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "host", "project" })
    public List<AssetOption> getMountedBlockVolumesForHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getMountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "linuxHost", "project" })
    public List<AssetOption> getMountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("hpuxHost")
    public List<AssetOption> getMountedBlockVolumesForHpuxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "hpuxHost", "project" })
    public List<AssetOption> getMountedBlockVolumesForHpuxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getMountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "windowsHost", "project" })
    public List<AssetOption> getMountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("fileSystemType")
    public List<AssetOption> getWindowsFilesystemTypes(AssetOptionsContext context) {
        return WINDOWS_FILESYSTEM_TYPES;
    }

    @Asset("blockSize")
    public List<AssetOption> getAllWindowsBlockSize(AssetOptionsContext context) {
        return ALL_BLOCKSIZE_OPTIONS;
    }

    @Asset("blockSize")
    @AssetDependencies({ "fileSystemType" })
    public List<AssetOption> getWindowsBlockSize(AssetOptionsContext context, String fileSystemType) {

        if ("ntfs".equals(fileSystemType)) {
            return NTFS_OPTIONS;
        } else {
            return FAT32_OPTIONS;
        }
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies("esxHost")
    public List<AssetOption> getMountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, true);
    }

    @Asset("mountedBlockVolumeDatastore")
    @AssetDependencies({ "esxHost", "blockdatastore" })
    public List<AssetOption> getMountedBlockVolumesForEsxHostDatastore(AssetOptionsContext context, URI host, String datastore) {
        return getBlockVolumesForHostDatastore(api(context), context.getTenant(), host, datastore);
    }

    @Asset("mountedBlockVolume")
    @AssetDependencies({ "esxHost", "project" })
    public List<AssetOption> getMountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, true);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("host")
    public List<AssetOption> getUnmountedBlockVolumesForHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({ "host", "project" })
    public List<AssetOption> getUnmountedBlockVolumesForHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getUnmountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({ "linuxHost", "project" })
    public List<AssetOption> getUnmountedBlockVolumesForLinuxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getUnmountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({ "windowsHost", "project" })
    public List<AssetOption> getUnmountedBlockVolumesForWindowsHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies("esxHost")
    public List<AssetOption> getUnmountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host) {
        return getBlockVolumesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockVolume")
    @AssetDependencies({ "esxHost", "project" })
    public List<AssetOption> getUnmountedBlockVolumesForEsxHost(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockVolumesForHost(api(context), project, host, false,
                new BlockObjectMountPointFilter().not()
                        .and(new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not())));
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("host")
    public List<AssetOption> getMountedBlockResources(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, true);
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("host")
    public List<AssetOption> getMountedBlockResourcesNoTargets(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, true, new BlockObjectSRDFTargetFilter().not());
    }

    @Asset("unmountedBlockResourceNoTargets")
    @AssetDependencies({ "host" })
    public List<AssetOption> getUnmountedBlockResourcesNoTargets(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, false,
                new BlockObjectSRDFTargetFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "host" })
    public List<AssetOption> getUnmountedBlockResources(AssetOptionsContext context, URI host) {
        return getBlockResourcesForHost(api(context), context.getTenant(), host, false);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "host", "project" })
    public List<AssetOption> getUnmountedBlockResources(AssetOptionsContext context, URI host, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, host, false);
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getLinuxMountedBlockResources(AssetOptionsContext context, URI linuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), linuxHost, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getLinuxMountedBlockResourcesNoTargets(AssetOptionsContext context, URI linuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), linuxHost, true,
                new BlockObjectSRDFTargetFilter().not()
                        .and(new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not())));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "linuxHost" })
    public List<AssetOption> getLinuxUnmountedBlockResources(AssetOptionsContext context, URI linuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), linuxHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "linuxHost", "project" })
    public List<AssetOption> getLinuxUnmountedBlockResources(AssetOptionsContext context, URI linuxHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, linuxHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResourcePath")
    @AssetDependencies("linuxHost")
    public List<AssetOption> getLinuxMountedBlockResourcePaths(AssetOptionsContext context, URI linuxHost) {
        List<? extends BlockObjectRestRep> volumes = BlockProviderUtils.getBlockResources(api(context), context.getTenant(), linuxHost,
                true);
        return createStringOptions(getTagValues(KnownMachineTags.getHostMountPointTagName(linuxHost), volumes));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "aixHost" })
    public List<AssetOption> getAixUnmountedBlockResources(AssetOptionsContext context, URI aixHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), aixHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "aixHost", "project" })
    public List<AssetOption> getAixUnmountedBlockResources(AssetOptionsContext context, URI aixHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, aixHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("aixHost")
    public List<AssetOption> getAixMountedBlockResources(AssetOptionsContext context, URI aixHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), aixHost, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("aixHost")
    public List<AssetOption> getAixMountedBlockResourcesNoTargets(AssetOptionsContext context, URI aixHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), aixHost, true,
                new BlockObjectSRDFTargetFilter().not()
                        .and(new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not())));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "hpuxHost" })
    public List<AssetOption> getHpuxUnmountedBlockResources(AssetOptionsContext context, URI hpuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), hpuxHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "hpuxHost", "project" })
    public List<AssetOption> getHpuxUnmountedBlockResources(AssetOptionsContext context, URI hpuxHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, hpuxHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("hpuxHost")
    public List<AssetOption> getHpuxMountedBlockResources(AssetOptionsContext context, URI hpuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), hpuxHost, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("hpuxHost")
    public List<AssetOption> getHpuxMountedBlockResourcesNoTargets(AssetOptionsContext context, URI hpuxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), hpuxHost, true,
                new BlockObjectSRDFTargetFilter().not().and(new BlockObjectVMFSDatastoreFilter().not()));
    }

    @Asset("mountedBlockResource")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getWindowsMountedBlockResources(AssetOptionsContext context, URI windowsHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), windowsHost, true,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResourceNoTargets")
    @AssetDependencies("windowsHost")
    public List<AssetOption> getWindowsMountedBlockResourcesNoTargets(AssetOptionsContext context, URI windowsHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), windowsHost, true,
                new BlockObjectSRDFTargetFilter().not().and(new BlockObjectVMFSDatastoreFilter().not())
                        .and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "windowsHost" })
    public List<AssetOption> getWindowsUnmountedBlockResources(AssetOptionsContext context, URI windowsHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), windowsHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "windowsHost", "project" })
    public List<AssetOption> getWindowsUnmountedBlockResources(AssetOptionsContext context, URI windowsHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, windowsHost, false,
                new BlockObjectVMFSDatastoreFilter().not().and(new BlockObjectBootVolumeFilter().not()));
    }

    @Asset("mountedBlockResource")
    @AssetDependencies({ "esxHost" })
    public List<AssetOption> getESXMountedBlockResources(AssetOptionsContext context, URI esxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), esxHost, true);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "esxHost" })
    public List<AssetOption> getESXUnmountedBlockResources(AssetOptionsContext context, URI esxHost) {
        return getBlockResourcesForHost(api(context), context.getTenant(), esxHost, false);
    }

    @Asset("unmountedBlockResource")
    @AssetDependencies({ "esxHost", "project" })
    public List<AssetOption> getESXUnmountedBlockResources(AssetOptionsContext context, URI esxHost, URI project) {
        return getProjectBlockResourcesForHost(api(context), project, esxHost, false);
    }

    @Asset("snapshotBlockVolume")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getSnapshotBlockVolumes(AssetOptionsContext context, URI project, String storageType) {
        final ViPRCoreClient client = api(context);
        if (isVolumeType(storageType)) {
            List<VolumeRestRep> volumes = listVolumes(client, project);
            List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
            Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);

            List<AssetOption> options = Lists.newArrayList();
            for (VolumeDetail detail : volumeDetails) {
                if (detail.vpool == null) {
                    continue;
                }
                boolean localSnapSupported = isLocalSnapshotSupported(detail.vpool);
                boolean isRPTargetVolume = isRPTargetVolume(detail.volume);
                boolean isRPSourceVolume = isRPSourceVolume(detail.volume);
                boolean isInConsistencyGroup = BlockProvider.isInConsistencyGroup(detail.volume);

                debug("filter[ localSnapSupported=%s, isRPTargetVolume=%s, isRPSourceVolume=%s, isInConsistencyGroup=%s]",
                        localSnapSupported, isRPTargetVolume, isRPSourceVolume, isInConsistencyGroup);

                if (isRPSourceVolume || (localSnapSupported && (!isInConsistencyGroup || isRPTargetVolume))) {
                    options.add(createVolumeOption(client, null, detail.volume, volumeNames));
                }
            }
            return options;
        } else {
            List<BlockConsistencyGroupRestRep> consistencyGroups = client.blockConsistencyGroups().search().byProject(project).run();
            return createBaseResourceOptions(consistencyGroups);
        }
    }

    @Asset("mobilityGroupMethod")
    public List<AssetOption> getMobilityGroupMethods(AssetOptionsContext context) {
        return Lists.newArrayList(MIGRATE_ONLY_OPTION, INGEST_AND_MIGRATE_OPTION);
    }

    @Asset("snapshotSessionBlockVolume")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getSnapshotSessionBlockVolumes(AssetOptionsContext context, URI project, String storageType) {
        final ViPRCoreClient client = api(context);
        if (isVolumeType(storageType)) {
            List<VolumeRestRep> volumes = listVolumes(client, project);
            List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
            Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);

            List<AssetOption> options = Lists.newArrayList();
            for (VolumeDetail detail : volumeDetails) {
                boolean localSnapSupported = isLocalSnapshotSupported(detail.vpool);
                boolean isRPTargetVolume = isRPTargetVolume(detail.volume);
                boolean isRPSourceVolume = isRPSourceVolume(detail.volume);
                boolean isInConsistencyGroup = !StringUtils.isEmpty(detail.volume.getReplicationGroupInstance());
                boolean isSnapshotSessionSupported = isSnapshotSessionSupportedForVolume(detail.volume);

                debug("filter[ localSnapSupported=%s, isRPTargetVolume=%s, isRPSourceVolume=%s, isInConsistencyGroup=%s, isXio3XVolume=%s ]",
                        localSnapSupported, isRPTargetVolume, isRPSourceVolume, isInConsistencyGroup, isSnapshotSessionSupported);

                if (isSnapshotSessionSupported && localSnapSupported && !isInConsistencyGroup) {
                    options.add(createVolumeOption(client, null, detail.volume, volumeNames));
                }
            }
            return options;
        } else {
            List<BlockConsistencyGroupRestRep> consistencyGroups = client.blockConsistencyGroups().search().byProject(project).run();
            return createBaseResourceOptions(consistencyGroups);
        }
    }

    @Asset("snapshotSessionsByVolume")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType", "snapshotSessionBlockVolume" })
    public List<AssetOption> getSnapshotSessionsByVolume(AssetOptionsContext ctx, URI projectId, String type, URI volumeOrCGId) {
        if (isVolumeType(type) && BlockProviderUtils.isType(volumeOrCGId, VOLUME_TYPE)) {
            List<BlockSnapshotSessionRestRep> snapshotSessions = api(ctx).blockSnapshotSessions().getByVolume(volumeOrCGId);
            return constructSnapshotSessionOptions(api(ctx), projectId, snapshotSessions);
        } else if (isConsistencyGroupType(type) && BlockProviderUtils.isType(volumeOrCGId, BLOCK_CONSISTENCY_GROUP_TYPE)) {
            return getConsistencyGroupSnapshotSessions(ctx, volumeOrCGId);
        } else {
            return new ArrayList<AssetOption>();
        }
    }

    @Asset("blockVolumeWithSnapshot")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getBlockVolumesWithSnapshot(AssetOptionsContext context, URI project, String volumeOrConsistencyType) {
        final ViPRCoreClient client = api(context);
        if (isVolumeType(volumeOrConsistencyType)) {

            Set<URI> volIdSet = new HashSet<>();
            List<BlockSnapshotRestRep> snapshots = findSnapshotsByProject(client, project);
            for (BlockSnapshotRestRep snapshot : snapshots) {
                volIdSet.add(snapshot.getParent().getId());
            }
            // Have to get volumes just as it needs vol's mount point which snapshot doesn't have.
            List<VolumeRestRep> volumes = getVolumesByIds(client, volIdSet);
            List<VolumeRestRep> filteredVols = new ArrayList<>();
            for (VolumeRestRep vol : volumes) {
                if (StringUtils.isEmpty(vol.getReplicationGroupInstance())) {
                    filteredVols.add(vol);
                }
            }
            return createVolumeOptions(client, filteredVols);
        } else {
            List<BlockConsistencyGroupRestRep> consistencyGroups = client.blockConsistencyGroups()
                    .search()
                    .byProject(project)
                    .run();
            return createBaseResourceOptions(consistencyGroups);
        }
    }

    @Asset("localSnapshotBlockVolume")
    @AssetDependencies({ "project" })
    public List<AssetOption> getLocalSnapshotBlockVolumes(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);

        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {
            if (isLocalSnapshotSupported(detail.vpool)) {
                options.add(createVolumeOption(client, null, detail.volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("remoteSnapshotBlockVolume")
    @AssetDependencies({ "project" })
    public List<AssetOption> getRemoteSnapshotBlockVolumes(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = ResourceUtils.mapById(volumes);

        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {
            if (isRemoteSnapshotSupported(detail.volume)) {
                options.add(createVolumeOption(client, null, detail.volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("localMirrorBlockVolume")
    @AssetDependencies({ "project" })
    public List<AssetOption> getLocalMirrorBlockVolumes(AssetOptionsContext context, URI project) {
        final ViPRCoreClient client = api(context);
        List<VolumeRestRep> volumes = listVolumes(client, project);
        List<VolumeDetail> volumeDetails = getVolumeDetails(client, volumes);
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);

        List<AssetOption> options = Lists.newArrayList();
        for (VolumeDetail detail : volumeDetails) {
            if (isLocalMirrorSupported(detail.vpool)) {
                options.add(createVolumeOption(client, null, detail.volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("volumeWithContinuousCopies")
    @AssetDependencies("project")
    public List<AssetOption> getVolumesWithContinuousCopies(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(project, new SourceTargetVolumesFilter() {
            @Override
            public boolean accept(VolumeRestRep volume) {
                if (volume.getProtection() == null) {
                    return false;
                }
                MirrorRestRep mirrors = volume.getProtection().getMirrorRep();
                if (mirrors == null || mirrors.getMirrors() == null || mirrors.getMirrors().isEmpty()) {
                    return false;
                }
                return true;
            }
        });
        return createVolumeOptions(client, volumes);
    }

    @Asset("continuousCopies")
    @AssetDependencies("volumeWithContinuousCopies")
    public List<AssetOption> getContinuousCopies(AssetOptionsContext ctx, URI volume) {
        return createVolumeOptions(api(ctx), api(ctx).blockVolumes().getContinuousCopies(volume));
    }

    @Asset("blockJournalSize")
    @AssetDependencies("rpConsistencyGroupByProject")
    public List<AssetOption> getBlockJournalSize(AssetOptionsContext ctx, URI consistencyGroup) {

        String minimumSize = null;

        BlockConsistencyGroupRestRep cg = api(ctx).blockConsistencyGroups().get(consistencyGroup);
        // Get the first volume in the consistency group. All volumes will be source volumes
        RelatedResourceRep vol = cg.getVolumes().get(0);
        VolumeRestRep volume = api(ctx).blockVolumes().get(vol);
        if (volume.getProtection() != null && volume.getProtection().getRpRep() != null
                && volume.getProtection().getRpRep().getProtectionSet() != null) {
            RelatedResourceRep protectionSetId = volume.getProtection().getRpRep().getProtectionSet();
            ProtectionSetRestRep protectionSet = api(ctx).blockVolumes().getProtectionSet(volume.getId(), protectionSetId.getId());
            List<URI> protectionSetVolumeIds = new ArrayList<URI>();
            for (RelatedResourceRep protectionVolume : protectionSet.getVolumes()) {
                protectionSetVolumeIds.add(protectionVolume.getId());
            }
            List<VolumeRestRep> protectionSetVolumes = api(ctx).blockVolumes().withInternal(true).getByIds(protectionSetVolumeIds, null);
            for (VolumeRestRep protectionVolume : protectionSetVolumes) {
                if (protectionVolume.getProtection().getRpRep().getPersonality().equalsIgnoreCase("METADATA")) {
                    String capacity = protectionVolume.getCapacity();
                    if (minimumSize == null || Float.parseFloat(capacity) < Float.parseFloat(minimumSize)) {
                        minimumSize = capacity;
                    }
                }
            }
        }

        if (minimumSize == null) {
            return Lists.newArrayList();
        } else {
            return Lists.newArrayList(newAssetOption(minimumSize, minimumSize));
        }
    }

    @Asset("volumeWithoutConsistencyGroup")
    @AssetDependencies("project")
    public List<AssetOption> getVolumesWithoutConsistencyGroup(AssetOptionsContext ctx, URI project) {
        debug("getting volumes that don't belong to a consistency group (project=%s)", project);
        ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> volumes = client.blockVolumes().findByProject(project);
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        List<AssetOption> options = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            if (volume.getConsistencyGroup() == null) {
                options.add(createVolumeOption(client, null, volume, volumeNames));
            }
        }
        return options;
    }

    @Asset("volumeWithFullCopies")
    @AssetDependencies({ "project", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getVolumesWithFullCopies(AssetOptionsContext ctx, URI project, String volumeOrConsistencyType) {
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            List<VolumeRestRep> volumes = findVolumesByProject(client, project);
            List<VolumeRestRep> filteredVols = new ArrayList<>();
            for (VolumeRestRep vol : volumes) {
                if (vol.getProtection() == null ||
                        vol.getProtection().getFullCopyRep() == null ||
                        vol.getProtection().getFullCopyRep().getFullCopyVolumes() == null ||
                        vol.getProtection().getFullCopyRep().getFullCopyVolumes().isEmpty() ||
                        !StringUtils.isEmpty(vol.getReplicationGroupInstance())) {
                    continue;
                }
                filteredVols.add(vol);
            }
            log.info("Got volumes with full copies: [{}]", filteredVols.size());
            return createVolumeOptions(client, filteredVols);
        } else {
            List<BlockConsistencyGroupRestRep> consistencyGroups = api(ctx).blockConsistencyGroups()
                    .search()
                    .byProject(project)
                    .run();

            return createBaseResourceOptions(consistencyGroups);
        }
    }

    /**
     * Find all volumes by a project
     * 
     * @param client
     * @param project
     * @return a list of volume REST representations.
     */
    private List<VolumeRestRep> findVolumesByProject(ViPRCoreClient client, URI project) {
        log.info("Finding volumes by project {}", project);
        List<SearchResultResourceRep> volRefs = client.blockVolumes().performSearchBy(SearchConstants.PROJECT_PARAM, project);
        List<URI> ids = new ArrayList<>();
        for (SearchResultResourceRep volRef : volRefs) {
            ids.add(volRef.getId());
        }

        List<VolumeRestRep> volumes = client.blockVolumes().getByIds(ids, null);
        log.info("Got volumes: [{}]", volumes.size());

        return volumes;
    }

    @Asset("fullCopy")
    @AssetDependencies({ "volumeWithFullCopies", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getFullCopies(AssetOptionsContext ctx, URI volumeId, String volumeOrConsistencyType) {

        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            if (!BlockProviderUtils.isType(volumeId, VOLUME_TYPE)) {
                warn("Inconsistent types, %s and %s, return empty results", volumeId, volumeOrConsistencyType);
                return new ArrayList<AssetOption>();
            }

            return createVolumeOptions(client, client.blockVolumes().getFullCopies(volumeId));
        } else {
            if (!BlockProviderUtils.isType(volumeId, BLOCK_CONSISTENCY_GROUP_TYPE)) {
                warn("Inconsistent types, %s and %s, return empty results", volumeId, volumeOrConsistencyType);
                return new ArrayList<AssetOption>();
            }
            return getConsistencyGroupFullCopies(ctx, volumeId);
        }
    }

    @Asset("fullCopyAvailableForDetach")
    @AssetDependencies({ "volumeWithFullCopies", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getFullCopiedDetach(AssetOptionsContext ctx, URI volumeId, String volumeOrConsistencyType) {
        if (!checkTypeConsistency(volumeId, volumeOrConsistencyType)) {
            warn("Inconsistent types, %s and %s, return empty results", volumeId, volumeOrConsistencyType);
            return new ArrayList<AssetOption>();
        }
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            List<VolumeRestRep> volumes = client.blockVolumes().getFullCopies(volumeId, new DefaultResourceFilter<VolumeRestRep>() {
                @Override
                public boolean accept(VolumeRestRep volume) {
                    String replicaState = volume.getProtection().getFullCopyRep().getReplicaState();
                    return replicaState != null &&
                            !(replicaState.equals(ReplicationState.DETACHED.name()));
                }
            });
            return createVolumeOptions(client, volumes);
        } else {
            return getConsistencyGroupFullCopies(ctx, volumeId);
        }
    }

    private List<AssetOption> getConsistencyGroupFullCopies(AssetOptionsContext ctx, URI consistencyGroupId) {
        return createNamedResourceOptions(api(ctx).blockConsistencyGroups().getFullCopies(consistencyGroupId));
    }

    private List<AssetOption> getConsistencyGroupSnapshots(AssetOptionsContext ctx, URI consistencyGroupId) {
        return createNamedResourceOptions(api(ctx).blockConsistencyGroups().getSnapshots(consistencyGroupId));
    }

    private List<AssetOption> getConsistencyGroupSnapshotSessions(AssetOptionsContext ctx, URI consistencyGroupId) {
        return createNamedResourceOptions(api(ctx).blockConsistencyGroups().getSnapshotSessions(consistencyGroupId));
    }

    @Asset("fullCopyAvailableForResynchronize")
    @AssetDependencies({ "volumeWithFullCopies", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getFullCopiedSynchronize(AssetOptionsContext ctx, URI volumeId, String volumeOrConsistencyType) {
        if (!checkTypeConsistency(volumeId, volumeOrConsistencyType)) {
            warn("Inconsistent types, %s and %s, return empty results", volumeId, volumeOrConsistencyType);
            return new ArrayList<AssetOption>();
        }
        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            List<VolumeRestRep> volumes = client.blockVolumes().getFullCopies(volumeId, new DefaultResourceFilter<VolumeRestRep>() {
                @Override
                public boolean accept(VolumeRestRep volume) {
                    String replicaState = volume.getProtection().getFullCopyRep().getReplicaState();
                    return replicaState != null &&
                            !(replicaState.equals(ReplicationState.DETACHED.name())) &&
                            !(replicaState.equals(ReplicationState.INACTIVE.name()));
                }
            });
            return createVolumeOptions(client, volumes);
        } else {
            return getConsistencyGroupFullCopies(ctx, volumeId);
        }
    }

    @Asset("fullCopyAvailableForRestore")
    @AssetDependencies({ "volumeWithFullCopies", "blockVolumeOrConsistencyType" })
    public List<AssetOption> getFullCopiedRestore(AssetOptionsContext ctx, URI volumeId, String volumeOrConsistencyType) {
        if (!checkTypeConsistency(volumeId, volumeOrConsistencyType)) {
            warn("Inconsistent types, %s and %s, return empty results", volumeId, volumeOrConsistencyType);
            return new ArrayList<AssetOption>();
        }

        final ViPRCoreClient client = api(ctx);
        if (isVolumeType(volumeOrConsistencyType)) {
            List<VolumeRestRep> volumes = client.blockVolumes().getFullCopies(volumeId, new DefaultResourceFilter<VolumeRestRep>() {
                @Override
                public boolean accept(VolumeRestRep volume) {
                    String replicaState = volume.getProtection().getFullCopyRep().getReplicaState();
                    return replicaState != null &&
                            !(replicaState.equals(ReplicationState.RESTORED.name())) &&
                            !(replicaState.equals(ReplicationState.INACTIVE.name())) &&
                            !(replicaState.equals(ReplicationState.DETACHED.name()));
                }
            });
            return createVolumeOptions(client, volumes);
        } else {
            return getConsistencyGroupFullCopies(ctx, volumeId);
        }
    }

    @Asset("mobilityGroup")
    public List<AssetOption> getMobilityGroups(AssetOptionsContext ctx) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeGroupRestRep> volumeGroups = client.application().getApplications(new DefaultResourceFilter<VolumeGroupRestRep>() {
            @Override
            public boolean accept(VolumeGroupRestRep volumeGroup) {
                if (volumeGroup.getRoles() != null && volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.MOBILITY.name())) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        return createBaseResourceOptions(volumeGroups);
    }

    @Asset("addMobilityGroupResource")
    @AssetDependencies("mobilityGroup")
    public List<AssetOption> getAddMobilityGroupResources(AssetOptionsContext ctx, final URI mobilityGroupId) {
        final ViPRCoreClient client = api(ctx);
        VolumeGroupRestRep mobilityGroup = client.application().get(mobilityGroupId);
        if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
            // VPLEX volumes that don't have reference to this mobility group
            List<URI> volumeIds = client.blockVolumes().listBulkIds();
            final ResourceFilter<VolumeRestRep> vplexFilter = new VplexVolumeFilter();
            List<VolumeRestRep> volumes = client.blockVolumes().getByIds(volumeIds, new ResourceFilter<VolumeRestRep>() {
                @Override
                public boolean acceptId(URI id) {
                    return true;
                }

                @Override
                public boolean accept(VolumeRestRep item) {
                    boolean accept = (item.getVolumeGroups() == null || !contains(item, mobilityGroupId))
                            && vplexFilter.accept(item);
                    if (accept) {
                        boolean rpProtection = (item.getProtection() != null
                                && item.getProtection().getRpRep() != null
                                && item.getProtection().getRpRep().getPersonality() != null);
                        if (rpProtection) {
                            // If RP+VPLEX protection specified, only allow RP SOURCE volumes to be listed
                            // as candidates for mobility groups. Exclude TARGETs and JOURNALs.
                            String personality = item.getProtection().getRpRep().getPersonality();
                            if (Volume.PersonalityTypes.TARGET.name().equals(personality)
                                    || Volume.PersonalityTypes.METADATA.name().equals(personality)) {
                                accept = false;
                            }
                        }
                    }
                    return accept;
                }

                private boolean contains(VolumeRestRep item, URI mobilityGroup) {
                    for (RelatedResourceRep vg : item.getVolumeGroups()) {
                        if (vg.getId().equals(mobilityGroup)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
            return createBaseResourceOptions(volumes);
        } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
            List<URI> hostIds = client.hosts().listBulkIds();
            List<HostRestRep> hosts = client.hosts().getByIds(hostIds, new ResourceFilter<HostRestRep>() {

                @Override
                public boolean acceptId(URI id) {
                    return true;
                }

                @Override
                public boolean accept(HostRestRep item) {
                    return item.getVolumeGroups() == null || !contains(item, mobilityGroupId);
                }

                private boolean contains(HostRestRep item, URI mobilityGroup) {
                    for (RelatedResourceRep vg : item.getVolumeGroups()) {
                        if (vg.getId().equals(mobilityGroup)) {
                            return true;
                        }
                    }
                    return false;
                }

            });
            return createBaseResourceOptions(hosts);
        } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {

            List<URI> clusterIds = client.clusters().listBulkIds();
            List<ClusterRestRep> clusters = client.clusters().getByIds(clusterIds, new ResourceFilter<ClusterRestRep>() {

                @Override
                public boolean acceptId(URI id) {
                    return true;
                }

                @Override
                public boolean accept(ClusterRestRep item) {
                    return item.getVolumeGroups() == null || !contains(item, mobilityGroupId);
                }

                private boolean contains(ClusterRestRep item, URI mobilityGroup) {
                    for (RelatedResourceRep vg : item.getVolumeGroups()) {
                        if (vg.getId().equals(mobilityGroup)) {
                            return true;
                        }
                    }
                    return false;
                }

            });
            return createBaseResourceOptions(clusters);
        } else {
            return Lists.newArrayList();
        }
    }

    @Asset("removeMobilityGroupResource")
    @AssetDependencies("mobilityGroup")
    public List<AssetOption> getRemoveMobilityGroupResources(AssetOptionsContext ctx, URI mobilityGroupId) {
        final ViPRCoreClient client = api(ctx);
        VolumeGroupRestRep mobilityGroup = client.application().get(mobilityGroupId);
        if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.VOLUMES.name())) {
            return createNamedResourceOptions(client.application().listVolumes(mobilityGroupId));
        } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.HOSTS.name())) {
            return createNamedResourceOptions(client.application().getHosts(mobilityGroupId));
        } else if (mobilityGroup.getMigrationGroupBy().equals(VolumeGroup.MigrationGroupBy.CLUSTERS.name())) {
            return createNamedResourceOptions(client.application().getClusters(mobilityGroupId));
        } else {
            return Lists.newArrayList();
        }
    }

    @Asset("application")
    public List<AssetOption> getApplications(AssetOptionsContext ctx) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeGroupRestRep> volumeGroups = client.application().getApplications(new DefaultResourceFilter<VolumeGroupRestRep>() {
            @Override
            public boolean accept(VolumeGroupRestRep volumeGroup) {
                if (volumeGroup.getRoles() != null && volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.name())) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        return createBaseResourceOptions(volumeGroups);
    }

    @Asset("applicationBlockVolume")
    @AssetDependencies("application")
    public List<AssetOption> getApplicationVolumes(AssetOptionsContext ctx, URI application) {
        final ViPRCoreClient client = api(ctx);
        List<NamedRelatedResourceRep> volList = client.application().listVolumes(application);
        List<AssetOption> options = new ArrayList<AssetOption>();
        if (volList != null && !volList.isEmpty()) {
            List<VolumeRestRep> allVols = client.blockVolumes().getByRefs(volList);
            Map<String, List<VolumeRestRep>> repGrpVolMap = new HashMap<String, List<VolumeRestRep>>();
            for (VolumeRestRep vol : allVols) {
                if (!BlockProviderUtils.isRPTargetReplicationGroup(vol.getReplicationGroupInstance())) {
                    if (repGrpVolMap.get(vol.getReplicationGroupInstance()) == null) {
                        repGrpVolMap.put(vol.getReplicationGroupInstance(), new ArrayList<VolumeRestRep>());
                    }
                    repGrpVolMap.get(vol.getReplicationGroupInstance()).add(vol);
                }
            }
            for (Entry<String, List<VolumeRestRep>> entry : repGrpVolMap.entrySet()) {
                for (VolumeRestRep vol : entry.getValue()) {
                    options.add(new AssetOption(vol.getId(),
                            getMessage("application.volumes.replication_group", vol.getName(), vol.getReplicationGroupInstance())));
                }
            }
        }
        return options;
    }

    @Asset("replicationGroup")
    @AssetDependencies("application")
    public List<AssetOption> getApplicationReplicationGroups(AssetOptionsContext ctx, URI applicationId) {
        return createStringOptions(BlockProviderUtils.getApplicationReplicationGroupNames(api(ctx), applicationId));
    }

    @Asset("copyReplicationGroup")
    @AssetDependencies({ "application", "restoreFullCopyName" })
    public List<AssetOption> getApplicationReplicationGroupsForCopy(AssetOptionsContext ctx, URI applicationId, String copyName) {
        final ViPRCoreClient client = api(ctx);
        List<VolumeRestRep> allCopyVols = client.blockVolumes()
                .getByRefs(client.application().getFullCopiesByApplication(applicationId).getVolumes());
        return createStringOptions(BlockStorageUtils.stripRPTargetFromReplicationGroup(
                groupFullCopyByApplicationSubGroup(ctx,
                        filterByCopyName(allCopyVols, copyName)).keySet()));
    }

    @Asset("copyReplicationGroup")
    @AssetDependencies({ "application", "fullCopyName" })
    public List<AssetOption> getApplicationReplicationGroupsForFullCopyName(AssetOptionsContext ctx, URI applicationId, String copyName) {
        return getApplicationReplicationGroupsForCopy(ctx, applicationId, copyName);
    }

    private Map<String, List<VolumeRestRep>> groupFullCopyByApplicationSubGroup(AssetOptionsContext ctx, List<VolumeRestRep> vols) {
        final ViPRCoreClient client = api(ctx);
        Map<String, List<VolumeRestRep>> grouped = new HashMap<String, List<VolumeRestRep>>();
        Set<RelatedResourceRep> parentVolIds = new HashSet<RelatedResourceRep>();
        for (VolumeRestRep vol : vols) {
            if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                    && vol.getProtection().getFullCopyRep().getAssociatedSourceVolume() != null) {
                parentVolIds.add(vol.getProtection().getFullCopyRep().getAssociatedSourceVolume());
            }
        }
        List<VolumeRestRep> parentVolumes = client.blockVolumes().getByRefs(parentVolIds);
        if (parentVolumes != null && !parentVolumes.isEmpty()) {
            for (VolumeRestRep vol : parentVolumes) {
                if (grouped.get(vol.getReplicationGroupInstance()) == null) {
                    grouped.put(vol.getReplicationGroupInstance(), new ArrayList<VolumeRestRep>());
                }
                grouped.get(vol.getReplicationGroupInstance()).add(vol);
            }
        }
        return grouped;
    }

    private List<VolumeRestRep> filterByCopyName(List<VolumeRestRep> allCopyVols, String copyName) {
        List<VolumeRestRep> filtered = new ArrayList<VolumeRestRep>();
        if (allCopyVols != null) {
            for (VolumeRestRep vol : allCopyVols) {
                if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                        && copyName.equals(vol.getProtection().getFullCopyRep().getFullCopySetName())) {
                    filtered.add(vol);
                }
            }
        }
        return filtered;
    }

    @Asset("fullCopyName")
    @AssetDependencies("application")
    public List<AssetOption> getApplicationFullCopyNames(AssetOptionsContext ctx, URI applicationId) {
        List<VolumeRestRep> fullCopies = getFullCopiesForApplication(ctx, applicationId);
        Set<String> fullCopyNames = new HashSet<String>();
        for (VolumeRestRep vol : fullCopies) {
            if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                    && vol.getProtection().getFullCopyRep().getFullCopySetName() != null) {
                fullCopyNames.add(vol.getProtection().getFullCopyRep().getFullCopySetName());
            }
        }
        return createStringOptions(fullCopyNames);
    }

    /**
     * returns the list of application full copies that can be restored from. For RP, we exclude target copies because the target is read
     * only
     * 
     * @param ctx
     * @param applicationId
     * @return
     */
    @Asset("restoreFullCopyName")
    @AssetDependencies("application")
    public List<AssetOption> getApplicationFullCopyNamesForRestore(AssetOptionsContext ctx, URI applicationId) {
        List<VolumeRestRep> fullCopies = getFullCopiesForApplication(ctx, applicationId);
        Set<String> fullCopyNames = new HashSet<String>();
        for (VolumeRestRep vol : fullCopies) {
            if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                    && vol.getProtection().getFullCopyRep().getFullCopySetName() != null
                    && !fullCopyNames.contains(vol.getProtection().getFullCopyRep().getFullCopySetName())) {
                if (!isFullCopyOfRPTarget(ctx, vol)) {
                    fullCopyNames.add(vol.getProtection().getFullCopyRep().getFullCopySetName());
                }
            }
        }
        return createStringOptions(fullCopyNames);
    }

    /**
     * returns true if the passed in volume is a clone of an RP source volume
     * 
     * @param vol
     * @return
     */
    private boolean isFullCopyOfRPTarget(AssetOptionsContext ctx, VolumeRestRep vol) {
        final ViPRCoreClient client = api(ctx);
        // get the source volume for the full copy
        if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                && vol.getProtection().getFullCopyRep().getAssociatedSourceVolume() != null) {
            if (BlockStorageUtils
                    .isRPTargetVolume(client.blockVolumes().get(vol.getProtection().getFullCopyRep().getAssociatedSourceVolume()))) {
                return true;
            }
        }
        return false;
    }

    @Asset("siteFullCopyName")
    @AssetDependencies({ "application", "applicationVirtualArray" })
    public List<AssetOption> getApplicationFullCopyNamesForSite(AssetOptionsContext ctx, URI applicationId, URI applicationVirtualArray) {
        List<VolumeRestRep> fullCopies = getFullCopiesForApplication(ctx, applicationId);
        Set<String> fullCopyNames = new HashSet<String>();
        for (VolumeRestRep vol : fullCopies) {
            if (vol != null && vol.getProtection() != null && vol.getProtection().getFullCopyRep() != null
                    && vol.getProtection().getFullCopyRep().getFullCopySetName() != null
                    && (applicationVirtualArray == null || vol.getVirtualArray().getId().equals(applicationVirtualArray))) {
                fullCopyNames.add(vol.getProtection().getFullCopyRep().getFullCopySetName());
            }
        }
        return createStringOptions(fullCopyNames);
    }

    private List<VolumeRestRep> getFullCopiesForApplication(AssetOptionsContext ctx, URI applicationId) {
        List<VolumeRestRep> fullCopies = new ArrayList<VolumeRestRep>();
        final ViPRCoreClient client = api(ctx);
        NamedVolumesList volList = client.application().getFullCopiesByApplication(applicationId);
        if (volList != null && volList.getVolumes() != null && !volList.getVolumes().isEmpty()) {
            List<URI> ids = new ArrayList<URI>();
            for (NamedRelatedResourceRep volId : volList.getVolumes()) {
                ids.add(volId.getId());
            }
            fullCopies.addAll(client.blockVolumes().getByIds(ids));
        }
        return fullCopies;
    }

    @Asset("applicationSnapshotVirtualArray")
    @AssetDependencies("application")
    public List<AssetOption> getApplicationSnapshotVirtualArrays(AssetOptionsContext ctx, URI applicationId) {
        final ViPRCoreClient client = api(ctx);
        List<NamedRelatedResourceRep> volList = client.application().listVolumes(applicationId);
        List<AssetOption> options = new ArrayList<AssetOption>();
        boolean isVplex = false;
        boolean isRP = false;
        URI sourceVarrayId = null;
        List<VolumeRestRep> allRPSourceVols = null;

        if (volList != null && !volList.isEmpty()) {
            VolumeRestRep vol = client.blockVolumes().get(volList.get(0));
            StorageSystemRestRep sys = client.storageSystems().get(vol.getStorageController());
            if (sys.getSystemType().equals("vplex")) {
                isVplex = true;
                sourceVarrayId = vol.getVirtualArray().getId();
            }
            if (BlockProviderUtils.isVolumeRP(vol)) {
                isRP = true;
                allRPSourceVols = client.blockVolumes().getByRefs(volList, RecoverPointPersonalityFilter.SOURCE);
                if (allRPSourceVols != null && !allRPSourceVols.isEmpty()) {
                    VolumeRestRep rpvol = allRPSourceVols.get(0);
                    sourceVarrayId = rpvol.getVirtualArray().getId();
                }
            }
        } else {
            return options;
        }

        // if it's neither RP nor vplex, it's just a simple block volume application; site is not needed
        if (!isVplex && !isRP) {
            options.add(newAssetOption(URI.create("none"), "None"));
        }

        // if the volumes are vplex or RP display source as an option
        if (isVplex || isRP) {
            options.add(newAssetOption(sourceVarrayId, "protection.site.type.source"));
        }

        // if the volumes are RP (vplex or not) add the RP targets as options
        if (isRP) {
            Set<URI> targetVarrayIds = new HashSet<URI>();
            List<AssetOption> targetOptions = new ArrayList<AssetOption>();
            List<VolumeRestRep> allRPTargetVols = client.blockVolumes().getByRefs(volList, RecoverPointPersonalityFilter.TARGET);
            for (VolumeRestRep targetVol : allRPTargetVols) {
                targetVarrayIds.add(targetVol.getVirtualArray().getId());
            }
            List<VirtualArrayRestRep> targetVarrays = client.varrays().getByIds(targetVarrayIds);
            for (VirtualArrayRestRep targetVarray : targetVarrays) {
                targetOptions.add(newAssetOption(String.format("tgt:%s", targetVarray.getId().toString()),
                        "protection.site.type.target", targetVarray.getName()));
            }

            // sort the targets
            AssetOptionsUtils.sortOptionsByLabel(targetOptions);

            options.addAll(targetOptions);

        }
        return options;
    }

    @Asset("applicationVirtualArray")
    @AssetDependencies("application")
    public List<AssetOption> getApplicationVirtualArrays(AssetOptionsContext ctx, URI applicationId) {
        final ViPRCoreClient client = api(ctx);
        List<NamedRelatedResourceRep> volList = client.application().listVolumes(applicationId);
        List<AssetOption> options = new ArrayList<AssetOption>();
        boolean isRP = false;
        URI sourceVarrayId = null;
        List<VolumeRestRep> allRPSourceVols = null;

        if (volList != null && !volList.isEmpty()) {
            VolumeRestRep vol = client.blockVolumes().get(volList.get(0));
            if (BlockProviderUtils.isVolumeRP(vol)) {
                isRP = true;
                allRPSourceVols = client.blockVolumes().getByRefs(volList, RecoverPointPersonalityFilter.SOURCE);
                if (allRPSourceVols != null && !allRPSourceVols.isEmpty()) {
                    VolumeRestRep rpvol = allRPSourceVols.get(0);
                    sourceVarrayId = rpvol.getVirtualArray().getId();
                }
            }
        } else {
            options.add(newAssetOption(URI.create("none"), "None"));
            return options;
        }

        // if the volumes are RP (vplex or not) add the RP targets as options
        if (isRP) {
            options.add(newAssetOption(sourceVarrayId, "protection.site.type.source"));
            Set<URI> targetVarrayIds = new HashSet<URI>();
            List<AssetOption> targetOptions = new ArrayList<AssetOption>();
            List<VolumeRestRep> allRPTargetVols = client.blockVolumes().getByRefs(volList, RecoverPointPersonalityFilter.TARGET);
            for (VolumeRestRep targetVol : allRPTargetVols) {
                targetVarrayIds.add(targetVol.getVirtualArray().getId());
            }
            List<VirtualArrayRestRep> targetVarrays = client.varrays().getByIds(targetVarrayIds);
            for (VirtualArrayRestRep targetVarray : targetVarrays) {
                targetOptions.add(newAssetOption(String.format("tgt:%s", targetVarray.getId().toString()),
                        "protection.site.type.target", targetVarray.getName()));
            }

            // sort the targets
            AssetOptionsUtils.sortOptionsByLabel(targetOptions);

            options.addAll(targetOptions);

        }
        if (options.isEmpty()) {
            options.add(newAssetOption(URI.create("none"), "None"));
        }
        return options;
    }

    class VirtualPoolFilter extends DefaultResourceFilter<VolumeRestRep> {

        private final URI virtualPoolId;

        public VirtualPoolFilter(URI virtualPoolId) {
            this.virtualPoolId = virtualPoolId;
        }

        @Override
        public boolean accept(VolumeRestRep item) {
            return ObjectUtils.equals(ResourceUtils.id(item.getVirtualPool()), virtualPoolId);
        }

    }

    @SafeVarargs
    public static List<VolumeRestRep> listSourceVolumes(ViPRCoreClient client, URI project, ResourceFilter<VolumeRestRep>... filters) {
        // Filter volumes that are not SRDF Targets, not RP Targets and not RP Metadata
        FilterChain<VolumeRestRep> filter = new FilterChain<VolumeRestRep>(new SRDFTargetFilter().not());
        filter.and(RecoverPointPersonalityFilter.TARGET.not());
        filter.and(RecoverPointPersonalityFilter.METADATA.not());
        filter.and(new BlockVolumeBootVolumeFilter().not());
        for (ResourceFilter<VolumeRestRep> additionalFilter : filters) {
            filter.and(additionalFilter);
        }
        return client.blockVolumes().findByProject(project, filter);
    }

    @SafeVarargs
    public static List<VolumeRestRep> listJournalVolumes(ViPRCoreClient client, URI project, ResourceFilter<VolumeRestRep>... filters) {
        // Filter all volumes except Journals
        FilterChain<VolumeRestRep> filter = new FilterChain<VolumeRestRep>(new SRDFTargetFilter().not());
        filter.and(RecoverPointPersonalityFilter.METADATA);
        for (ResourceFilter<VolumeRestRep> additionalFilter : filters) {
            filter.and(additionalFilter);
        }
        return client.blockVolumes().withInternal(true).findByProject(project, filter);
    }

    protected static List<VolumeRestRep> listVolumes(ViPRCoreClient client, URI project) {
        return client.blockVolumes().findByProject(project);
    }

    protected List<VolumeRestRep> listVolumesWithoutConsistencyGroup(ViPRCoreClient client, URI project) {
        return client.blockVolumes().findByProject(project, new DefaultResourceFilter<VolumeRestRep>() {

            @Override
            public boolean accept(VolumeRestRep item) {
                return !isInConsistencyGroup(item) || isIBMXIVVolume(item);
            }
        });
    }

    protected static List<AssetOption> createVolumeWithVarrayOptions(ViPRCoreClient client,
            Collection<? extends BlockObjectRestRep> blockObjects) {
        List<URI> varrayIds = getVirtualArrayIds(blockObjects);
        Map<URI, VirtualArrayRestRep> varrayNames = getVirutalArrayNames(client, varrayIds);
        List<AssetOption> options = Lists.newArrayList();
        for (BlockObjectRestRep blockObject : blockObjects) {
            options.add(createVolumeWithVarrayOption(blockObject, varrayNames));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected static AssetOption createVolumeWithVarrayOption(BlockObjectRestRep blockObject, Map<URI, VirtualArrayRestRep> varrayNames) {
        String label = getBlockObjectLabelWithVarray(blockObject, varrayNames);
        String name = getBlockObjectName(null, blockObject);
        if (StringUtils.isNotBlank(name)) {
            label = String.format("%s: %s", name, label);
        }
        return new AssetOption(blockObject.getId(), label);
    }

    private static String getBlockObjectLabelWithVarray(BlockObjectRestRep blockObject, Map<URI, VirtualArrayRestRep> varrayNames) {
        if (blockObject instanceof VolumeRestRep) {
            VolumeRestRep volume = (VolumeRestRep) blockObject;
            String varrayName = varrayNames.get(volume.getVirtualArray().getId()).getName();
            return getMessage("block.unexport.volume", volume.getName(), volume.getProvisionedCapacity(), varrayName, volume.getWwn());
        }
        return blockObject.getName();
    }

    private static List<URI> getVirtualArrayIds(Collection<? extends BlockObjectRestRep> blockObjects) {
        List<URI> varrayIds = Lists.newArrayList();
        for (BlockObjectRestRep blockObject : blockObjects) {
            if (blockObject instanceof VolumeRestRep) {
                VolumeRestRep volume = (VolumeRestRep) blockObject;
                varrayIds.add(volume.getVirtualArray().getId());
            }
        }
        return varrayIds;
    }

    protected static Map<URI, VirtualArrayRestRep> getVirutalArrayNames(ViPRCoreClient client, List<URI> varrays) {
        if (varrays == null) {
            return Collections.emptyMap();
        }
        return ResourceUtils.mapById(client.varrays().getByIds(varrays));
    }

    protected static List<AssetOption> createVolumeOptions(ViPRCoreClient client, Collection<? extends BlockObjectRestRep> blockObjects) {
        return createVolumeOptions(client, null, null, blockObjects);
    }

    protected static List<AssetOption> createVolumeOptions(ViPRCoreClient client, URI project, URI hostId,
            Collection<? extends BlockObjectRestRep> blockObjects) {
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        List<AssetOption> options = Lists.newArrayList();
        for (BlockObjectRestRep blockObject : blockObjects) {
            options.add(createVolumeOption(client, hostId, blockObject, volumeNames));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected static AssetOption createVolumeOption(ViPRCoreClient client, URI hostId, BlockObjectRestRep blockObject,
            Map<URI, VolumeRestRep> volumeNames) {
        String label = getBlockObjectLabel(client, blockObject, volumeNames);
        String name = getBlockObjectName(hostId, blockObject);
        if (StringUtils.isNotBlank(name)) {
            label = String.format("%s: %s", name, label);
        }
        return new AssetOption(blockObject.getId(), label);
    }

    protected static Map<URI, VolumeRestRep> getProjectVolumeNames(ViPRCoreClient client, URI project) {
        if (project == null) {
            return Collections.emptyMap();
        }
        return ResourceUtils.mapById(client.blockVolumes().findByProject(project));
    }

    protected static String getBlockObjectName(URI hostId, BlockObjectRestRep blockObject) {
        if (hostId != null) {
            String mountPoint = KnownMachineTags.getBlockVolumeMountPoint(hostId, blockObject);
            if (StringUtils.isNotBlank(mountPoint)) {
                return mountPoint;
            }
        }

        String datastore = KnownMachineTags.getBlockVolumeVMFSDatastore(hostId, blockObject);
        if (StringUtils.isNotBlank(datastore)) {
            return datastore;
        }
        return null;
    }

    protected boolean isMounted(Collection<URI> hostIds, VolumeRestRep volume) {
        for (URI hostId : hostIds) {
            if (BlockProviderUtils.isMounted(hostId, volume)) {
                return true;
            }
        }
        return false;
    }

    private static String getBlockObjectLabel(ViPRCoreClient client, BlockObjectRestRep blockObject, Map<URI, VolumeRestRep> volumeNames) {
        if (blockObject instanceof VolumeRestRep) {
            if (blockObject instanceof BlockMirrorRestRep) {
                BlockMirrorRestRep mirror = (BlockMirrorRestRep) blockObject;
                return getMessage("block.mirror", mirror.getName(), mirror.getWwn());
            } else {
                VolumeRestRep volume = (VolumeRestRep) blockObject;
                return getMessage("block.volume", volume.getName(), volume.getProvisionedCapacity(), volume.getWwn());
            }
        } else if (blockObject instanceof BlockSnapshotRestRep) {
            BlockSnapshotRestRep snapshot = (BlockSnapshotRestRep) blockObject;
            return getMessage("block.snapshot.label", snapshot.getName(), getBlockSnapshotParentVolumeName(volumeNames, snapshot),
                    snapshot.getWwn());
        } else if (blockObject instanceof BlockSnapshotSessionRestRep) {
            BlockSnapshotSessionRestRep snapshotSession = (BlockSnapshotSessionRestRep) blockObject;
            return getMessage("block.snapshotsession.label", snapshotSession.getName(),
                    getBlockSnapshotSessionParentVolumeName(volumeNames, snapshotSession));
        }
        return blockObject.getName();
    }

    private static String getBlockSnapshotLinkedLabel(BlockSnapshotRestRep snapshot, Map<URI, String> linkedSnapshotToSnapshotSessionMap) {
        String snapshotSessionName = linkedSnapshotToSnapshotSessionMap.get(snapshot.getId());
        if (snapshotSessionName != null && !snapshotSessionName.isEmpty()) {
            return getMessage("block.snapshot.linked", snapshot.getName(), snapshotSessionName);
        }
        return getMessage("block.snapshot.linked.unknown", snapshot.getName());
    }

    protected static String getBlockSnapshotParentVolumeName(Map<URI, VolumeRestRep> volumeNames, BlockSnapshotRestRep snapshot) {
        if (snapshot.getParent() != null &&
                snapshot.getParent().getId() != null &&
                volumeNames != null &&
                volumeNames.get(snapshot.getParent().getId()) != null) {
            return ResourceUtils.name(volumeNames.get(snapshot.getParent().getId()));
        }
        return getMessage("block.snapshot.unknown.volume");
    }

    protected static String getBlockSnapshotSessionParentVolumeName(Map<URI, VolumeRestRep> volumeNames,
            BlockSnapshotSessionRestRep snapshotSession) {
        if (snapshotSession.getParent() != null &&
                snapshotSession.getParent().getId() != null &&
                volumeNames != null &&
                volumeNames.get(snapshotSession.getParent().getId()) != null) {
            return ResourceUtils.name(volumeNames.get(snapshotSession.getParent().getId()));
        }
        return getMessage("block.snapshot.unknown.volume");
    }

    private List<AssetOption>
            getSnapshotOptionsForProject(AssetOptionsContext ctx, URI project, ResourceFilter<BlockSnapshotRestRep> filter) {
        ViPRCoreClient client = api(ctx);
        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().findByProject(project, filter);
        return constructSnapshotOptions(client, project, snapshots);
    }

    private List<AssetOption> getSnapshotOptionsForProject(AssetOptionsContext ctx, URI project) {
        ViPRCoreClient client = api(ctx);
        List<BlockSnapshotRestRep> snapshots = client.blockSnapshots().findByProject(project);
        return constructSnapshotOptions(client, project, snapshots);
    }

    protected List<AssetOption> constructSnapshotOptions(ViPRCoreClient client, URI project, List<BlockSnapshotRestRep> snapshots) {
        List<AssetOption> options = Lists.newArrayList();
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        for (BlockSnapshotRestRep snapshot : snapshots) {
            options.add(new AssetOption(snapshot.getId(), getBlockObjectLabel(client, snapshot, volumeNames)));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> constructSnapshotOptions(List<BlockSnapshotRestRep> snapshots) {
        List<AssetOption> options = Lists.newArrayList();
        for (BlockSnapshotRestRep snapshot : snapshots) {
            options.add(
                    new AssetOption(snapshot.getId(), getMessage("block.snapshot.labelNoVolume", snapshot.getName(), snapshot.getWwn())));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> constructSnapshotWithSnapshotSessionOptions(List<BlockSnapshotRestRep> snapshots,
            List<BlockSnapshotSessionRestRep> snapshotSessions) {
        List<AssetOption> options = Lists.newArrayList();
        // Create a map of linked target URIs to snapshot session names for convenience when creating
        // the option labels.
        Map<URI, String> linkedSnapshotToSnapshotSessionMap = new HashMap<URI, String>();
        for (BlockSnapshotSessionRestRep snapshotSession : snapshotSessions) {
            for (RelatedResourceRep linkedTarget : snapshotSession.getLinkedTarget()) {
                linkedSnapshotToSnapshotSessionMap.put(linkedTarget.getId(), snapshotSession.getName());
            }
        }
        for (BlockSnapshotRestRep snapshot : snapshots) {
            // Ignore RP Bookmarks for snapshot session options
            boolean isRPSnapshot = (snapshot.getTechnologyType() != null
                    && snapshot.getTechnologyType().equalsIgnoreCase(RECOVERPOINT_BOOKMARK_SNAPSHOT_TYPE_VALUE));
            boolean validSnapshotOption = snapshot.getTechnologyType() == null || !isRPSnapshot;
            if (validSnapshotOption) {
                options.add(new AssetOption(snapshot.getId(), getBlockSnapshotLinkedLabel(snapshot, linkedSnapshotToSnapshotSessionMap)));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected List<AssetOption> constructSnapshotSessionOptions(ViPRCoreClient client, URI project,
            List<BlockSnapshotSessionRestRep> snapshotSessions) {
        List<AssetOption> options = Lists.newArrayList();
        Map<URI, VolumeRestRep> volumeNames = getProjectVolumeNames(client, project);
        for (BlockSnapshotSessionRestRep snapshotSession : snapshotSessions) {
            options.add(new AssetOption(snapshotSession.getId(), getBlockObjectLabel(client, snapshotSession, volumeNames)));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    private List<AssetOption>
            getContinuousCopyOptionsForProject(AssetOptionsContext ctx, URI project, URI volumeId,
                    ResourceFilter<BlockMirrorRestRep> filter) {
        ViPRCoreClient client = api(ctx);

        List<BlockMirrorRestRep> copies = client.blockVolumes().getContinuousCopies(volumeId, filter);
        return constructCopiesOptions(client, project, copies);
    }

    protected List<AssetOption> constructCopiesOptions(ViPRCoreClient client, URI project, List<BlockMirrorRestRep> copies) {
        List<AssetOption> options = Lists.newArrayList();
        for (BlockMirrorRestRep copy : copies) {
            options.add(new AssetOption(copy.getId(), getBlockObjectLabel(client, copy, null)));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    /**
     * Gets the set of volume IDs associated with the given exports.
     * 
     * @param exports
     *            the export groups.
     * @return the set of volume IDs.
     */
    protected static Set<URI> getBlockVolumeIdsForExports(List<ExportGroupRestRep> exports) {
        Set<URI> ids = Sets.newHashSet();
        for (ExportGroupRestRep export : exports) {
            if (export.getVolumes() != null) {
                for (ExportBlockParam volume : export.getVolumes()) {
                    ids.add(volume.getId());
                }
            }
        }
        return ids;
    }

    /**
     * Gets the set of volume IDs for volumes in the given project that are exported to the given host/cluster
     * 
     * @param client An instance of the ViPRCoreClient
     * @param projectId The ViPR ID of the project
     * @param hostOrClusterId The ViPR ID of the host/cluster
     * 
     * @return The set of Volume IDs
     */
    protected static Set<URI> getExportedVolumes(ViPRCoreClient client, URI projectId, URI hostOrClusterId, URI virtualArrayId) {
        // determine host and cluster id
        URI hostId = BlockStorageUtils.isHost(hostOrClusterId) ? hostOrClusterId : null;
        URI clusterId = BlockStorageUtils.isCluster(hostOrClusterId) ? hostOrClusterId : null;

        // get a list of all the exports in this project that expose resources to this host/cluster
        ResourceFilter<ExportGroupRestRep> filter;
        if (virtualArrayId == null) {
            filter = new ExportHostOrClusterFilter(hostId, clusterId);
        } else {
            filter = new ExportHostOrClusterFilter(hostId, clusterId).and(new ExportVirtualArrayFilter(virtualArrayId));
        }
        List<ExportGroupRestRep> exports = client.blockExports().findByProject(projectId, filter);

        return getBlockVolumeIdsForExports(exports);
    }

    /**
     * Gets the value of the specified tag from the given volumes.
     * 
     * @param tagName
     *            the tag name.
     * @param volumes
     *            the volumes.
     * @return the tag values.
     */
    public static Set<String> getTagValues(String tagName, Collection<? extends BlockObjectRestRep> volumes) {
        Set<String> values = Sets.newHashSet();
        for (BlockObjectRestRep volume : volumes) {
            String value = MachineTagUtils.getBlockVolumeTag(volume, tagName);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Returns a list of virtual pools where volume can be moved
     */
    protected List<BlockVirtualPoolRestRep> listTargetVirtualPools(AssetOptionsContext ctx, URI volumeId,
            ResourceFilter<BlockVirtualPoolRestRep> filter) {
        ViPRCoreClient client = api(ctx);
        List<VirtualPoolChangeRep> vpoolChanges = client.blockVolumes().listVirtualPoolChangeCandidates(volumeId);

        List<URI> vpoolIds = Lists.newArrayList();
        for (VirtualPoolChangeRep change : vpoolChanges) {
            if (change.getAllowed()) {
                vpoolIds.add(change.getId());
            }
        }
        return client.blockVpools().getByIds(vpoolIds, filter);
    }

    /**
     * Gets the volume details for a collection of volumes.
     * 
     * @param client the bourne client.
     * @param volumes the collection of volumes.
     * @return the volume details.
     */
    protected static List<VolumeDetail> getVolumeDetails(ViPRCoreClient client, Collection<VolumeRestRep> volumes) {
        Set<URI> blockVirtualPoolIds = getBlockVirtualPoolIdsForVolumes(volumes);
        Map<URI, BlockVirtualPoolRestRep> virtualPoolMap = getBlockVirtualPools(client, blockVirtualPoolIds);

        List<VolumeDetail> details = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            BlockVirtualPoolRestRep virtualPool = virtualPoolMap.get(volume.getVirtualPool().getId());
            details.add(new VolumeDetail(volume, virtualPool));
        }
        return details;
    }

    /**
     * Gets the unique set of BlockVirtualPool IDs for the given volumes.
     * 
     * @param volumes the volumes.
     * @return the block virtual pool IDs.
     */
    protected static Set<URI> getBlockVirtualPoolIdsForVolumes(Collection<VolumeRestRep> volumes) {
        Set<URI> ids = Sets.newHashSet();
        for (VolumeRestRep volume : volumes) {
            ids.add(volume.getVirtualPool().getId());
        }
        return ids;
    }

    /**
     * Gets all BlockVirtualPools mapped by ID.
     * 
     * @param client the ViPR client instance.
     * @param ids the IDs.
     * @return the mapping of ID->BlockVirtualPoolRestRep
     */
    public static Map<URI, BlockVirtualPoolRestRep> getBlockVirtualPools(ViPRCoreClient client, Collection<URI> ids) {
        Map<URI, BlockVirtualPoolRestRep> virtualPools = Maps.newHashMap();
        for (BlockVirtualPoolRestRep virtualPool : client.blockVpools().getByIds(ids)) {
            virtualPools.put(virtualPool.getId(), virtualPool);
        }
        return virtualPools;
    }

    /**
     * Gets all BlockVirtualPools mapped by ID.
     * 
     * @param client the ViPR client instance.
     * @param volumes the volumes for which we need the VPool information.
     * @return the mapping of ID->BlockVirtualPoolRestRep
     */
    public static Map<URI, BlockVirtualPoolRestRep> getVpoolsForVolumes(ViPRCoreClient client, List<VolumeRestRep> volumes) {
        // collect the id of each vpool used by a volume in the given list
        Set<URI> vpoolIds = Sets.newHashSet();
        for (VolumeRestRep volume : volumes) {
            vpoolIds.add(volume.getVirtualPool().getId());
        }

        // build up a map of URI to VPool
        return getBlockVirtualPools(client, vpoolIds);
    }

    /**
     * Gets all {@link VolumeRestRep}s that are either in the target VArray or use the target VArray for protection
     * 
     * @param client the ViPR client instance.
     * @param targetVArrayId the target VArray ID.
     * @param volumes the volumes we are concerned with. (These should be VPlex volumes)
     * @return List of {@link VolumeRestRep}s that are VPlex volumes that are in the target VArray
     */
    public static List<BlockObjectRestRep> getVPlexVolumesInTargetVArray(ViPRCoreClient client, URI targetVArrayId,
            List<VolumeRestRep> volumes) {
        // collect vpools used by these volumes
        Map<URI, BlockVirtualPoolRestRep> vpools = getVpoolsForVolumes(client, volumes);

        // sift through the volumes to find ones with the correct VArray
        List<BlockObjectRestRep> acceptedVolumes = Lists.newArrayList();
        for (VolumeRestRep volume : volumes) {
            if (volume.getVirtualArray().getId().equals(targetVArrayId)) {
                addVolume(acceptedVolumes, volume);
            } else {
                // if this volume's HA type is 'distributed' and its distributed VArray matches the target VArray we can accept the volume
                URI vpoolId = volume.getVirtualPool().getId();
                BlockVirtualPoolRestRep volumeVpool = vpools.get(vpoolId);
                if (volumeVpool != null && isVpoolProtectedByVarray(volumeVpool, targetVArrayId)) {
                    addVolume(acceptedVolumes, volume);
                }
            }
        }

        return acceptedVolumes;
    }

    /**
     * Class for holding all volume detail information, currently the volume and virtual pool.
     * 
     * @author jonnymiller
     */
    public static class VolumeDetail {
        public VolumeRestRep volume;
        public BlockVirtualPoolRestRep vpool;

        public VolumeDetail(VolumeRestRep volume) {
            this.volume = volume;
        }

        public VolumeDetail(VolumeRestRep volume, BlockVirtualPoolRestRep vpool) {
            this.volume = volume;
            this.vpool = vpool;
        }
    }

    public static List<AssetOption> createVirtualPoolResourceOptions(Collection<? extends VirtualPoolCommonRestRep> virtualPools) {

        List<AssetOption> options = Lists.newArrayList();

        for (VirtualPoolCommonRestRep virtualPool : virtualPools) {
            options.add(createVirtualPoolResourceOption(virtualPool));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    public static AssetOption createVirtualPoolResourceOption(VirtualPoolCommonRestRep virtualPool) {
        boolean hasPools = (virtualPool.getUseMatchedPools() && !CollectionUtils.isEmpty(virtualPool.getMatchedStoragePools()) ||
                (!virtualPool.getUseMatchedPools() && !CollectionUtils.isEmpty(virtualPool.getAssignedStoragePools())));

        String label = virtualPool.getName();
        if (!hasPools) {
            label = getMessage("block.virtualPool.noStorage", virtualPool.getName());
        }

        return new AssetOption(virtualPool.getId(), label);
    }

    protected List<AssetOption> createVpoolChangeOptions(String vpoolChangeOperation, List<VirtualPoolChangeRep> vpoolChanges) {
        List<AssetOption> options = Lists.newArrayList();
        List<AssetOption> available = Lists.newArrayList();
        List<AssetOption> wrongOperation = Lists.newArrayList();
        List<AssetOption> notAllowed = Lists.newArrayList();
        for (VirtualPoolChangeRep vpoolChange : vpoolChanges) {
            if (vpoolChange.getAllowed() && vpoolChange.getAllowedChangeOperations() != null) {
                for (StringHashMapEntry allowedChangeOperation : vpoolChange.getAllowedChangeOperations()) {
                    String operation = allowedChangeOperation.getName();
                    boolean isCorrectOperation = StringUtils.isNotBlank(operation) &&
                            operation.equalsIgnoreCase(vpoolChangeOperation);
                    if (isCorrectOperation) {
                        available.add(new AssetOption(vpoolChange.getId(), vpoolChange.getName()));
                    } else {
                        wrongOperation.add(new AssetOption(EMPTY_STRING,
                                getMessage("block.virtualPool.vpoolChangeOperation", vpoolChange.getName(),
                                        getAllowedChangeOperationNames(vpoolChange.getAllowedChangeOperations()))));
                    }
                }
            } else {
                notAllowed.add(new AssetOption(EMPTY_STRING,
                        getMessage("block.virtualPool.vpoolChangeReason", vpoolChange.getName(), vpoolChange.getNotAllowedReason())));
            }
        }
        options.addAll(available);
        options.addAll(wrongOperation);
        options.addAll(notAllowed);
        return options;
    }

    private String getAllowedChangeOperationNames(List<StringHashMapEntry> allowedChangeOperations) {
        List<String> names = Lists.newArrayList();
        for (StringHashMapEntry allowedChangeOperation : allowedChangeOperations) {
            names.add(getMessage("virtualPoolChange.operation." + allowedChangeOperation.getName()));
        }
        return StringUtils.join(names, ',');
    }

    protected List<BlockObjectRestRep>
            getProjectBlockResources(ViPRCoreClient client, URI hostOrClusterId, URI project, boolean onlyMounted) {
        List<BlockObjectRestRep> blockObjects = Lists.newArrayList();

        // the list of host ids that the volume could be mounted to
        List<URI> hostIds = getHostIds(client, hostOrClusterId, onlyMounted);

        // get a list of all volumes and snapshots in the project
        List<VolumeRestRep> projectVolumes = client.blockVolumes().findByProject(project);
        List<BlockSnapshotRestRep> projectSnapshots = client.blockSnapshots().findByProject(project);

        // cycle through every volume in the project and add the volume and its snapshots
        for (VolumeRestRep volume : projectVolumes) {
            boolean isMounted = isMounted(hostIds, volume);
            if ((onlyMounted && isMounted) || (!onlyMounted && !isMounted)) {
                addVolume(blockObjects, volume, projectSnapshots);
            }
        }

        // remaining snapshots can be added now. We don't know their parent volume but we need them in the list anyway
        blockObjects.addAll(projectSnapshots);

        return blockObjects;
    }

    protected List<VolumeRestRep> getProjectBlockVolumes(ViPRCoreClient client, URI hostOrClusterId, URI project, boolean onlyMounted) {
        List<VolumeRestRep> volumes = Lists.newArrayList();

        // the list of host ids that the volume could be mounted to
        List<URI> hostIds = getHostIds(client, hostOrClusterId, onlyMounted);

        // get a list of all volumes and snapshots in the project
        List<VolumeRestRep> projectVolumes = client.blockVolumes().findByProject(project);

        // cycle through every volume in the project and add those that match
        for (VolumeRestRep volume : projectVolumes) {
            boolean isMounted = isMounted(hostIds, volume);
            if ((onlyMounted && isMounted) || (!onlyMounted && !isMounted)) {
                volumes.add(volume);
            }
        }
        return volumes;
    }

    protected List<URI> getHostIds(ViPRCoreClient client, URI hostOrClusterId,
            boolean onlyMounted) {
        List<URI> hostIds = Lists.newArrayList();

        if (onlyMounted) {
            hostIds.add(hostOrClusterId);
        } else {
            hostIds.addAll(HostProvider.getHostIds(client, hostOrClusterId));

            // Add Cluster ID to the end of the host ID's
            if (BlockStorageUtils.isCluster(hostOrClusterId)) {
                hostIds.add(hostOrClusterId);
            } else {
                HostRestRep host = client.hosts().get(hostOrClusterId);
                if (host.getCluster() != null) {
                    hostIds.add(host.getCluster().getId());
                }
            }
        }
        return hostIds;
    }

    protected static void addVolume(List<BlockObjectRestRep> blockObjects, VolumeRestRep volume) {
        addVolume(blockObjects, volume, null);
    }

    /**
     * Add the volume and its snapshots to the 'blockObjects' list.
     * 
     * When the method completes the snapshots that have been added to the blockObjects list will be removed from the snapshots list.
     */
    protected static void addVolume(List<BlockObjectRestRep> blockObjects, VolumeRestRep volume, List<BlockSnapshotRestRep> snapshots) {
        blockObjects.add(volume);
        if (CollectionUtils.isNotEmpty(snapshots)) {
            // use an iterator so we can do proper removes
            Iterator<BlockSnapshotRestRep> snapshotIter = snapshots.iterator();
            while (snapshotIter.hasNext()) {
                BlockSnapshotRestRep snap = snapshotIter.next();
                if (ResourceUtils.idEquals(snap.getParent(), volume)) {
                    blockObjects.add(snap);
                    snapshotIter.remove();
                }
            }
        }
    }

    /**
     * SRDF filter for block objects.
     */
    private static class BlockObjectSRDFTargetFilter extends DefaultResourceFilter<BlockObjectRestRep> {
        private final SRDFTargetFilter filter = new SRDFTargetFilter();

        @Override
        public boolean accept(BlockObjectRestRep item) {
            if (item instanceof VolumeRestRep) {
                return filter.accept((VolumeRestRep) item);
            }
            return false;
        }
    }

    /**
     * VMFS Datastore filter for block objects.
     */
    private static class BlockObjectVMFSDatastoreFilter extends DefaultResourceFilter<BlockObjectRestRep> {
        @Override
        public boolean accept(BlockObjectRestRep blockObject) {
            return BlockStorageUtils.isVolumeVMFSDatastore(blockObject);
        }
    }

    /**
     * VMFS Datastore filter for block volumes.
     */
    private static class BlockVolumeVMFSDatastoreFilter extends DefaultResourceFilter<VolumeRestRep> {
        @Override
        public boolean accept(VolumeRestRep volume) {
            return BlockStorageUtils.isVolumeVMFSDatastore(volume);
        }
    }

    /**
     * Boot volume filter for block objects.
     */
    private static class BlockObjectBootVolumeFilter extends DefaultResourceFilter<BlockObjectRestRep> {
        @Override
        public boolean accept(BlockObjectRestRep blockObject) {
            return BlockStorageUtils.isVolumeBootVolume(blockObject);
        }
    }

    /**
     * MountPoint filter for block objects.
     */
    private static class BlockObjectMountPointFilter extends DefaultResourceFilter<BlockObjectRestRep> {
        @Override
        public boolean accept(BlockObjectRestRep blockObject) {
            return BlockStorageUtils.isVolumeMounted(blockObject);
        }
    }

    /**
     * MountPoint filter for block volumes.
     */
    private static class BlockVolumeMountPointFilter extends DefaultResourceFilter<VolumeRestRep> {
        @Override
        public boolean accept(VolumeRestRep volume) {
            return BlockStorageUtils.isVolumeMounted(volume);
        }
    }

    /**
     * Check if volume is an IBM XIV volume
     *
     * @param vol VolumeRestRep instance to be checked.
     * @return true if the volume is an IBM XIV volume, false otherwise
     */
    private boolean isIBMXIVVolume(VolumeRestRep vol) {
        return vol != null && IBMXIV_SYSTEM_TYPE.equals(vol.getSystemType());
    }

    /**
     * Find a single ExportGroup REST response using the passed in arguments for host/cluster.
     * 
     * @param hostOrClusterId URI for host or cluster
     * @param projectId URI for the project
     * @param varrayId URI for the varray to check
     * @param client ViPR core client
     * @return The found EG REST response or null otherwise
     */
    private ExportGroupRestRep findExportGroup(URI hostOrClusterId, URI projectId, URI varrayId, ViPRCoreClient client) {
        List<ExportGroupRestRep> exports = findExportGroups(hostOrClusterId, projectId, varrayId, client);
        return (exports.isEmpty() ? null : exports.get(0));
    }

    /**
     * Find all ExportGroup REST responses using the passed in arguments for host/cluster.
     * 
     * @param hostOrClusterId URI for host or cluster
     * @param projectId URI for the project
     * @param varrayId URI for the varray to check
     * @param client ViPR core client
     * @return List found EG REST response or empty list otherwise
     */
    private List<ExportGroupRestRep> findExportGroups(URI hostOrClusterId, URI projectId, URI varrayId, ViPRCoreClient client) {
        List<ExportGroupRestRep> exports = new ArrayList<ExportGroupRestRep>();
        if (BlockStorageUtils.isHost(hostOrClusterId)) {
            exports = client.blockExports().findByHost(hostOrClusterId, projectId, varrayId);
        } else {
            exports = client.blockExports().findByCluster(hostOrClusterId, projectId, varrayId);
        }
        return exports;
    }

    private List<AssetOption> createPortGroupChangeOptions(List<StoragePortGroupChangeRep> portGroups) {
        List<AssetOption> options = Lists.newArrayList();
        List<AssetOption> valid = Lists.newArrayList();
        List<AssetOption> invalid = Lists.newArrayList();
        for (StoragePortGroupChangeRep group : portGroups) {
            String label;
            if (group.getAllowed()) {
                String portMetric = (group.getPortMetric() != null) ? String.valueOf(Math.round(group.getPortMetric() * 100 / 100)) + "%"
                        : "N/A";
                String volumeCount = (group.getVolumeCount() != null) ? String.valueOf(group.getVolumeCount()) : "N/A";
                String nGuid = group.getNativeGuid();
                String nativeGuid = EMPTY_STRING;
                try {
                    // Remove the PG name from the native GUID.
                    // Ex:
                    // From -> SYMMETRIX+000196801518+PORTGROUP+lglw7136_pg
                    // To -> SYMMETRIX+000196801518
                    nativeGuid = nGuid.replaceAll("\\+PORTGROUP.*$", "");
                } catch (Exception e) {
                    nativeGuid = nGuid;
                    warn("Issue encountered parsing native guid %s, exception: %s", nativeGuid, e.getMessage());
                }
                label = getMessage("exportPortGroup.portGroups", group.getName(), nativeGuid, portMetric, volumeCount);
                valid.add(new AssetOption(group.getId(), label));

            } else {
                label = getMessage("exportPortGroup.portGroups.portGroupChangeReason", group.getName(), group.getNotAllowedReason());
                invalid.add(new AssetOption(EMPTY_STRING, label));
            }
        }
        options.addAll(valid);
        options.addAll(invalid);

        return options;
    }
}
