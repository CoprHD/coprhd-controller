/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.datatable.FileSystemsDataTable;
import models.datatable.NfsACLDataTable;
import models.datatable.ShareACLDataTable;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.BourneUtil;
import util.FileUtils;
import util.FileUtils.ExportRuleInfo;
import util.MessagesUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;

import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileCifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileNfsACLUpdateParams;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.model.file.FileShareExportUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.FileSystemShareParam;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.NfsACL;
import com.emc.storageos.model.file.QuotaDirectoryDeleteParam;
import com.emc.storageos.model.file.QuotaDirectoryRestRep;
import com.emc.storageos.model.file.QuotaDirectoryUpdateParam;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.security.Security;
import controllers.util.FlashException;

@With(Common.class)
public class FileSystems extends ResourceController {
    private static final String UNKNOWN = "resources.filesystems.unknown";
    protected static final String DELETED = "resources.filesystem.share.acl.deleted";
    protected static final String ADDED = "resources.filesystem.share.acl.added";

    private static FileSystemsDataTable fileSystemsDataTable = new FileSystemsDataTable();

    private static final StringOption[] PERMISSION_TYPES = {
            new StringOption(FileShareExport.Permissions.rw.name(), FileShareExport.Permissions.rw.name()),
            new StringOption(FileShareExport.Permissions.ro.name(), FileShareExport.Permissions.ro.name()),
            new StringOption(FileShareExport.Permissions.root.name(), FileShareExport.Permissions.root.name())
    };

    public static void fileSystems(String projectId) {
        setActiveProjectId(projectId);
        renderArgs.put("dataTable", fileSystemsDataTable);
        addReferenceData();
        render();
    }

