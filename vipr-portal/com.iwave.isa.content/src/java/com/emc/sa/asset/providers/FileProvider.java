/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.VirtualPoolProtocolFilter;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Component
@AssetNamespace("vipr")
public class FileProvider extends BaseAssetOptionsProvider {
    public static final String CIFS = "CIFS";
    public static final String NFS = "NFS";
    public static final String NFSv4 = "NFSv4";
    public static final String EXPORTED_TYPE = "exported";
    public static final String UNEXPORTED_TYPE = "unexported";

    @Asset("fileNfsVirtualPool")
    @AssetDependencies("fileVirtualArray")
    public List<AssetOption> getFileNfsVirtualPools(AssetOptionsContext ctx, URI virtualArray) {
        return getFileVirtualPools(ctx, virtualArray, NFS, NFSv4);
    }

    @Asset("fileNfsVirtualPool")
    public List<AssetOption> getFileNfsVirtualPools(AssetOptionsContext ctx) {
        return getFileVirtualPools(ctx, null, NFS, NFSv4);
    }

    @Asset("fileCifsVirtualPool")
    public List<AssetOption> getFileCifsVirtualPools(AssetOptionsContext ctx) {
        return getFileVirtualPools(ctx, null, CIFS);
    }

    @Asset("fileCifsVirtualPool")
    @AssetDependencies("fileVirtualArray")
    public List<AssetOption> getFileCifsVirtualPools(AssetOptionsContext ctx, URI virtualArray) {
        return getFileVirtualPools(ctx, virtualArray, CIFS);
    }

    @Asset("fileVirtualPool")
    @AssetDependencies("fileVirtualArray")
    public List<AssetOption> getFileVirtualPools(AssetOptionsContext ctx, URI virtualArray) {
        return getFileVirtualPools(ctx, virtualArray, CIFS, NFS, NFSv4);
    }

    @Asset("fileVirtualPool")
    public List<AssetOption> getFileVirtualPools(AssetOptionsContext ctx) {
        return getFileVirtualPools(ctx, null, CIFS, NFS, NFSv4);
    }

