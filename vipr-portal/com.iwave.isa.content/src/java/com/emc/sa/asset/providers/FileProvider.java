/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsUtils;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.VirtualArrayRelatedResourceRep;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileShareRestRep.FileProtectionRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.MountInfo;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.SourceTargetFileSystemsFilter;
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
            virtualPools = api(ctx).fileVpools().getByTenant(ctx.getTenant());
        } else {
            virtualPools = api(ctx).fileVpools().getByVirtualArrayAndTenant(virtualArray, ctx.getTenant(),
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

    @Asset("fileUnmountedFilesystemNoTarget")
    @AssetDependencies("project")
    public List<AssetOption> getUnmountedFilesystemsNoTarget(AssetOptionsContext ctx, URI project) {
        return createFilesystemOptions(api(ctx).fileSystems().findByProject(project), new NoTargetFilesytemsPredicate());
    }

    @Asset("fileFilesystemAssociation")
    @AssetDependencies("project")
    public List<AssetOption> getFilesystemsForAssociation(AssetOptionsContext ctx, URI project) {
        ViPRCoreClient client = api(ctx);
        List<FileShareRestRep> fileSharesforAssociation = Lists.newArrayList();

        Map<URI, Boolean> uriToBool = Maps.newHashMap();
        List<FileShareRestRep> fileShares = client.fileSystems().findByProject(project);

        for (FileShareRestRep fileShare : fileShares) {
            URI vpoolId = fileShare.getVirtualPool().getId();

            if (!uriToBool.containsKey(vpoolId)) {
                FileVirtualPoolRestRep vpool = client.fileVpools().get(vpoolId);
                uriToBool.put(vpoolId, (vpool.getProtection() != null && vpool.getProtection().getAllowFilePolicyAtFSLevel()
                        && (vpool.getProtection().getReplicationSupported() || vpool.getProtection().getScheduleSnapshots())));
            }

            if (uriToBool.get(vpoolId)) {
                fileSharesforAssociation.add(fileShare);
            }
        }

        return createFilesystemOptions(fileSharesforAssociation);
    }

    @Asset("fileFilesystemSourceArray")
    @AssetDependencies("fileFilesystemAssociation")
    public List<AssetOption> getFileFilesystemSourceArrayForAssociation(AssetOptionsContext ctx, URI fsId) {
        ViPRCoreClient client = api(ctx);
        VirtualArrayRestRep vArray = null;

        List<AssetOption> options = Lists.newArrayList();
        FileShareRestRep fsObj = client.fileSystems().get(fsId);
        if (fsObj != null) {
            vArray = client.varrays().get(fsObj.getVirtualArray().getId());
            options.add(createBaseResourceOption(vArray));
        }
        return options;
    }

    private List<FilePolicyRestRep> getAllFileSystemLevelPolicies(AssetOptionsContext ctx) {
        ViPRCoreClient client = api(ctx);
        List<FilePolicyRestRep> fileSystemPolicies = new ArrayList<FilePolicyRestRep>();
        FilePolicyListRestRep policies = client.fileProtectionPolicies().listFilePolicies();
        if (policies != null && !policies.getFilePolicies().isEmpty()) {
            for (NamedRelatedResourceRep policy : policies.getFilePolicies()) {
                FilePolicyRestRep policyRestRep = client.fileProtectionPolicies().get(policy.getId());
                if (policyRestRep != null && policyRestRep.getAppliedAt() != null
                        && policyRestRep.getAppliedAt().equalsIgnoreCase("file_system")) {
                    fileSystemPolicies.add(policyRestRep);
                }
            }
        }
        return fileSystemPolicies;
    }

    @Asset("fileFilePolicy")
    @AssetDependencies({ "project", "fileFilesystemAssociation" })
    public List<AssetOption> getFilePolicies(AssetOptionsContext ctx, URI project, URI fsId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();
        for (FilePolicyRestRep policyRestRep : getAllFileSystemLevelPolicies(ctx)) {
            options.add(new AssetOption(policyRestRep.getId(), policyRestRep.getName()));
        }
        if (options != null && !options.isEmpty()) {
            AssetOptionsUtils.sortOptionsByLabel(options);
        }
        return options;
    }

    @Asset("fileFilePolicy")
    public List<AssetOption> getFilePolicies(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();
        for (FilePolicyRestRep policyRestRep : getAllFileSystemLevelPolicies(ctx)) {
            options.add(new AssetOption(policyRestRep.getId(), policyRestRep.getName()));
        }
        if (options != null && !options.isEmpty()) {
            AssetOptionsUtils.sortOptionsByLabel(options);
        }
        return options;
    }

    @Asset("fileFilesystemWithPolicies")
    @AssetDependencies("project")
    public List<AssetOption> getFilesystemsWithPolicies(AssetOptionsContext ctx, URI project) {
        List<AssetOption> options = Lists.newArrayList();

        List<FilePolicyRestRep> fileSystemPolicies = getAllFileSystemLevelPolicies(ctx);
        List<FileShareRestRep> fileSystems = api(ctx).fileSystems().findByProject(project);

        for (FilePolicyRestRep policyRestRep : fileSystemPolicies) {
            if (policyRestRep.getAssignedResources() != null && !policyRestRep.getAssignedResources().isEmpty()) {
                for (FileShareRestRep fileSystem : fileSystems) {
                    for (NamedRelatedResourceRep resource : policyRestRep.getAssignedResources()) {
                        if (resource.getId().equals(fileSystem.getId())) {
                            options.add(new AssetOption(fileSystem.getId(),
                                    getMessage("file.fileNativeId", fileSystem.getName(), fileSystem.getNativeId())));
                            break;
                        }
                    }
                }
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileSystemPolicies")
    @AssetDependencies("fileFilesystemWithPolicies")
    public List<AssetOption> getFileSystemPolicies(AssetOptionsContext ctx, URI fsId) {
        List<AssetOption> options = Lists.newArrayList();
        List<FilePolicyRestRep> fileSystemPolicies = getAllFileSystemLevelPolicies(ctx);
        for (FilePolicyRestRep policyRestRep : fileSystemPolicies) {
            // This function also get called after order submission and we get null value for fsId
            // workaround to display policy name in oder receipt: if fsId is null then list all the policy
            if (fsId == null) {
                options.add(new AssetOption(policyRestRep.getId(), policyRestRep.getName()));
            } else if (policyRestRep.getAssignedResources() != null && !policyRestRep.getAssignedResources().isEmpty()) {
                for (NamedRelatedResourceRep resource : policyRestRep.getAssignedResources()) {
                    if (resource.getId().equals(fsId)) {
                        options.add(new AssetOption(policyRestRep.getId(), policyRestRep.getName()));
                    }
                }
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);

        return options;
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

    @Asset("fileWithContinuousCopies")
    @AssetDependencies("project")
    public List<AssetOption> getFileWithContinuousCopies(AssetOptionsContext ctx, URI project) {
        final ViPRCoreClient client = api(ctx);
        List<FileShareRestRep> fileShares = client.fileSystems().findByProject(project, new SourceTargetFileSystemsFilter() {
            @Override
            public boolean acceptId(URI id) {
                return !client.fileSystems().getFileContinuousCopies(id).isEmpty();
            }
        });
        return createFilesystemOptions(fileShares);
    }

    @Asset("fileContinuousCopies")
    @AssetDependencies("fileWithContinuousCopies")
    public List<AssetOption> getFileContinuousCopies(AssetOptionsContext ctx, URI fileId) {
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        List<NamedRelatedResourceRep> mirrors = client.fileSystems().getFileContinuousCopies(fileId);
        for (NamedRelatedResourceRep mirror : mirrors) {
            FileShareRestRep fileShare = client.fileSystems().get(mirror.getId());
            options.add(new AssetOption(fileShare.getId(), getMessage("file.fileNativeId", fileShare.getName(), fileShare.getNativeId())));
        }

        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("protectedRemoteFileSystem")
    @AssetDependencies({ "project" })
    public List<AssetOption> getProtectedFileSystems(AssetOptionsContext ctx, URI project) {
        debug("getting protected remote file system (project=%s)", project);
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        List<FileShareRestRep> fileSystems = client.fileSystems().findByProject(project);

        for (FileShareRestRep fileShare : fileSystems) {
            if (fileShare.getProtection() != null &&
                    StringUtils.equals(FileShare.PersonalityTypes.SOURCE.toString(), fileShare.getProtection().getPersonality())) {
                options.add(
                        new AssetOption(fileShare.getId(), getMessage("file.fileNativeId", fileShare.getName(), fileShare.getNativeId())));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("failoverFileTarget")
    @AssetDependencies("protectedRemoteFileSystem")
    public List<AssetOption> getFailoverFileTarget(AssetOptionsContext ctx, URI protectedFileSystem) {
        if (protectedFileSystem != null) {
            ViPRCoreClient client = api(ctx);
            List<AssetOption> options = Lists.newArrayList();

            debug("getting failoverFileTargets (protectedFileSystem=%s)", protectedFileSystem);
            FileShareRestRep file = client.fileSystems().get(protectedFileSystem);

            FileProtectionRestRep protection = file.getProtection();
            if (protection != null) {
                List<VirtualArrayRelatedResourceRep> targets = protection.getTargetFileSystems();
                for (VirtualArrayRelatedResourceRep target : targets) {
                    FileShareRestRep fileshare = client.fileSystems().get(target.getId());
                    options.add(new AssetOption(fileshare.getId(),
                            getMessage("file.fileNativeId", fileshare.getName(), fileshare.getNativeId())));
                }
            }

            AssetOptionsUtils.sortOptionsByLabel(options);
            return options;
        }

        return Lists.newArrayList();
    }

    @Asset("failbackFileTarget")
    @AssetDependencies("protectedRemoteFileSystem")
    public List<AssetOption> getFailbackFileTarget(AssetOptionsContext ctx, URI protectedFileSystem) {
        if (protectedFileSystem != null) {
            ViPRCoreClient client = api(ctx);
            List<AssetOption> options = Lists.newArrayList();

            debug("getting failbackFileTargets (protectedFileSystem=%s)", protectedFileSystem);
            FileShareRestRep file = client.fileSystems().get(protectedFileSystem);

            FileProtectionRestRep protection = file.getProtection();
            if (protection != null) {
                List<VirtualArrayRelatedResourceRep> targets = protection.getTargetFileSystems();
                for (VirtualArrayRelatedResourceRep target : targets) {
                    FileShareRestRep fileshare = client.fileSystems().get(target.getId());
                    options.add(new AssetOption(fileshare.getId(),
                            getMessage("file.fileNativeId", fileshare.getName(), fileshare.getNativeId())));
                }
            }

            AssetOptionsUtils.sortOptionsByLabel(options);
            return options;
        }

        return Lists.newArrayList();
    }

    @Asset("unprotectedFilesystem")
    @AssetDependencies({ "project" })
    public List<AssetOption> getUnprotectedFileSystems(AssetOptionsContext ctx, URI project) {
        debug("getting unprotected file system (project=%s)", project);
        ViPRCoreClient client = api(ctx);
        List<AssetOption> options = Lists.newArrayList();

        List<FileShareRestRep> fileSystems = client.fileSystems().findByProject(project);

        for (FileShareRestRep fileShare : fileSystems) {
            if (fileShare.getProtection() != null && fileShare.getProtection().getPersonality() == null) {
                options.add(
                        new AssetOption(fileShare.getId(), getMessage("file.fileNativeId", fileShare.getName(), fileShare.getNativeId())));
            }
        }

        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    protected enum FileVirtualPoolChangeOperationEnum {
        ADD_REMOTE_FILE_REPLICATION("Add Remote file replication"), ADD_LOCAL_FILE_REPLICATION("Add Local file replication");

        private String description;

        private FileVirtualPoolChangeOperationEnum(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    @Asset("fileVirtualPoolChangeOperation")
    public List<AssetOption> getVirtualPoolchangeOperations(AssetOptionsContext ctx) {
        List<AssetOption> options = Lists.newArrayList();

        for (FileVirtualPoolChangeOperationEnum operation : FileVirtualPoolChangeOperationEnum.values()) {
            options.add(newAssetOption(operation.name(), "fileVirtualPoolChange.operation." + operation.name()));
        }
        return options;
    }

    @Asset("fileTargetVirtualPool")
    @AssetDependencies({ "fileFilePolicy" })
    public List<AssetOption> getFileTargetVirtualPools(AssetOptionsContext ctx, URI filePolicy) {
        List<AssetOption> options = Lists.newArrayList();
        ViPRCoreClient client = api(ctx);
        FilePolicyRestRep policyRest = client.fileProtectionPolicies().getFilePolicy(filePolicy);
        List<FileVirtualPoolRestRep> vpoolChanges = client.fileVpools().getByTenant(ctx.getTenant());

        for (FileVirtualPoolRestRep vpool : vpoolChanges) {
            if (vpool.getProtection() != null) {
                if ((policyRest.getType().equals("file_snapshot") && vpool.getProtection().getScheduleSnapshots())
                        || (policyRest.getType().equals("file_replication") &&
                                vpool.getProtection().getReplicationSupported())) {
                    options.add(new AssetOption(vpool.getId(), vpool.getName()));
                }
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("fileSourceVirtualPool")
    @AssetDependencies({ "unprotectedFilesystem" })
    public List<AssetOption> getFileSourceVirtualPool(AssetOptionsContext ctx, URI fileSystems) {
        List<AssetOption> options = Lists.newArrayList();
        ViPRCoreClient client = api(ctx);

        URI sourceVpoolId = client.fileSystems().get(fileSystems).getVirtualPool().getId();
        FileVirtualPoolRestRep sourceVpool = client.fileVpools().get(sourceVpoolId);
        options.add(new AssetOption(sourceVpool.getId(), sourceVpool.getName()));

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
     * Predicate for filtering out mounted filesystems and target filesystems
     */
    private class NoTargetFilesytemsPredicate implements Predicate {

        @Override
        public boolean evaluate(Object object) {
            FileShareRestRep filesystem = getFilesystem(object);
            return !MachineTagUtils.hasDatastores(filesystem) &&
                    !(filesystem.getProtection() != null && filesystem.getProtection().getParentFileSystem() != null);
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
        return getMessage("file.fileNativeId.volume", fileSystem.getName(), fileSystem.getNativeId(), fileSystem.getCapacity());
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

    // For mount operation
    @Asset("fileExportedFilesystem")
    @AssetDependencies("project")
    public List<AssetOption> getExportedFilesystems(AssetOptionsContext ctx, URI project) {
        List<FileShareRestRep> filesystems = api(ctx).fileSystems().findByProject(project);
        List<FileShareRestRep> exportedFS = new ArrayList<FileShareRestRep>();
        for (FileShareRestRep fs : filesystems) {
            if (!api(ctx).fileSystems().getExports(fs.getId()).isEmpty()) {
                exportedFS.add(fs);
            }
        }
        return createFilesystemOptions(exportedFS, null);
    }

    @Asset("subDirectory")
    @AssetDependencies("fileExportedFilesystem")
    public List<AssetOption> getExportedSubdirectory(AssetOptionsContext ctx, URI fileExportedFilesystem) {
        List<AssetOption> options = Lists.newArrayList();
        List<ExportRule> exports = api(ctx).fileSystems().getExport(fileExportedFilesystem, true, null);
        for (ExportRule export : exports) {
            AssetOption tempOption;
            if (StringUtils.isEmpty(getSubDir(ctx, export))) {
                tempOption = new AssetOption("!No subdirectory", "No subdirectory");
            } else {
                tempOption = new AssetOption(getSubDir(ctx, export), getSubDir(ctx, export));
            }
            if (!options.contains(tempOption)) {
                options.add(tempOption);
            }
        }
        AssetOptionsUtils.sortOptionsByKey(options);
        return options;
    }

    @Asset("securityType")
    @AssetDependencies({ "fileExportedFilesystem", "subDirectory" })
    public List<AssetOption> getExportedSubdirectory(AssetOptionsContext ctx, URI fileExportedFilesystem, String subDirectory) {
        List<AssetOption> options = Lists.newArrayList();
        String subDir = subDirectory;
        if ("!No subdirectory".equalsIgnoreCase(subDir)) {
            subDir = null;
        }
        List<ExportRule> exports = api(ctx).fileSystems().getExport(fileExportedFilesystem, false, subDir);
        for (ExportRule rule : exports) {
            List<String> securityTypes = Arrays.asList(rule.getSecFlavor().split("\\s*,\\s*"));
            for (String sec : securityTypes) {
                options.add(new AssetOption(sec, sec));
            }
        }
        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    @Asset("mountedNFSExport")
    @AssetDependencies("linuxFileHost")
    public List<AssetOption> getNFSMountsForHost(AssetOptionsContext ctx, URI host) {
        List<AssetOption> options = Lists.newArrayList();
        List<MountInfo> hostMounts = api(ctx).fileSystems().getNfsMountsByHost(host);
        for (MountInfo mountInfo : hostMounts) {
            String mountString = mountInfo.fetchMountString();
            options.add(new AssetOption(mountString, getDisplayMount(ctx, mountInfo)));
        }

        AssetOptionsUtils.sortOptionsByLabel(options);
        return options;
    }

    public String getDisplayMount(AssetOptionsContext ctx, MountInfo mount) {
        StringBuffer strMount = new StringBuffer();

        String subDirPath = "";
        if (!StringUtils.isEmpty(mount.getSubDirectory())) {
            subDirPath = "/" + mount.getSubDirectory();
        }
        FileShareRestRep fs = api(ctx).fileSystems().get(mount.getFsId());
        String fsLabel = getMessage("file.fileNativeId", fs.getName(), fs.getNativeId());
        strMount.append(mount.getMountPath())
                .append("(")
                .append(mount.getSecurityType()).append(", ")
                .append(fsLabel)
                .append(subDirPath).append(")");

        return strMount.toString();
    }

    private String getSubDir(AssetOptionsContext ctx, ExportRule export) {
        FileShareRestRep fs = api(ctx).fileSystems().get(export.getFsID());
        String subDir = export.getExportPath().replace(fs.getMountPath(), "");
        if (subDir.startsWith("/")) {
            subDir = subDir.replaceFirst("/", "");
        }
        return subDir;
    }
}
