/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResources;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addRollback;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;
import static com.emc.sa.service.vipr.file.FileConstants.DEFAULT_ROOT_USER;
import static com.emc.sa.service.vipr.file.FileConstants.NFS_PROTOCOL;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.file.tasks.AssociateFilePolicyToFileSystem;
import com.emc.sa.service.vipr.file.tasks.ChangeFileVirtualPool;
import com.emc.sa.service.vipr.file.tasks.CreateFileContinuousCopy;
import com.emc.sa.service.vipr.file.tasks.CreateFileSnapshot;
import com.emc.sa.service.vipr.file.tasks.CreateFileSnapshotExport;
import com.emc.sa.service.vipr.file.tasks.CreateFileSnapshotShare;
import com.emc.sa.service.vipr.file.tasks.CreateFileSystem;
import com.emc.sa.service.vipr.file.tasks.CreateFileSystemExport;
import com.emc.sa.service.vipr.file.tasks.CreateFileSystemQuotaDirectory;
import com.emc.sa.service.vipr.file.tasks.CreateFileSystemShare;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileContinuousCopy;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSnapshot;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSnapshotExport;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSnapshotExportRule;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSnapshotShare;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSystem;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSystemExport;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSystemExportRule;
import com.emc.sa.service.vipr.file.tasks.DeactivateFileSystemShare;
import com.emc.sa.service.vipr.file.tasks.DeactivateQuotaDirectory;
import com.emc.sa.service.vipr.file.tasks.DissociateFilePolicyFromFileSystem;
import com.emc.sa.service.vipr.file.tasks.ExpandFileSystem;
import com.emc.sa.service.vipr.file.tasks.FailoverFileSystem;
import com.emc.sa.service.vipr.file.tasks.FindFileSnapshotExportRules;
import com.emc.sa.service.vipr.file.tasks.FindFileSystemExportRules;
import com.emc.sa.service.vipr.file.tasks.FindNfsExport;
import com.emc.sa.service.vipr.file.tasks.GetActiveSnapshotsForFileSystem;
import com.emc.sa.service.vipr.file.tasks.GetCifsSharesForFileSystem;
import com.emc.sa.service.vipr.file.tasks.GetFileSystem;
import com.emc.sa.service.vipr.file.tasks.GetNfsExportsForFileSnapshot;
import com.emc.sa.service.vipr.file.tasks.GetNfsExportsForFileSystem;
import com.emc.sa.service.vipr.file.tasks.GetQuotaDirectory;
import com.emc.sa.service.vipr.file.tasks.GetSharesForFileSnapshot;
import com.emc.sa.service.vipr.file.tasks.RestoreFileSnapshot;
import com.emc.sa.service.vipr.file.tasks.SetFileSnapshotShareACL;
import com.emc.sa.service.vipr.file.tasks.SetFileSystemShareACL;
import com.emc.sa.service.vipr.file.tasks.StopFileContinuousCopy;
import com.emc.sa.service.vipr.file.tasks.UpdateFileSnapshotExport;
import com.emc.sa.service.vipr.file.tasks.UpdateFileSystemExport;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.api.service.impl.resource.FileService.FileTechnologyType;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileShareExportUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.file.SnapshotExportUpdateParams;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class FileStorageUtils {

    public static final String COPY_NATIVE = "native";

    public static FileShareRestRep getFileSystem(URI fileSystemId) {
        return execute(new GetFileSystem(fileSystemId));
    }

    public static List<FileShareRestRep> getFileSystems(Collection<URI> fileSystemIds) {
        List<FileShareRestRep> fileSystems = Lists.newArrayList();
        for (URI fileSystemId : fileSystemIds) {
            fileSystems.add(getFileSystem(fileSystemId));
        }
        return fileSystems;
    }

    public static ShareACLs createShareACLs(FileSystemACLs[] acls) {
        ShareACLs aclsToAdd = new ShareACLs();
        List<ShareACL> aclList = new ArrayList<ShareACL>();

        for (FileSystemACLs fileSystemACL : acls) {
            ShareACL shareAcl = new ShareACL();
            if (fileSystemACL.aclType.equalsIgnoreCase("GROUP")) {
                shareAcl.setGroup(fileSystemACL.aclName);
            } else {
                shareAcl.setUser(fileSystemACL.aclName);
            }
            if (!StringUtils.isEmpty(fileSystemACL.aclDomain)) {
                shareAcl.setDomain(fileSystemACL.aclDomain);
            }
            shareAcl.setPermission(fileSystemACL.aclPermission);
            aclList.add(shareAcl);
        }
        aclsToAdd.setShareACLs(aclList);
        return aclsToAdd;
    }

    public static URI createFileSystem(URI project, URI virtualArray, URI virtualPool, String label, double sizeInGb, int advisoryLimit,
            int softLimit, int gracePeriod) {
        Task<FileShareRestRep> task = execute(new CreateFileSystem(label, sizeInGb, advisoryLimit, softLimit, gracePeriod, virtualPool,
                virtualArray, project));
        addAffectedResource(task);
        URI fileSystemId = task.getResourceId();
        addRollback(new DeactivateFileSystem(fileSystemId, FileControllerConstants.DeleteTypeEnum.FULL));
        logInfo("file.storage.filesystem.task", fileSystemId, task.getOpId());
        return fileSystemId;
    }

    public static URI createFileSystem(URI project, URI virtualArray, URI virtualPool, String label, double sizeInGb) {
        Task<FileShareRestRep> task = execute(new CreateFileSystem(label, sizeInGb, virtualPool, virtualArray, project));
        addAffectedResource(task);
        URI fileSystemId = task.getResourceId();
        addRollback(new DeactivateFileSystem(fileSystemId, FileControllerConstants.DeleteTypeEnum.FULL));
        logInfo("file.storage.filesystem.task", fileSystemId, task.getOpId());
        return fileSystemId;
    }

    public static void deleteFileSystem(URI fileSystemId, FileControllerConstants.DeleteTypeEnum fileDeletionType) {
        if (FileControllerConstants.DeleteTypeEnum.FULL.equals(fileDeletionType)) {
            // Remove snapshots for the volume
            for (FileSnapshotRestRep snapshot : getFileSnapshots(fileSystemId)) {
                deleteFileSnapshot(snapshot.getId());
            }

            // Deactivate CIFS Shares
            for (SmbShareResponse share : getCifsShares(fileSystemId)) {
                deactivateCifsShare(fileSystemId, share.getShareName());
            }

            // Delete all export rules for filesystem and all sub-directories
            if (!getFileSystemExportRules(fileSystemId, true, null).isEmpty()) {
                deactivateFileSystemExport(fileSystemId, true, null);
            }

            // Deactivate NFS Exports
            for (FileSystemExportParam export : getNfsExports(fileSystemId)) {
                deactivateExport(fileSystemId, export);
            }
        }

        // Remove the FileSystem
        deactivateFileSystem(fileSystemId, fileDeletionType);
    }

    public static void deleteFileSnapshot(URI fileSnapshotId) {
        // Deactivate Snapshot Exports
        if (!getFileSnapshotExportRules(fileSnapshotId, true, null).isEmpty()) {
            deactivateSnapshotExport(fileSnapshotId, true, null);
        }

        // Deactivate NFS Exports
        for (FileSystemExportParam export : getNfsSnapshotExport(fileSnapshotId)) {
            deactivateSnapshotExport(fileSnapshotId, export);
        }

        // Deactivate Snapshot Shares
        for (SmbShareResponse share : getFileSnapshotShares(fileSnapshotId)) {
            deactivateSnapshotShare(fileSnapshotId, share);
        }

        // Remove the FileSnapshot
        deactivateFileSnapshot(fileSnapshotId);
    }

    public static void deactivateFileSystem(URI fileSystemId, FileControllerConstants.DeleteTypeEnum fileDeletionType) {
        Task<FileShareRestRep> response = execute(new DeactivateFileSystem(fileSystemId, fileDeletionType));
        addAffectedResource(response);
        logInfo("file.storage.task", response.getOpId());
    }

    public static void expandFileSystem(URI fileSystemId, double sizeInGb) {
        String newSize = String.valueOf(DiskSizeConversionUtils.gbToBytes(sizeInGb));
        Task<FileShareRestRep> response = execute(new ExpandFileSystem(fileSystemId, newSize));
        addAffectedResource(response);
        logInfo("file.storage.task", response.getOpId());
    }

    public static String createCifsShare(URI fileSystemId, String shareName, String shareComment, String subDirectory) {
        Task<FileShareRestRep> task = execute(new CreateFileSystemShare(shareName, shareComment, fileSystemId, subDirectory));
        addAffectedResource(task);
        String shareId = task.getResourceId().toString();
        addRollback(new DeactivateFileSystemShare(fileSystemId, shareId));
        logInfo("file.storage.share.task", shareId, task.getOpId());
        return shareId;
    }

    public static void setFileSystemShareACL(URI fileSystemId, String shareName, FileSystemACLs[] acls) {
        Task<FileShareRestRep> task = execute(new SetFileSystemShareACL(fileSystemId, shareName, acls));
        addAffectedResource(task);
        logInfo("file.storage.share.filesystem.acl", fileSystemId, shareName, task.getOpId());
    }

    public static void setFileSnapshotShareACL(URI snapshotId, String shareName, FileSystemACLs[] acls) {
        Task<FileSnapshotRestRep> task = execute(new SetFileSnapshotShareACL(snapshotId, shareName, acls));
        addAffectedResource(task);
        logInfo("file.storage.share.snapshot.acl", snapshotId, shareName, task.getOpId());
    }

    public static void deactivateCifsShare(URI fileSystemId, String shareName) {
        Task<FileShareRestRep> task = execute(new DeactivateFileSystemShare(fileSystemId, shareName));
        addAffectedResource(task);
    }

    public static List<SmbShareResponse> getCifsShares(URI fileSystemId) {
        return execute(new GetCifsSharesForFileSystem(fileSystemId));
    }

    public static String createFileSystemExport(URI fileSystemId, String comment, FileExportRule exportRule, String subDirectory) {
        return createFileSystemExport(fileSystemId, comment, exportRule.security, exportRule.permission, DEFAULT_ROOT_USER,
                exportRule.exportHosts, subDirectory);
    }

    public static String createFileSystemExport(URI fileSystemId, String comment, String security, String permissions, String rootUser,
            List<String> exportHosts, String subDirectory) {
        Task<FileShareRestRep> task = execute(new CreateFileSystemExport(fileSystemId, comment, NFS_PROTOCOL, security, permissions,
                rootUser, exportHosts, subDirectory));
        addAffectedResource(task);
        String exportId = task.getResourceId().toString();
        addRollback(new DeactivateFileSystemExportRule(fileSystemId, true, null));
        logInfo("file.storage.export.task", exportId, task.getOpId());
        return exportId;
    }

    public static String createFileSnapshotExport(URI fileSnapshotId, String comment, FileExportRule exportRule, String subDirectory) {
        return FileStorageUtils.createFileSnapshotExport(fileSnapshotId, comment, exportRule.security, exportRule.permission,
                DEFAULT_ROOT_USER, exportRule.exportHosts, subDirectory);
    }

    public static String createFileSnapshotExport(URI fileSnapshotId, String comment, String security, String permissions, String rootUser,
            List<String> exportHosts, String subDirectory) {
        Task<FileSnapshotRestRep> task = execute(new CreateFileSnapshotExport(fileSnapshotId, comment, NFS_PROTOCOL, security, permissions,
                rootUser, exportHosts, subDirectory));
        addAffectedResource(task);
        String exportId = task.getResourceId().toString();
        addRollback(new DeactivateFileSnapshotExportRule(fileSnapshotId, true, null));
        logInfo("file.storage.export.task", exportId, task.getOpId());
        return exportId;
    }

    public static void deactivateSnapshotExport(URI fileSnapshotId, Boolean allDir, String subDir) {
        Task<FileSnapshotRestRep> task = execute(new DeactivateFileSnapshotExportRule(fileSnapshotId, allDir, subDir));
        addAffectedResource(task);
    }

    public static void deactivateSnapshotExport(URI fileSnapshotId, FileSystemExportParam export) {
        deactivateSnapshotExport(fileSnapshotId, export.getProtocol(), export.getSecurityType(), export.getPermissions(),
                export.getRootUserMapping());
    }

    public static void deactivateSnapshotExport(URI fileSnapshotId, String protocol, String type, String permissions,
            String rootUser) {
        Task<FileSnapshotRestRep> task = execute(new DeactivateFileSnapshotExport(fileSnapshotId, protocol, type, permissions,
                rootUser));
        addAffectedResource(task);
    }

    public static void deactivateSnapshotShare(URI fileSnapshotId, SmbShareResponse share) {
        deactivateSnapshotShare(fileSnapshotId, share.getShareName());
    }

    public static void deactivateSnapshotShare(URI fileSnapshotId, String shareName) {
        Task<FileSnapshotRestRep> task = execute(new DeactivateFileSnapshotShare(fileSnapshotId, shareName));
        addAffectedResource(task);
    }

    public static void deactivateExport(URI fileSystemId, FileSystemExportParam export) {
        deactivateExport(fileSystemId, export.getProtocol(), export.getSecurityType(), export.getPermissions(),
                export.getRootUserMapping());
    }

    public static void deactivateExport(URI fileSystemId, String protocol, String type, String permissions,
            String rootUser) {
        Task<FileShareRestRep> task = execute(new DeactivateFileSystemExport(fileSystemId, protocol, type, permissions,
                rootUser));
        addAffectedResource(task);
    }

    public static List<SmbShareResponse> getFileSnapshotShares(URI fileSystemId) {
        return execute(new GetSharesForFileSnapshot(fileSystemId));
    }

    public static List<FileSystemExportParam> getNfsExports(URI fileSystemId) {
        return execute(new GetNfsExportsForFileSystem(fileSystemId));
    }

    public static List<FileSystemExportParam> getNfsSnapshotExport(URI snapshotId) {
        return execute(new GetNfsExportsForFileSnapshot(snapshotId));
    }

    public static FileSystemExportParam getNfsExport(URI fileSystemId, String security, String permissions,
            String rootUser) {
        return getExport(fileSystemId, NFS_PROTOCOL, security, permissions, rootUser);
    }

    public static FileSystemExportParam findNfsExportByMountPoint(URI fileSystemId, String mountPoint) {
        List<FileSystemExportParam> exports = getNfsExports(fileSystemId);
        return execute(new FindNfsExport(fileSystemId, mountPoint, exports));
    }

    public static FileSystemExportParam getExport(URI fileSystemId, String protocol, String security,
            String permissions, String rootUser) {
        List<FileSystemExportParam> exports = execute(new GetNfsExportsForFileSystem(fileSystemId));
        for (FileSystemExportParam export : exports) {
            boolean protocolMatch = StringUtils.equals(export.getProtocol(), protocol);
            boolean securityMatch = StringUtils.equals(export.getSecurityType(), security);
            boolean permissionMatch = StringUtils.equals(export.getPermissions(), permissions);
            boolean rootUserMatch = StringUtils.equals(export.getRootUserMapping(), rootUser);

            if (protocolMatch && securityMatch && permissionMatch && rootUserMatch) {
                return export;
            }
        }
        return null;
    }

    public static Task<FileShareRestRep> createFileContinuousCopy(URI fileId, String name) {
        Task<FileShareRestRep> copy = execute(new CreateFileContinuousCopy(fileId, name, FileTechnologyType.LOCAL_MIRROR.name()));
        addAffectedResource(copy);
        return copy;
    }

    public static void removeContinuousCopiesForFile(URI fileId, Collection<URI> continuousCopyIds) {
        for (URI continuousCopyId : continuousCopyIds) {
            removeFileContinuousCopy(fileId, continuousCopyId);
        }
    }

    private static void removeFileContinuousCopy(URI fileId, URI continuousCopyId) {
        execute(new StopFileContinuousCopy(fileId, continuousCopyId, FileTechnologyType.LOCAL_MIRROR.name()));
        Task<FileShareRestRep> task = execute(new DeactivateFileContinuousCopy(fileId, continuousCopyId,
                FileControllerConstants.DeleteTypeEnum.FULL.toString()));
        addAffectedResource(task);
    }

    public static void failoverFileSystem(URI fileId, URI targetId) {
        Tasks<FileShareRestRep> tasks = execute(new FailoverFileSystem(fileId, targetId, FileTechnologyType.REMOTE_MIRROR.name()));
        addAffectedResources(tasks);
    }

    public static void changeFileVirtualPool(URI fileId, URI targetVirtualPool) {
        Task<FileShareRestRep> task = execute(new ChangeFileVirtualPool(fileId, targetVirtualPool));
        addAffectedResource(task);
    }

    public static URI createFileSnapshot(URI fileSystemId, String name) {
        Task<FileSnapshotRestRep> task = execute(new CreateFileSnapshot(fileSystemId, name));
        addAffectedResource(task);
        return task.getResourceId();
    }

    public static URI createFileSystemQuotaDirectory(URI fileSystemId, String name, Boolean oplock, String securityStyle, String size,
            int softLimit, int advisoryLimit, int gracePeriod) {
        Task<QuotaDirectoryRestRep> task = execute(new CreateFileSystemQuotaDirectory(fileSystemId, name, oplock, securityStyle, size,
                softLimit, advisoryLimit, gracePeriod));
        addAffectedResource(task);
        return task.getResourceId();
    }

    public static List<QuotaDirectoryRestRep> getQuotaDirectories(Collection<URI> quotaDirectoryIds) {
        List<QuotaDirectoryRestRep> quotaDirectories = Lists.newArrayList();
        for (URI quotaDirectoryId : quotaDirectoryIds) {
            quotaDirectories.add(getQuotaDirectory(quotaDirectoryId));
        }
        return quotaDirectories;
    }

    public static void deactivateQuotaDirectory(URI quotaDirectoryId) {
        Task<QuotaDirectoryRestRep> response = execute(new DeactivateQuotaDirectory(quotaDirectoryId));
        addAffectedResource(response);
        logInfo("file.storage.task", response.getOpId());
    }

    public static QuotaDirectoryRestRep getQuotaDirectory(URI quotaDirectoryId) {
        return execute(new GetQuotaDirectory(quotaDirectoryId));
    }

    public static void deactivateFileSnapshot(URI snapshotId) {
        Task<FileSnapshotRestRep> task = execute(new DeactivateFileSnapshot(snapshotId));
        addAffectedResource(task);
    }

    public static List<FileSnapshotRestRep> getFileSnapshots(URI fileSystemId) {
        return execute(new GetActiveSnapshotsForFileSystem(fileSystemId));
    }

    public static void restoreFileSnapshot(URI snapshotId) {
        Task<FileSnapshotRestRep> task = execute(new RestoreFileSnapshot(snapshotId));
        addAffectedResource(task);
    }

    public static URI shareFileSnapshot(URI snapshotId, String shareName, String shareComment) {
        Task<FileSnapshotRestRep> task = execute(new CreateFileSnapshotShare(snapshotId, shareName, shareComment));
        addAffectedResource(task);
        return task.getResourceId();
    }

    public static URI deactivateFileSystemExport(URI fileSystemId, Boolean allDir, String subDir) {
        Task<FileShareRestRep> task = execute(new DeactivateFileSystemExportRule(fileSystemId, allDir, subDir));
        addAffectedResource(task);
        return task.getResourceId();
    }

    public static String updateFileSystemExport(URI fileSystemId, String subDirectory, FileExportRule[] fileExportRules) {
        List<ExportRule> exportRuleList = getFileSystemExportRules(fileSystemId, false, subDirectory);
        Set<String> existingRuleSet = Sets.newHashSet();
        for (ExportRule rule : exportRuleList) {
            existingRuleSet.add(rule.getSecFlavor());
        }

        Map<String, Map<String, Set<String>>> rules = Maps.newHashMap();
        for (FileExportRule fileExportRule : fileExportRules) {
            if (!rules.containsKey(fileExportRule.security)) {
                Map<String, Set<String>> rule = Maps.newHashMap();
                rule.put(fileExportRule.permission, Sets.newHashSet(fileExportRule.exportHosts));
                rules.put(fileExportRule.security, rule);
            }
            else if (!rules.get(fileExportRule.security).containsKey(fileExportRule.permission)) {
                rules.get(fileExportRule.security).put(fileExportRule.permission, Sets.newHashSet(fileExportRule.exportHosts));
            }
            else {
                rules.get(fileExportRule.security).get(fileExportRule.permission).addAll(fileExportRule.exportHosts);
            }
        }
        List<ExportRule> exportRuleListToAdd = Lists.newArrayList();
        List<ExportRule> exportRuleListToModify = Lists.newArrayList();
        for (String sec : rules.keySet()) {
            Map<String, Set<String>> rule = rules.get(sec);
            ExportRule exportRule = new ExportRule();
            exportRule.setFsID(fileSystemId);
            exportRule.setSecFlavor(sec);
            exportRule.setReadOnlyHosts(rule.get(FileShareExport.Permissions.ro.name()));
            exportRule.setReadWriteHosts(rule.get(FileShareExport.Permissions.rw.name()));
            exportRule.setRootHosts(rule.get(FileShareExport.Permissions.root.name()));
            exportRule.setAnon(DEFAULT_ROOT_USER);
            if (existingRuleSet.contains(exportRule.getSecFlavor())) {
                exportRuleListToModify.add(exportRule);
            }
            else {
                exportRuleListToAdd.add(exportRule);
            }
        }

        FileShareExportUpdateParams params = new FileShareExportUpdateParams();
        if (!exportRuleListToAdd.isEmpty()) {
            ExportRules exportRulesToAdd = new ExportRules();
            exportRulesToAdd.setExportRules(exportRuleListToAdd);
            params.setExportRulesToAdd(exportRulesToAdd);
        }
        if (!exportRuleListToModify.isEmpty()) {
            ExportRules exportRulesToModify = new ExportRules();
            exportRulesToModify.setExportRules(exportRuleListToModify);
            params.setExportRulesToModify(exportRulesToModify);
        }
        Task<FileShareRestRep> task = execute(new UpdateFileSystemExport(fileSystemId, subDirectory, params));
        addAffectedResource(task);
        String exportId = task.getResourceId().toString();
        logInfo("file.storage.export.task", exportId, task.getOpId());
        return exportId;
    }

    public static String updateFileSnapshotExport(URI fileSnapshotId, String subDirectory, FileExportRule[] fileExportRules) {
        List<ExportRule> exportRuleList = getFileSnapshotExportRules(fileSnapshotId, false, subDirectory);
        Set<String> existingRuleSet = Sets.newHashSet();
        for (ExportRule rule : exportRuleList) {
            existingRuleSet.add(rule.getSecFlavor());
        }

        Map<String, Map<String, Set<String>>> rules = Maps.newHashMap();
        for (FileExportRule fileExportRule : fileExportRules) {
            if (!rules.containsKey(fileExportRule.security)) {
                Map<String, Set<String>> rule = Maps.newHashMap();
                rule.put(fileExportRule.permission, Sets.newHashSet(fileExportRule.exportHosts));
                rules.put(fileExportRule.security, rule);
            }
            else if (!rules.get(fileExportRule.security).containsKey(fileExportRule.permission)) {
                rules.get(fileExportRule.security).put(fileExportRule.permission, Sets.newHashSet(fileExportRule.exportHosts));
            }
            else {
                rules.get(fileExportRule.security).get(fileExportRule.permission).addAll(fileExportRule.exportHosts);
            }
        }
        List<ExportRule> exportRuleListToAdd = Lists.newArrayList();
        List<ExportRule> exportRuleListToModify = Lists.newArrayList();
        for (String sec : rules.keySet()) {
            Map<String, Set<String>> rule = rules.get(sec);
            ExportRule exportRule = new ExportRule();
            exportRule.setFsID(fileSnapshotId);
            exportRule.setSecFlavor(sec);
            exportRule.setReadOnlyHosts(rule.get(FileShareExport.Permissions.ro.name()));
            exportRule.setReadWriteHosts(rule.get(FileShareExport.Permissions.rw.name()));
            exportRule.setRootHosts(rule.get(FileShareExport.Permissions.root.name()));
            exportRule.setAnon(DEFAULT_ROOT_USER);
            if (existingRuleSet.contains(exportRule.getSecFlavor())) {
                exportRuleListToModify.add(exportRule);
            }
            else {
                exportRuleListToAdd.add(exportRule);
            }
        }

        SnapshotExportUpdateParams params = new SnapshotExportUpdateParams();
        if (!exportRuleListToAdd.isEmpty()) {
            ExportRules exportRulesToAdd = new ExportRules();
            exportRulesToAdd.setExportRules(exportRuleListToAdd);
            params.setExportRulesToAdd(exportRulesToAdd);
        }
        if (!exportRuleListToModify.isEmpty()) {
            ExportRules exportRulesToModify = new ExportRules();
            exportRulesToModify.setExportRules(exportRuleListToModify);
            params.setExportRulesToModify(exportRulesToModify);
        }

        Task<FileSnapshotRestRep> task = execute(new UpdateFileSnapshotExport(fileSnapshotId, subDirectory, params));
        addAffectedResource(task);
        String exportId = task.getResourceId().toString();
        logInfo("file.storage.export.task", exportId, task.getOpId());
        return exportId;
    }

    public static List<ExportRule> getFileSystemExportRules(URI fileSystemId, Boolean allDir, String subDir) {
        return execute(new FindFileSystemExportRules(fileSystemId, allDir, subDir));
    }

    public static List<ExportRule> getFileSnapshotExportRules(URI fileSnapshotId, Boolean allDir, String subDir) {
        return execute(new FindFileSnapshotExportRules(fileSnapshotId, allDir, subDir));
    }

    public static Task<FileShareRestRep> associateFilePolicy(URI fileSystemId, URI filePolicyId) {
        return execute(new AssociateFilePolicyToFileSystem(fileSystemId, filePolicyId));
    }

    public static Task<FileShareRestRep> dissociateFilePolicy(URI fileSystemId, URI filePolicyId) {
        return execute(new DissociateFilePolicyFromFileSystem(fileSystemId, filePolicyId));
    }

    public static List<String> getInvalidFileACLs(FileSystemACLs[] fileACLs) {
        List<String> names = new ArrayList<String>();
        for (FileStorageUtils.FileSystemACLs acl : fileACLs) {
            if (StringUtils.contains(acl.aclName, "\\")) {
                names.add(acl.aclName);
            }
        }

        return names;
    }

    public static FileSystemACLs[] clearEmptyFileACLs(FileSystemACLs[] fileACLs) {
        List<FileStorageUtils.FileSystemACLs> toRemove = new ArrayList<FileStorageUtils.FileSystemACLs>();
        for (FileStorageUtils.FileSystemACLs acl : fileACLs) {
            if (acl.aclName != null && acl.aclName.isEmpty()) {
                toRemove.add(acl);
            }
        }

        for (FileStorageUtils.FileSystemACLs element : toRemove) {
            fileACLs = ArrayUtils.removeElement(fileACLs, element);
        }

        return fileACLs;
    }

    public static class FileSystemACLs {
        @Param
        public String aclType;

        @Param
        public String aclName;

        @Param
        public String aclDomain;

        @Param
        public String aclPermission;
    }

    public static class FileExportRule {
        @Param
        protected List<String> exportHosts;

        @Param
        protected String security;

        @Param
        protected String permission;
    }
}