    private List<AssetOption> getFileVirtualPools(AssetOptionsContext ctx, URI virtualArray, String... protocols) {
        Collection<FileVirtualPoolRestRep> virtualPools;
        if (virtualArray == null) {
            virtualPools = api(ctx).fileVpools().getAll();
        }
        else {
            virtualPools = api(ctx).fileVpools().getByVirtualArray(virtualArray,
                    new VirtualPoolProtocolFilter<FileVirtualPoolRestRep>(protocols));
        }
        List<AssetOption> options = createBaseResourceOptions(virtualPools);
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileFilesystem")
    @AssetDependencies("project")
    public List<AssetOption> getFilesystems(AssetOptionsContext ctx, URI project) {
        return createFilesystemOptions(api(ctx).fileSystems().findByProject(project));
    }

    @Asset("fileNFSFilesystem")
    @AssetDependencies("project")
    public List<AssetOption> getNFSFilesystems(AssetOptionsContext ctx, URI project) {
        return createFilesystemOptions(api(ctx).fileSystems().findByProject(project), new NFSFilesytemsPredicate());
    }

    @Asset("fileCIFSFilesystem")
    @AssetDependencies("project")
    public List<AssetOption> getCIFSFilesystems(AssetOptionsContext ctx, URI project) {
        return createFilesystemOptions(api(ctx).fileSystems().findByProject(project), new CIFSFilesytemsPredicate());
    }

    @Asset("fileSnapshotFilesystem")
    @AssetDependencies({ "project" })
    public List<AssetOption> getSnapshotFilesystems(AssetOptionsContext ctx, URI project) {
        ViPRCoreClient client = api(ctx);
        Collection<FileShareRestRep> filesystems = client.fileSystems().findByProject(project);
        Map<URI, FileVirtualPoolRestRep> pools = getFileVirtualPools(client, filesystems);
        return createFilesystemOptions(filesystems, new SnapableFilesystemsPredicate(pools));
    }

    @Asset("fileUnmountedFilesystem")
    @AssetDependencies("project")
    public List<AssetOption> getUnmountedFilesystems(AssetOptionsContext ctx, URI project) {
        return createFilesystemOptions(api(ctx).fileSystems().findByProject(project), new UnmountedFilesytemsPredicate());
    }

    @Asset("fileSnapshot")
    @AssetDependencies("fileFilesystem")
    public List<AssetOption> getFileSnapshots(AssetOptionsContext ctx, URI filesystem) {
        return createBaseResourceOptions(api(ctx).fileSnapshots().getByFileSystem(filesystem));
    }

    @Asset("quotaDirectory")
    @AssetDependencies("fileFilesystem")
    public List<AssetOption> getQuotaDirectories(AssetOptionsContext ctx, URI filesystem) {
        return createNamedResourceOptions(api(ctx).quotaDirectories().listByFileSystem(filesystem));
    }

    @Asset("fileSnapshot")
    @AssetDependencies("fileCIFSFilesystem")
    public List<AssetOption> getCIFSFileSnapshots(AssetOptionsContext ctx, URI filesystem) {
        return createBaseResourceOptions(api(ctx).fileSnapshots().getByFileSystem(filesystem));
    }

    @Asset("fileSnapshot")
    @AssetDependencies("fileNFSFilesystem")
    public List<AssetOption> getNFSFileSnapshots(AssetOptionsContext ctx, URI filesystem) {
        return createBaseResourceOptions(api(ctx).fileSnapshots().getByFileSystem(filesystem));
    }

    @Asset("fileShares")
    @AssetDependencies("fileFilesystem")
    public List<AssetOption> getFileShares(AssetOptionsContext ctx, URI fileFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        for (SmbShareResponse share : listFileShares(ctx, fileFilesystem)) {
            String label = String.format("%s [%s]", share.getShareName(), share.getMountPoint());
            options.add(new AssetOption(share.getShareName(), label));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileShares")
    @AssetDependencies("fileNFSFilesystem")
    public List<AssetOption> getNFSFileShares(AssetOptionsContext ctx, URI fileFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        for (SmbShareResponse share : listFileShares(ctx, fileFilesystem)) {
            String label = String.format("%s [%s]", share.getShareName(), share.getMountPoint());
            options.add(new AssetOption(share.getShareName(), label));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileShares")
    @AssetDependencies("fileCIFSFilesystem")
    public List<AssetOption> getCIFSFileShares(AssetOptionsContext ctx, URI fileFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        for (SmbShareResponse share : listFileShares(ctx, fileFilesystem)) {
            String label = String.format("%s [%s]", share.getShareName(), share.getMountPoint());
            options.add(new AssetOption(share.getShareName(), label));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileSnapshotShares")
    @AssetDependencies("fileSnapshot")
    public List<AssetOption> getFileSnapshotShares(AssetOptionsContext ctx, URI fileSnapshot) {
        List<AssetOption> options = Lists.newArrayList();
        for (SmbShareResponse share : api(ctx).fileSnapshots().getShares(fileSnapshot)) {
            String label = String.format("%s [%s]", share.getShareName(), share.getMountPoint());
            options.add(new AssetOption(share.getShareName(), label));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileExports")
    @AssetDependencies("fileFilesystem")
    public List<AssetOption> getFileExports(AssetOptionsContext ctx, URI fileFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileSystemExportParam export : listFileExports(ctx, fileFilesystem)) {
            options.add(new AssetOption(export.getMountPoint(), export.getMountPoint()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileExports")
    @AssetDependencies("fileNFSFilesystem")
    public List<AssetOption> getNFSFileExports(AssetOptionsContext ctx, URI fileFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileSystemExportParam export : listFileExports(ctx, fileFilesystem)) {
            options.add(new AssetOption(export.getMountPoint(), export.getMountPoint()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileExports")
    @AssetDependencies("fileCIFSFilesystem")
    public List<AssetOption> getCIFSFileExports(AssetOptionsContext ctx, URI fileFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileSystemExportParam export : listFileExports(ctx, fileFilesystem)) {
            options.add(new AssetOption(export.getMountPoint(), export.getMountPoint()));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("securityStyle")
    public List<AssetOption> getSecurityStyles(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (QuotaDirectory.SecurityStyles style : QuotaDirectory.SecurityStyles.values()) {
            if (!style.name().equals("parent")) {
                options.add(new AssetOption(style.name(), style.name().toUpperCase()));
            }
        }
        return options;
    }

    @Asset("fileExportsWithRootPermissions")
    @AssetDependencies("fileUnmountedFilesystem")
    public List<AssetOption> getFileExportsWithRootPermissions(AssetOptionsContext ctx, URI fileFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileSystemExportParam export : listFileExports(ctx, fileFilesystem)) {
            if (export.getPermissions().equalsIgnoreCase("root")) {
                options.add(new AssetOption(export.getMountPoint(), export.getMountPoint()));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    private List<SmbShareResponse> listFileShares(AssetOptionsContext ctx, URI filesystem) {
        return api(ctx).fileSystems().getShares(filesystem);
    }

    private List<FileSystemExportParam> listFileExports(AssetOptionsContext ctx, URI filesystem) {
        return api(ctx).fileSystems().getExports(filesystem);
    }

    /**
     * convenience method for creating filesystem options using no predicate
     */
    private List<AssetOption> createFilesystemOptions(Collection<FileShareRestRep> filesystems) {
        return createFilesystemOptions(filesystems, null);
    }

    /**
     * Create filesystem options from the list of filesystems, filtering using the given predicate
     * 
     * @return The list of AssetOptions
     */
    protected List<AssetOption> createFilesystemOptions(Collection<FileShareRestRep> filesystems, Predicate predicate) {
        CollectionUtils.filter(filesystems, predicate);
        List<AssetOption> options = Lists.newArrayList();
        for (FileShareRestRep fs : filesystems) {
            options.add(new AssetOption(fs.getId(), getLabel(fs)));
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    private Map<URI, FileVirtualPoolRestRep> getFileVirtualPools(ViPRCoreClient client, Collection<FileShareRestRep> filesystems) {
        Set<URI> fileVirtualPoolIds = getFileVirtualPoolIdsForFilesystems(filesystems);
        return getFileVirtualPools(client, fileVirtualPoolIds);
    }

    public static Map<URI, FileVirtualPoolRestRep> getFileVirtualPools(ViPRCoreClient client, Set<URI> ids) {
        Map<URI, FileVirtualPoolRestRep> virtualPools = Maps.newHashMap();
        for (FileVirtualPoolRestRep virtualPool : client.fileVpools().getByIds(ids)) {
            virtualPools.put(virtualPool.getId(), virtualPool);
        }
        return virtualPools;
    }

    private static Set<URI> getFileVirtualPoolIdsForFilesystems(Collection<FileShareRestRep> filesystems) {
        Set<URI> ids = Sets.newHashSet();
        for (FileShareRestRep filesystem : filesystems) {
            ids.add(filesystem.getVirtualPool().getId());
        }
        return ids;
    }

    /**
     * Predicate for filtering NFS filesystems
     */
    private class NFSFilesytemsPredicate implements Predicate {

        @Override
        public boolean evaluate(Object object) {
            FileShareRestRep filesystem = getFilesystem(object);
            return filesystem.getProtocols().contains(NFS)
                    || filesystem.getProtocols().contains(NFSv4);
        }

    }

    /**
     * Predicate for filtering NFS filesystems
     */
    private class CIFSFilesytemsPredicate implements Predicate {

        @Override
        public boolean evaluate(Object object) {
            FileShareRestRep filesystem = getFilesystem(object);
            return filesystem.getProtocols().contains(CIFS);
        }

    }

    /**
     * Predicate for filtering out mounted filesystems
     */
    private class UnmountedFilesytemsPredicate implements Predicate {

        @Override
        public boolean evaluate(Object object) {
            FileShareRestRep filesystem = getFilesystem(object);
            return !MachineTagUtils.hasDatastores(filesystem);
        }

    }

    /**
     * Predicate for filtering out un-snapshot-able filesystems.
     */
    private class SnapableFilesystemsPredicate implements Predicate {

        private Map<URI, FileVirtualPoolRestRep> pools;

        public SnapableFilesystemsPredicate(Map<URI, FileVirtualPoolRestRep> pools) {
            this.pools = pools;
        }

        @Override
        public boolean evaluate(Object object) {
            FileShareRestRep filesystem = getFilesystem(object);
            URI vpoolId = filesystem.getVirtualPool().getId();
            FileVirtualPoolRestRep vpool = pools.get(vpoolId);
            return isLocalSnapshotSupported(vpool) || isRemoteSnapshotSupported(filesystem);
        }

        protected boolean isLocalSnapshotSupported(FileVirtualPoolRestRep virtualPool) {
            return (virtualPool.getProtection() != null) &&
                    (virtualPool.getProtection().getSnapshots() != null) &&
                    (virtualPool.getProtection().getSnapshots().getMaxSnapshots() > 0);
        }

        protected boolean isRemoteSnapshotSupported(FileShareRestRep filesystem) {
            return filesystem.getDataProtection() != null;
        }

    }

    private FileShareRestRep getFilesystem(Object object) {
        if (object instanceof FileShareRestRep) {
            return (FileShareRestRep) object;
        }
        throw new IllegalArgumentException(getMessage("file.exception.fileShareRestRep"));
    }

    public String getLabel(FileShareRestRep fileSystem) {
        return getMessage("file.volume", fileSystem.getName(), fileSystem.getCapacity());
    }

    @Asset("fileSMBPermissionType")
    public List<AssetOption> getSnapshotPermissionType(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileSMBShare.PermissionType type : FileSMBShare.PermissionType.values()) {
            options.add(newAssetOption(type.name(), String.format("file.SMB.permissionType.%s", type.name())));
        }
        return options;
    }

    @Asset("fileShareACLPermission")
    public List<AssetOption> getFileShareACLPermissions(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (CifsShareACLUpdateParams.SharePermission perm : CifsShareACLUpdateParams.SharePermission.values()) {
            options.add(newAssetOption(perm.name(), String.format("file.ACL.permission.%s", perm.name())));
        }
        return options;
    }

    @Asset("fileSnapshotACLPermission")
    public List<AssetOption> getFileSnapshotACLPermissions(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        CifsShareACLUpdateParams.SharePermission readPermission = CifsShareACLUpdateParams.SharePermission.READ;
        options.add(newAssetOption(readPermission.name(), String.format("file.ACL.permission.%s", readPermission.name())));
        return options;
    }

    @Asset("fileSMBPermission")
    public List<AssetOption> getSnapshotPermissions(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileSMBShare.Permission perm : FileSMBShare.Permission.values()) {
            options.add(newAssetOption(perm.name(), String.format("file.SMB.permission.%s", perm.name())));
        }
        return options;
    }

    @Asset("fileNFSSecurity")
    public List<AssetOption> getNFSSecurityTypes(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileShareExport.SecurityTypes securityType : FileShareExport.SecurityTypes.values()) {
            options.add(newAssetOption(securityType.name(), String.format("file.NFS.security.%s", securityType.name())));
        }
        return options;
    }

    @Asset("fileNFSPermission")
    public List<AssetOption> getNFSPermissions(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (FileShareExport.Permissions perm : FileShareExport.Permissions.values()) {
            options.add(newAssetOption(perm.name(), String.format("file.NFS.permission.%s", perm.name())));
        }
        return options;
    }

    @Asset("fileTrueFalse")
    public List<AssetOption> getTrueFalseOption(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        options.add(newAssetOption("true", "boolean.true"));
        options.add(newAssetOption("false", "boolean.false"));
        return options;
    }
    
    @Asset("fileDeletionType")
    public List<AssetOption> getFileDeletionType(AssetOptionsContext ctx) {
        List<AssetOption> options = new ArrayList<>();

        options.add(newAssetOption(FileControllerConstants.DeleteTypeEnum.FULL.toString(), "file.deletion.type.full"));
        options.add(newAssetOption(FileControllerConstants.DeleteTypeEnum.VIPR_ONLY.toString(), "file.deletion.type.vipr_only"));

        return options;
    }

    @Asset("fileIngestExportType")
    public List<AssetOption> getFileIngestExportType(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        options.add(newAssetOption(EXPORTED_TYPE, "file.ingest.export.type.exported"));
        options.add(newAssetOption(UNEXPORTED_TYPE, "file.ingest.export.type.unexported"));
        return options;
    }
}