    public static void fileSystemsJson(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            setActiveProjectId(projectId);
        } else {
            projectId = getActiveProjectId();
        }
        List<FileSystemsDataTable.FileSystem> fileSystems = FileSystemsDataTable.fetch(uri(projectId));
        renderJSON(DataTablesSupport.createJSON(fileSystems, params));
    }

    public static void fileSystem(String fileSystemId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        FileShareRestRep fileSystem = null;
        try {
            fileSystem = client.fileSystems().get(uri(fileSystemId));
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404 ||
                    e.getHttpCode() == 400) {
                flash.error(MessagesUtils.get(UNKNOWN, fileSystemId));
                fileSystems(null);
            }
            throw e;
        }

        if (fileSystem != null) {
            if (fileSystem.getVirtualArray() != null) {
                VirtualArrayRestRep virtualArray = client.varrays().get(fileSystem.getVirtualArray());
                renderArgs.put("virtualArray", virtualArray);
            }
            if (fileSystem.getVirtualPool() != null) {
                FileVirtualPoolRestRep virtualPool = client.fileVpools().get(fileSystem.getVirtualPool());
                renderArgs.put("virtualPool", virtualPool);
            }
            if (Security.isSystemAdminOrRestrictedSystemAdmin()) {
                if (fileSystem.getStorageSystem() != null) {
                    StorageSystemRestRep storageSystem = client.storageSystems().get(fileSystem.getStorageSystem());
                    renderArgs.put("storageSystem", storageSystem);
                }
                if (fileSystem.getPool() != null) {
                    StoragePoolRestRep storagePool = client.storagePools().get(fileSystem.getPool());
                    renderArgs.put("storagePool", storagePool);
                }
                if (fileSystem.getStoragePort() != null) {
                    StoragePortRestRep storagePort = client.storagePorts().get(fileSystem.getStoragePort());
                    renderArgs.put("storagePort", storagePort);
                }
            }

            Tasks<FileShareRestRep> tasksResponse = client.fileSystems().getTasks(fileSystem.getId());
            List<Task<FileShareRestRep>> tasks = tasksResponse.getTasks();
            renderArgs.put("tasks", tasks);
        } else {
            notFound(MessagesUtils.get("resources.filesystems.notfound"));
        }

        renderArgs.put("permissionTypeOptions", PERMISSION_TYPES);
        render(fileSystem);
    }

    public static void fileSystemExports(String fileSystemId) {
        URI id = uri(fileSystemId);
        List<ExportRule> exports = FileUtils.getFSExportRules(id);
        List<FileSystemExportParam> exportsParam = FileUtils.getExports(id);
        renderArgs.put("permissionTypeOptions", Lists.newArrayList(FileShareExport.Permissions.values()));
        render(exports, exportsParam);
    }
    
	public static void fileSystemNfsACLs(String fileSystemId) {
		ViPRCoreClient client = BourneUtil.getViprClient();
		List<NfsACL> nfsAcls = client.fileSystems().getAllNfsACLs(
				uri(fileSystemId));
		render(nfsAcls);
	}

    public static void listNfsAcl(String fileSystemId, String fsMountPath,
            String subDir) {
        renderArgs.put("dataTable", new NfsACLDataTable());
        renderArgs.put("subDir", subDir);
        renderArgs.put("fileSystemId", uri(fileSystemId));
        renderArgs.put("fsMountPath", fsMountPath);
        renderArgs.put("fileSystemSubDirAndPath", fileSystemId + "~~~" + subDir
                + "~~~" + fsMountPath);
        renderArgs.put("permissionOptions", StringOption.options(new String[] {
                "read", "write", "execute" }));
        ViPRCoreClient client = BourneUtil.getViprClient();
        NfsACLForm nfsACL = new NfsACLForm();
        FileShareRestRep restRep = client.fileSystems().get(uri(fileSystemId));
        renderArgs.put("fileSystemName", restRep.getName());

        render(nfsACL);
    }

    public static void listNfsAclJson(String fileSystemSubDirAndPath) {
        renderArgs.put("dataTable", new NfsACLDataTable());
        String fileSystemId = null;
        String subDir = null;
        String fsMountPath = null;
        if (StringUtils.isNotBlank(fileSystemSubDirAndPath)) {
            String[] parts = fileSystemSubDirAndPath.split("~~~");
            if (parts.length == 3) {
                fileSystemId = parts[0];
                subDir = parts[1];
                fsMountPath = parts[2];
            }
        }
        renderArgs.put("subDir", subDir);
        renderArgs.put("fsMountPath", fsMountPath);
        renderArgs.put("fileSystemId", uri(fileSystemId));
        renderArgs.put("fileSystemName", uri(fileSystemId));
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<NfsACL> nfsAcls = client.fileSystems().getNfsACLs(
                uri(fileSystemId), subDir);
        NfsACL nfsAcl = new NfsACL();
        List<NfsACLDataTable.NfsAclInfo> nfsAccessControlList = Lists
                .newArrayList();
        if (nfsAcls.size() > 0) {
            nfsAcl = nfsAcls.get(0);
            List<NfsACE> acl = nfsAcl.getNfsAces();
            for (NfsACE ace : acl) {
                String name = ace.getUser();
                String type = ace.getType();
                String permissions = ace.getPermissions();
                String domain = ace.getDomain();
                String permissionType = ace.getPermissionType();
                nfsAccessControlList.add(new NfsACLDataTable.NfsAclInfo(name,
                        type, permissions, fileSystemId, subDir, domain,
                        fsMountPath, permissionType));
            }
        }
        renderJSON(DataTablesSupport.createJSON(nfsAccessControlList, params));
    }

    public static void editNfsAce(@Required String id) {
        String type = NfsACLForm.extractTypeFromId(id);
        String name = NfsACLForm.extractNameFromId(id);
        String domain = NfsACLForm.extractDomainFromId(id);
        String fileSystem = NfsACLForm.extractFileSystemFromId(id);
        String subDir = NfsACLForm.extractSubDirFromId(id);
        String permissions = NfsACLForm.extractPermissionsFromId(id);
        String permissionType = NfsACLForm.extractPermissionTypeFromId(id);
        String fsMountPath = NfsACLForm.extractMounPathFromId(id);
        if ("null".equals(domain)) {
            domain = "";
        }

        NfsACLForm nfsACL = new NfsACLForm();
        nfsACL.type = type.toUpperCase();
        nfsACL.name = name;
        nfsACL.domain = domain;
        String[] strPerm = permissions.replaceAll("/", ",").split(",");
        nfsACL.permissions = new HashSet<String>(Arrays.asList(strPerm));
        nfsACL.permissionType = permissionType;
        renderArgs.put("permissionOptions", StringOption.options(new String[] {
                "read", "write", "execute" }));
        renderArgs.put("permissionTypeOptions", StringOption.options(new String[] {
                "allow", "deny" }));
        renderArgs.put("fileSystemId", uri(fileSystem));
        ViPRCoreClient client = BourneUtil.getViprClient();
        FileShareRestRep restRep = client.fileSystems().get(uri(fileSystem));
        renderArgs.put("fileSystemName", restRep.getName());
        renderArgs.put("subDir", subDir);
        renderArgs.put("fsMountPath", fsMountPath);
        renderArgs.put("TYPE", type.toUpperCase());
        render(nfsACL);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void saveNfsAce(NfsACLForm nfsACL) {

        String name = params.get("name");
        String type = params.get("type");
        String domain = params.get("domain");
        String subDir = params.get("subDir");
        String fsMountPath = params.get("fsMountPath");
        String fileSystemId = params.get("fileSystemId");
        Set<String> permissions = nfsACL.permissions;
        String permissionType = nfsACL.permissionType;
        String strPer = "";
        for (String permission : permissions) {
            strPer = strPer + permission.toLowerCase() + ",";
        }
        strPer = strPer.substring(0, strPer.length() - 1);
        List<NfsACE> aces = Lists.newArrayList();
        NfsACE nfsAce = new NfsACE();

        nfsAce.setType(type.toLowerCase());
        nfsAce.setUser(name);
        nfsAce.setPermissions(strPer);
        nfsAce.setPermissionType(permissionType);
        if (domain != null && !"".equals(domain) && !"null".equals(domain)) {
            nfsAce.setDomain(domain);
        }
        aces.add(nfsAce);

        FileNfsACLUpdateParams input = new FileNfsACLUpdateParams();
        input.setAcesToModify(aces);
        if (subDir != null && !"null".equals(subDir)) {
            input.setSubDir(subDir);
        }

        ViPRCoreClient client = BourneUtil.getViprClient();
        client.fileSystems().updateNfsACL(uri(fileSystemId), input);

        listNfsAcl(fileSystemId, fsMountPath, subDir);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void removeNfsAcl(@As(",") String[] ids) {

        List<NfsACE> aces = Lists.newArrayList();
        String fileSystem = null;
        String subDir = null;
        String fsMountPath = null;
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                String type = NfsACLForm.extractTypeFromId(id);
                String name = NfsACLForm.extractNameFromId(id);
                String domain = NfsACLForm.extractDomainFromId(id);
                String permissions = NfsACLForm.extractPermissionsFromId(id);
                fileSystem = NfsACLForm.extractFileSystemFromId(id);
                subDir = NfsACLForm.extractSubDirFromId(id);
                fsMountPath = NfsACLForm.extractMounPathFromId(id);
                NfsACE ace = new NfsACE();
                ace.setUser(name);
                ace.setType(type);
                ace.setPermissions(permissions.replaceAll("/", ","));
                ace.setPermissionType("allow");
                if (domain != null && !"".equals(domain)
                        && !"null".equals(domain)) {
                    ace.setDomain(domain);
                }
                aces.add(ace);
            }

            FileNfsACLUpdateParams input = new FileNfsACLUpdateParams();
            input.setAcesToDelete(aces);
            ViPRCoreClient client = BourneUtil.getViprClient();
            client.fileSystems().updateNfsACL(uri(fileSystem), input);
        }
        flash.success(MessagesUtils.get(DELETED));
        listNfsAcl(fileSystem, fsMountPath, subDir);

    }

    public static void addNfsAcl(String fileSystemSubDirAndPath,
            ShareACLForm shareACL, String formAccessControlList) {
        String fileSystem = null;
        String subDir = null;
        String fsMountPath = null;
        if (StringUtils.isNotBlank(fileSystemSubDirAndPath)) {
            String[] parts = fileSystemSubDirAndPath.split("~~~");
            if (parts.length == 3) {
                fileSystem = parts[0];
                subDir = parts[1];
                fsMountPath = parts[2];
            }
        }

        if (formAccessControlList == null || "".equals(formAccessControlList)) {
            flash.error(MessagesUtils
                    .get("resources.filesystem.share.acl.invalid.settings"),
                    null);
            listNfsAcl(fileSystem, fsMountPath, subDir);
        }
        NfsACLUpdateParams input = createNfsAclParams(formAccessControlList);
        ViPRCoreClient client = BourneUtil.getViprClient();
        try {
            client.fileSystems().updateNfsACL(uri(fileSystem), input);
        } catch (Exception e) {
            flash.error(e.getMessage(), null);
            listNfsAcl(fileSystem, fsMountPath, subDir);
        }
        flash.success(MessagesUtils.get(ADDED));
        listNfsAcl(fileSystem, fsMountPath, subDir);
    }

    public static void addSubDirAndNfsAcl(String formAccessControlList, String fileSystem, String subDir) {
        if (formAccessControlList == null || "".equals(formAccessControlList)) {
            flash.error(MessagesUtils
                    .get("resources.filesystem.share.acl.invalid.settings"),
                    null);
            fileSystem(fileSystem);
        }
        NfsACLUpdateParams input = createNfsAclParams(formAccessControlList);
        if (subDir != null && !"null".equals(subDir) && !subDir.isEmpty()) {
            input.setSubDir(subDir);
        }
        ViPRCoreClient client = BourneUtil.getViprClient();
        try {
            client.fileSystems().updateNfsACL(uri(fileSystem), input);
        } catch (Exception e) {
            flash.error(e.getMessage(), null);
            fileSystem(fileSystem);
        }
        flash.success(MessagesUtils.get(ADDED));
        fileSystem(fileSystem);
    }

    private static NfsACLUpdateParams createNfsAclParams(String formData) {
        String[] uiAcls = formData.split(",");
        List<NfsACE> aces = Lists.newArrayList();
        for (String uiAce : uiAcls) {
            String[] uiData = uiAce.split("~~~");
            String uiType = uiData[0];
            String uiName = uiData[1];
            String uiDomain = uiData[2];
            String uiPermissions = uiData[3];
            String uiPermissiontype = uiData[4];
            NfsACE nfsAce = new NfsACE();
            nfsAce.setUser(uiName);
            if (uiDomain != null && !"".equals(uiDomain) && !"null".equals(uiDomain)) {
                nfsAce.setDomain(uiDomain);
            }
            if (uiType != null && !"".equals(uiType) && !"null".equals(uiType)) {
                nfsAce.setType(uiType);
            }
            if (uiPermissions != null && !"".equals(uiPermissions) && !"null".equals(uiPermissions)) {
                nfsAce.setPermissions(uiPermissions.replaceAll("/", ","));
            }
            nfsAce.setPermissionType(uiPermissiontype);

            aces.add(nfsAce);
        }
        FileNfsACLUpdateParams input = new FileNfsACLUpdateParams();
        input.setAcesToAdd(aces);
        return input;
    }

    public static void fileSystemExportsJson(String id, String path, String sec) {
        ExportRuleInfo info = FileUtils.getFSExportRulesInfo(uri(id), path, sec);
        renderJSON(info);
    }

    public static void fileSystemQuotaJson(String id) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        QuotaDirectoryRestRep quota = client.quotaDirectories().getQuotaDirectory(uri(id));
        renderJSON(quota);
    }

    public static void fileSystemShares(String fileSystemId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<SmbShareResponse> shares = client.fileSystems().getShares(uri(fileSystemId));

        render(shares);
    }

    public static void fileSystemSnapshots(String fileSystemId) {

        ViPRCoreClient client = BourneUtil.getViprClient();

        List<NamedRelatedResourceRep> refs = client.fileSnapshots().listByFileSystem(uri(fileSystemId));

        List<FileSnapshotRestRep> snapshots = client.fileSnapshots().getByRefs(refs);

        render(snapshots);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void fileSystemQuotaDirectories(String fileSystemId) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<QuotaDirectoryRestRep> quotas = client.quotaDirectories().getByFileSystem(uri(fileSystemId));
        render(quotas);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void deleteFileSystemQuotaDirectory(String fileSystemId, String quotaDirectoryId) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        QuotaDirectoryDeleteParam param = new QuotaDirectoryDeleteParam(true);
        Task<QuotaDirectoryRestRep> task = client.quotaDirectories().deleteQuotaDirectory(uri(quotaDirectoryId), param);
        flash.put("info", MessagesUtils.get("resources.filesystem.quota.deactivate"));

        fileSystem(fileSystemId);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void deleteFileSystemExport(String fileSystemId, String security, String exportPath) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportRule rule = new ExportRule();
        rule.setSecFlavor(security);

        List<ExportRule> list = Lists.newArrayList();
        list.add(rule);

        ExportRules exportRules = new ExportRules();
        exportRules.setExportRules(list);

        FileShareExportUpdateParams params = new FileShareExportUpdateParams();
        params.setExportRulesToDelete(exportRules);

        FileShareRestRep fileSystem = client.fileSystems().get(uri(fileSystemId));
        String subDir = FileUtils.findSubDirectory(fileSystem.getMountPath(), exportPath);
        client.fileSystems().updateExport(uri(fileSystemId), subDir, params);

        flash.put("info", MessagesUtils.get("resources.filesystem.export.deactivate"));

        fileSystem(fileSystemId);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void deleteFileSystemShare(String fileSystemId, String shareName) {
        ViPRCoreClient client = BourneUtil.getViprClient();

        Task<FileShareRestRep> task = client.fileSystems().removeShare(uri(fileSystemId), shareName);
        flash.put("info", MessagesUtils.get("resources.filesystem.share.deactivate"));

        fileSystem(fileSystemId);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void deleteFileSystem(String fileSystemId, String deleteType) {
        if (StringUtils.isNotBlank(fileSystemId)) {
            ViPRCoreClient client = BourneUtil.getViprClient();

            boolean forceDelete = false;
            Task<FileShareRestRep> task = client.fileSystems().deactivate(uri(fileSystemId),
                    new FileSystemDeleteParam(forceDelete,deleteType));
            flash.put("info", MessagesUtils.get("resources.filesystem.deactivate"));
        }
        fileSystem(fileSystemId);
    }

    @FlashException(value = "fileSystems")
    public static void delete(@As(",") String[] ids, String deleteType) {
        delete(uris(ids), deleteType);
    }

    private static void delete(List<URI> ids, String deleteType) {
        if (ids != null) {
            ViPRCoreClient client = BourneUtil.getViprClient();
            List<Task<FileShareRestRep>> tasks = Lists.newArrayList();
            for (URI id : ids) {
                boolean forceDelete = false;
                Task<FileShareRestRep> task = client.fileSystems().deactivate(id,
                        new FileSystemDeleteParam(forceDelete, deleteType));
                tasks.add(task);
            }
            if (!tasks.isEmpty()) {
                flash.put("info", MessagesUtils.get("resources.filesystems.deactivate", tasks.size()));
            }
        }
        fileSystems(null);
    }

    @FlashException(referrer = { "fileSystem" })
    public static void save(Boolean edit, String id, String fsPath, String exportPath, String security,
            String anon, String subDir, @As(",") List<String> ro, @As(",") List<String> rw, @As(",") List<String> root) {

        ExportRule rule = new ExportRule();
        rule.setFsID(uri(id));
        rule.setMountPoint(fsPath);
        rule.setExportPath(exportPath);
        rule.setAnon(anon);
        rule.setSecFlavor(security);

        // Clean up endpoints list by removing all empty items
        List<String> empty = Arrays.asList("");
        rw.removeAll(empty);
        ro.removeAll(empty);
        root.removeAll(empty);

        if (!ro.isEmpty()) {
            rule.setReadOnlyHosts(FileUtils.buildEndpointList(ro));
        }
        if (!rw.isEmpty()) {
            rule.setReadWriteHosts(FileUtils.buildEndpointList(rw));
        }
        if (!root.isEmpty()) {
            rule.setRootHosts(FileUtils.buildEndpointList(root));
        }

        List<ExportRule> addRules = Lists.newArrayList();
        addRules.add(rule);

        ExportRules exportRules = new ExportRules();
        exportRules.setExportRules(addRules);

        FileShareExportUpdateParams params = new FileShareExportUpdateParams();
        if (!edit) {
            params.setExportRulesToAdd(exportRules);
        } else {
            params.setExportRulesToModify(exportRules);
            subDir = FileUtils.findSubDirectory(fsPath, exportPath);
        }

        ViPRCoreClient client = BourneUtil.getViprClient();
        client.fileSystems().updateExport(uri(id), subDir, params);

        flash.put("info", MessagesUtils.get("resources.filesystem.export.update"));
        fileSystem(id);

    }

    public static void saveQuota(String fileSystemId, String id, Quota quota) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        QuotaDirectoryUpdateParam param = new QuotaDirectoryUpdateParam();
        param.setOpLock(quota.oplock);
        param.setSecurityStyle(quota.securityStyle);
        param.setSize(String.valueOf(DiskSizeConversionUtils.gbToBytes(new Double(quota.size))));
        client.quotaDirectories().updateQuotaDirectory(uri(id), param);
        flash.put("info", MessagesUtils.get("resources.filesystem.quota.update"));
        fileSystem(fileSystemId);
    }

    /**
     * This method called when user adds sub directory.
     * 
     * @param id
     *            id of the file system.
     * @param shareName
     *            shareName of the sub directory.
     * @param subDirectory
     *            name of the sub directory.
     * @param description
     *            Given description during creation of sub directory.
     */

    @FlashException(referrer = { "fileSystem" })
    public static void addSubDirectory(String id, String shareName, String subDirectroy, String description) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        FileSystemShareParam param = new FileSystemShareParam();
        param.setShareName(shareName);
        if (subDirectroy != null && !"".equals(subDirectroy)) {
            param.setSubDirectory(subDirectroy);
        }
        if (description != null && !"".equals(description)) {
            param.setDescription(description);
        }
        client.fileSystems().share(uri(id), param);
        flash.put("info", MessagesUtils.get("resources.filesystem.subdir.add"));
        fileSystem(id);
    }

    /**
     * This method called when user Adds an access control entry from UI.
     * 
     * @param aclURL
     *            URL of the file system share.
     * @param shareACL
     *            This contains the form data from UI.
     */
    public static void addShareAcl(String aclURL, ShareACLForm shareACL, String formAccessControlList) {
        String fileSystem = null;
        String shareName = null;
        if (StringUtils.isNotBlank(aclURL)) {
            String[] parts = aclURL.split("/");
            if (parts.length == 7) {
                fileSystem = parts[3];
                shareName = parts[5];
            }
        }

        if (formAccessControlList == null || "".equals(formAccessControlList)) {
            flash.error(MessagesUtils.get("resources.filesystem.share.acl.invalid.settings"), null);
            listAcl(fileSystem, shareName);
        }
        FileCifsShareACLUpdateParams input = createCifsShareAclParams(formAccessControlList);
        ViPRCoreClient client = BourneUtil.getViprClient();
        try {
            client.fileSystems().updateShareACL(uri(fileSystem), shareName, input);
        } catch (ServiceErrorException e) {
            flash.error(e.getMessage(), null);
            listAcl(fileSystem, shareName);
        }
        flash.success(MessagesUtils.get(ADDED));
        listAcl(fileSystem, shareName);
    }

    /**
     * This method called When user clicks ACE hyper-link to modify.
     * 
     * @param id
     *            Access control entry.
     * 
     */
    public static void editAce(@Required String id) {
        String type = ShareACLForm.extractTypeFromId(id);
        String name = ShareACLForm.extractNameFromId(id);
        String domain = ShareACLForm.extractDomainFromId(id);
        String fileSystem = ShareACLForm.extractFileSystemFromId(id);
        String shareName = ShareACLForm.extractShareNameFromId(id);
        String permission = ShareACLForm.extractPermissionFromId(id);
        if ("null".equals(domain)) {
            domain = "";
        }

        ShareACLForm shareACL = new ShareACLForm();
        shareACL.type = type;
        shareACL.name = name;
        shareACL.domain = domain;
        shareACL.permission = permission;
        renderArgs.put("permissionOptions", StringOption.options(new String[] { "Read", "Change", "FullControl" }));
        renderArgs.put("fileSystemId", uri(fileSystem));
        ViPRCoreClient client = BourneUtil.getViprClient();
        FileShareRestRep restRep = client.fileSystems().get(uri(fileSystem));
        renderArgs.put("fileSystemName", restRep.getName());

        renderArgs.put("shareName", shareName);
        renderArgs.put("TYPE", type.toUpperCase());
        render(shareACL);
    }

    /**
     * This method called When user Modifies ACE.
     * 
     * @param shareACL
     *            Form Data from modify screen.
     * 
     */
    @FlashException(referrer = { "fileSystem" })
    public static void saveAce(ShareACLForm shareACL) {

        String name = params.get("name");
        String type = params.get("type");
        String domain = params.get("domain");
        String shareName = params.get("shareName");
        String fileSystemId = params.get("fileSystemId");
        String permission = shareACL.permission;
        List<ShareACL> ace = new ArrayList<ShareACL>();
        ShareACL shareAcl = new ShareACL();
        ShareACLs aclsToModify = new ShareACLs();

        if ("GROUP".equalsIgnoreCase(type)) {
            shareAcl.setGroup(name);
        } else {
            shareAcl.setUser(name);
        }
        shareAcl.setPermission(permission);
        if (domain != null && !"".equals(domain)) {
            shareAcl.setDomain(domain);
        }
        ace.add(shareAcl);
        aclsToModify.setShareACLs(ace);
        FileCifsShareACLUpdateParams input = new FileCifsShareACLUpdateParams();
        input.setAclsToModify(aclsToModify);

        ViPRCoreClient client = BourneUtil.getViprClient();
        client.fileSystems().updateShareACL(uri(fileSystemId), shareName, input);

        listAcl(fileSystemId, shareName);
    }

    private static FileCifsShareACLUpdateParams createCifsShareAclParams(String formData) {
        String[] uiAcls = formData.split(",");
        List<ShareACL> acls = new ArrayList<ShareACL>();
        for (String uiAce : uiAcls) {
            String[] uiData = uiAce.split("~~~");
            String uiType = uiData[0];
            String uiName = uiData[1];
            String uiDomain = uiData[2];
            String uiPermission = uiData[3];
            ShareACL shareAcl = new ShareACL();
            if ("GROUP".equalsIgnoreCase(uiType)) {
                shareAcl.setGroup(uiName);
            } else {
                shareAcl.setUser(uiName);
            }
            shareAcl.setPermission(uiPermission);
            if (uiDomain != null && !"".equals(uiDomain)) {
                shareAcl.setDomain(uiDomain);
            }

            acls.add(shareAcl);
        }
        FileCifsShareACLUpdateParams input = new FileCifsShareACLUpdateParams();
        ShareACLs aclsToAdd = new ShareACLs();
        aclsToAdd.setShareACLs(acls);
        input.setAclsToAdd(aclsToAdd);
        return input;
    }

    /**
     * This method called when Access Control List page is loaded.
     * 
     * @param fileSystem
     *            File System id of the provided File System.
     * @param shareName
     *            Name of the file share.
     */
    public static void listAcl(String fileSystem, String shareName) {
        renderArgs.put("dataTable", new ShareACLDataTable());
        renderArgs.put("fileSystem", uri(fileSystem));
        renderArgs.put("shareName", shareName);
        renderArgs.put("aclURL", "/file/filesystems/" + uri(fileSystem) + "/shares/" + shareName + "/acl");
        renderArgs.put("permissionOptions", StringOption.options(new String[] { "Read", "Change", "FullControl" }));
        renderArgs.put("fileSystemId", uri(fileSystem));
        ViPRCoreClient client = BourneUtil.getViprClient();
        FileShareRestRep restRep = client.fileSystems().get(uri(fileSystem));
        renderArgs.put("fileSystemName", restRep.getName());
        ShareACLForm shareACL = new ShareACLForm();
        render(shareACL);
    }

    /**
     * This method called during population of Datatable for ACL data.
     * 
     * @param aclURL
     *            URL of the file system share.
     */
    public static void listAclJson(String aclURL) {
        String fileSystem = null;
        String shareName = null;
        if (StringUtils.isNotBlank(aclURL)) {
            String[] parts = aclURL.split("/");
            if (parts.length == 7) {
                fileSystem = parts[3];
                shareName = parts[5];
            }
        }
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<ShareACL> shareAcls = client.fileSystems().getShareACLs(uri(fileSystem), shareName);
        List<ShareACLDataTable.AclInfo> acl = Lists.newArrayList();
        for (ShareACL shareAcl : shareAcls) {
            String userOrGroup = shareAcl.getUser();
            String type = "User";
            if (shareAcl.getGroup() != null && shareAcl.getGroup() != "") {
                type = "Group";
                userOrGroup = shareAcl.getGroup();
            }
            acl.add(new ShareACLDataTable.AclInfo(userOrGroup, type, shareAcl.getPermission(), fileSystem, shareName, shareAcl.getDomain()));
        }

        renderJSON(DataTablesSupport.createJSON(acl, params));
    }

    /**
     * This method called When user selects ACLs and hit delete button.
     * 
     * @param aclURL
     *            URL of the file system share.
     * @param ids
     *            ids of the selected ACL
     */
    @FlashException(referrer = { "fileSystem" })
    public static void removeAcl(String aclUrl, @As(",") String[] ids) {
        ShareACLs aclsToDelete = new ShareACLs();
        List<ShareACL> shareAcls = new ArrayList<ShareACL>();
        String fileSystem = null;
        String shareName = null;
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                String type = ShareACLForm.extractTypeFromId(id);
                String name = ShareACLForm.extractNameFromId(id);
                String domain = ShareACLForm.extractDomainFromId(id);
                fileSystem = ShareACLForm.extractFileSystemFromId(id);
                shareName = ShareACLForm.extractShareNameFromId(id);
                ShareACL ace = new ShareACL();
                if ("Group".equalsIgnoreCase(type)) {
                    ace.setGroup(name);
                } else {
                    ace.setUser(name);
                }
                if (domain != null && !"".equals(domain) && !"null".equals(domain)) {
                    ace.setDomain(domain);
                }
                shareAcls.add(ace);
            }
            aclsToDelete.setShareACLs(shareAcls);
            FileCifsShareACLUpdateParams input = new FileCifsShareACLUpdateParams();
            input.setAclsToDelete(aclsToDelete);
            ViPRCoreClient client = BourneUtil.getViprClient();
            client.fileSystems().updateShareACL(uri(fileSystem), shareName, input);
        }
        flash.success(MessagesUtils.get(DELETED));
        listAcl(fileSystem, shareName);

    }

    /**
     * This static class holds the form data for the ACL page.
     */
    public static class ShareACLForm {

        private static final String ID_DELIMITER = "~~~~";
        public String domain;
        public String id;
        @Required
        public String name;
        @Required
        public String type = "USER";
        @Required
        public String permission;

        public FileCifsShareACLUpdateParams createCifsShareAclParams() {
            ShareACL shareAcl = new ShareACL();
            if ("GROUP".equalsIgnoreCase(type.trim())) {
                shareAcl.setGroup(name.trim());
            } else {
                shareAcl.setUser(name.trim());
            }
            shareAcl.setPermission(permission);
            if (domain.trim() != null && !"".equals(domain.trim())) {
                shareAcl.setDomain(domain.trim());
            }
            List<ShareACL> acls = new ArrayList<ShareACL>();
            acls.add(shareAcl);
            FileCifsShareACLUpdateParams input = new FileCifsShareACLUpdateParams();
            ShareACLs aclsToAdd = new ShareACLs();
            aclsToAdd.setShareACLs(acls);
            input.setAclsToAdd(aclsToAdd);
            return input;
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            Validation.required(formName + ".name", name);
            Validation.required(formName + ".type", type);
            Validation.required(formName + ".permission", permission);
            if (name == null || "".equals(name)) {
                Validation.addError(formName + ".name", "resources.filesystem.share.acl.invalid.name");
            }
        }

        public static String createId(String name, String type, String fileSystem, String shareName, String domain, String permission) {
            return name + ID_DELIMITER + type + ID_DELIMITER + fileSystem + ID_DELIMITER + shareName +
                    ID_DELIMITER + domain + ID_DELIMITER + permission;
        }

        public static String extractNameFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 6) {
                    return parts[0];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractTypeFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 6) {
                    return parts[1];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractFileSystemFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 6) {
                    return parts[2];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractShareNameFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 6) {
                    return parts[3];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractDomainFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 6) {
                    return parts[4];
                } else {
                    return id;
                }
            }
            return null;
        }

        public static String extractPermissionFromId(String id) {
            if (StringUtils.isNotBlank(id)) {
                String[] parts = id.split(ID_DELIMITER);
                if (parts.length == 6) {
                    return parts[5];
                } else {
                    return id;
                }
            }
            return null;
        }

    }
    
	public static class NfsACLForm {

		private static final String ID_DELIMITER = "~~~~";
		public String domain;
		public String id;
		@Required
		public String name;
		@Required
		public String type;
		@Required
		public String permissionType;
		@Required
		public Set<String> permissions;

		public NfsACLUpdateParams NfsACLUpdateParams() {
			NfsACE nfsAce = new NfsACE();
			nfsAce.setType(type.trim());
			nfsAce.setUser(name.trim());
			nfsAce.setPermissions(permissions.toString());
			nfsAce.setPermissionType(permissionType);
			if (domain.trim() != null && !"".equals(domain.trim())) {
				nfsAce.setDomain(domain.trim());
			}
			List<NfsACE> aces = Lists.newArrayList();
			aces.add(nfsAce);
			NfsACLUpdateParams input = new NfsACLUpdateParams();
			input.setAcesToAdd(aces);
			return input;
		}

		public void validate(String formName) {
			Validation.valid(formName, this);
			Validation.required(formName + ".name", name);
			Validation.required(formName + ".type", type);
			Validation.required(formName + ".permissionType", permissionType);
			Validation.required(formName + ".permissions", permissions);
			if (name == null || "".equals(name)) {
				Validation.addError(formName + ".name",
						"resources.filesystem.share.acl.invalid.name");
			}
		}

		public static String createId(String name, String type,
				String fileSystem, String subDir, String domain,
				String permissions, String fsMountPath, String permissionType) {
			return name + ID_DELIMITER + type + ID_DELIMITER + fileSystem
					+ ID_DELIMITER + subDir + ID_DELIMITER + domain
					+ ID_DELIMITER + permissions + ID_DELIMITER + fsMountPath + ID_DELIMITER + permissionType;
		}

		public static String extractNameFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[0];
				} else {
					return id;
				}
			}
			return null;
		}

		public static String extractTypeFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[1];
				} else {
					return id;
				}
			}
			return null;
		}

		public static String extractFileSystemFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[2];
				} else {
					return id;
				}
			}
			return null;
		}

		public static String extractSubDirFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[3];
				} else {
					return id;
				}
			}
			return null;
		}

		public static String extractDomainFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[4];
				} else {
					return id;
				}
			}
			return null;
		}

		public static String extractPermissionsFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[5];
				} else {
					return id;
				}
			}
			return null;
		}

		public static String extractMounPathFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[6];
				} else {
					return id;
				}
			}
			return null;
		}
		
		public static String extractPermissionTypeFromId(String id) {
			if (StringUtils.isNotBlank(id)) {
				String[] parts = id.split(ID_DELIMITER);
				if (parts.length == 8) {
					return parts[7];
				} else {
					return id;
				}
			}
			return null;
		}

	}

    public class Quota {
        private String name;
        private String securityStyle;
        private Boolean oplock;
        private String size;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSecurityStyle() {
            return securityStyle;
        }

        public void setSecurityStyle(String securityStyle) {
            this.securityStyle = securityStyle;
        }

        public Boolean getOplock() {
            return oplock;
        }

        public void setOplock(Boolean oplock) {
            this.oplock = oplock;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

    }
}
