/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.FileMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapFileSnapshot;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;
import com.emc.storageos.api.service.impl.resource.utils.CifsShareUtility;
import com.emc.storageos.api.service.impl.resource.utils.ExportVerificationUtility;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.PermissionsEnforcingResourceFilter;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.api.service.impl.response.ProjOwnedSnapResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileSnapshotBulkRep;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemExportList;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.FileSystemShareList;
import com.emc.storageos.model.file.FileSystemShareParam;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.file.SnapshotCifsShareACLUpdateParams;
import com.emc.storageos.model.file.SnapshotExportUpdateParams;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;

@Path("/file/snapshots")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.ANY }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.ANY })
public class FileSnapshotService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(FileService.class);

    private static final String EVENT_SERVICE_TYPE = "fileSnapshot";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private FileStorageScheduler _fileScheduler;

    public void setFileScheduler(FileStorageScheduler fileScheduler) {
        _fileScheduler = fileScheduler;
    }

    /**
     * Get info for file share snapshot
     * 
     * @param id the URN of a ViPR Snapshot
     * @brief Show file snapshot
     * @return File snapshot details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public FileSnapshotRestRep getSnapshot(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);
        return map(snap);
    }

    /**
     * Get all Snapshots matching the path
     * 
     * @QueryParam mountpath
     * @brief Show Snapshots
     * @return Snapshot details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public List<FileSnapshotRestRep> getSnapshots(@QueryParam("mountpath") String mountPath) {
        List<FileSnapshotRestRep> snapRepList = new ArrayList<FileSnapshotRestRep>();

        List<URI> ids = _dbClient.queryByType(Snapshot.class, true);
        Iterator<Snapshot> iter = _dbClient.queryIterativeObjects(Snapshot.class, ids);
        _log.info("getSnapshots call ... with mountpath {}", mountPath);
        while (iter.hasNext()) {
            Snapshot snap = iter.next();
            if (snap != null) {
                if (mountPath != null) {
                    if (snap.getMountPath().equalsIgnoreCase(mountPath)) {
                        snapRepList.add(map(snap));

                    } else {
                        _log.info("Skip this Snapshot Mount Path doesnt match {} {}", snap.getMountPath(), mountPath);
                    }
                } else {
                    _log.info("Mountpath query param is null");
                    snapRepList.add(map(snap));
                }
            }
        }
        return snapRepList;
    }

    @Override
    protected Snapshot queryResource(URI id) {
        ArgValidator.checkUri(id);
        Snapshot snap = _permissionsHelper.getObjectById(id, Snapshot.class);
        ArgValidator.checkEntityNotNull(snap, id, isIdEmbeddedInURL(id));
        return snap;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Snapshot snapshot = queryResource(id);

        URI projectUri = snapshot.getProject().getURI();
        ArgValidator.checkUri(projectUri);

        Project project = _permissionsHelper.getObjectById(projectUri, Project.class);
        ArgValidator.checkEntityNotNull(project, projectUri, isIdEmbeddedInURL(projectUri));
        return project.getTenantOrg().getURI();
    }

    private void verifyFileSnapshotExports(Snapshot snap, FileSystemExportParam param, String path) {
        FSExportMap snapExports = snap.getFsExports();
        URI id = snap.getId();

        if (null != snapExports) {

            Iterator<FileExport> it = snapExports.values().iterator();

            while (it.hasNext()) {
                FileExport fileExport = it.next();
                // If no key found then it should process as it is.
                boolean isAlreadyExportedToSameEndpoint = false;
                if (fileExport.getPath().equals(path)) {
                    List<String> availableEndpoints = fileExport.getClients();
                    List<String> providedEndpoints = param.getEndpoints();
                    for (String providedEndpoint : providedEndpoints) {
                        if (availableEndpoints.contains(providedEndpoint)) {
                            isAlreadyExportedToSameEndpoint = true;
                            break;
                        }
                    }
                    if (isAlreadyExportedToSameEndpoint) {
                        _log.info(String.format(
                                "Existing Export params for Snapshot id: %1$s,  SecurityType: %2$s, " +
                                        "Permissions: %3$s, Root user mapping: %4$s, ",
                                id, fileExport.getSecurityType(), fileExport.getPermissions(), fileExport.getRootUserMapping()));

                        _log.info(String.format(
                                "Recieved Export params for Snapshot id: %1$s,  SecurityType: %2$s, " +
                                        "Permissions: %3$s, Root user mapping: %4$s, ",
                                id, param.getSecurityType(), param.getPermissions(), param.getRootUserMapping()));
                        if (!fileExport.getPermissions().equals(param.getPermissions())) {
                            throw APIException.badRequests.updatingSnapshotExportNotAllowed("permissions");
                        }
                        if (!fileExport.getSecurityType().equals(param.getSecurityType())) {
                            throw APIException.badRequests.updatingSnapshotExportNotAllowed("type");
                        }
                        if (!fileExport.getRootUserMapping().equals(param.getRootUserMapping())) {
                            throw APIException.badRequests.updatingSnapshotExportNotAllowed("root_user");
                        }
                    }
                }
            }
        }
    }

    /**
     * Add file share snapshot export.
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id the URN of a ViPR Snapshot
     * @param param File system export parameters
     * @brief Create file snapshot export
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep export(@PathParam("id") URI id, FileSystemExportParam param)
            throws InternalException {

        _log.info("Snapshot Export request recieved {}", id);
        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);
        ArgValidator.checkEntity(snap, id, true);

        if (!param.getPermissions().equals(FileShareExport.Permissions.ro.name())) {
            throw APIException.badRequests.snapshotExportPermissionReadOnly();
        }

        ArgValidator.checkFieldValueFromEnum(param.getSecurityType(), "type",
                EnumSet.allOf(FileShareExport.SecurityTypes.class));

        ArgValidator.checkFieldValueFromEnum(param.getProtocol(), "protocol",
                EnumSet.allOf(StorageProtocol.File.class));

        FileService.validateIpInterfacesRegistered(param.getEndpoints(), _dbClient);
        FileShare fs = _permissionsHelper.getObjectById(snap.getParent(), FileShare.class);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());

        // Locate storage port for exporting file snap
        // We use file system in the call since file snap belongs to the same neighbourhood as its parent file system
        StoragePort sport = _fileScheduler.placeFileShareExport(fs, param.getProtocol(), param.getEndpoints());

        String path = snap.getPath();
        String mountPath = snap.getMountPath();

        _log.info("Check whether there is a NFS Export already for the path {}", path);
        FSExportMap exportMap = snap.getFsExports();
        if (exportMap != null) {

            Iterator it = snap.getFsExports().keySet().iterator();
            boolean exportExists = false;
            while (it.hasNext()) {
                String fsExpKey = (String) it.next();
                FileExport fileExport = snap.getFsExports().get(fsExpKey);
                _log.info("Snap export key {} does it exist ? {}", fsExpKey + ":" + fileExport.getPath(), exportExists);
                if (fileExport.getPath().equalsIgnoreCase(path)) {
                    exportExists = true;
                    _log.info("Snap export key {} exist {}", fsExpKey + ":" + fileExport.getPath(), exportExists);
                    break;
                }
            }
            if (exportExists) {
                throw APIException.badRequests.snapshotHasExistingExport();
            }
        }

        verifyFileSnapshotExports(snap, param, path);

        FileShareExport export = new FileShareExport(param.getEndpoints(), param.getSecurityType(), param.getPermissions(),
                param.getRootUserMapping(), param.getProtocol(), sport.getPortGroup(), sport.getPortNetworkId(), path, mountPath,
                param.getSubDirectory(), param.getComments());
        _log.info("FileSnapExport --- FileSnap id: " + id + ", Clients: " + export.getClients() + ", StoragePort:" + sport.getPortName()
                + ", StoragePort :" + export.getStoragePort() + ", SecurityType: " + export.getSecurityType() +
                ", Permissions: " + export.getPermissions() + ", Root user mapping: " + export.getRootUserMapping() + ",Protocol: "
                + export.getProtocol() +
                ",path:" + export.getPath() + ",mountPath:" + export.getMountPath());

        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(),
                task, ResourceOperationTypeEnum.EXPORT_FILE_SNAPSHOT);
        controller.export(device.getId(), snap.getId(), Arrays.asList(export), task);
        auditOp(OperationTypeEnum.EXPORT_FILE_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                snap.getId().toString(), device.getId().toString(), export.getClients(), param.getSecurityType(),
                param.getPermissions(), param.getRootUserMapping(), param.getProtocol());

        return toTask(snap, task, op);
    }

    /**
     * @Deprecated use {id}/export instead
     *             Get file share snapshots exports
     * @param id the URN of a ViPR Snapshot
     * @brief List file snapshot exports.This method is deprecated.
     *        <p>
     *        Use /file/snapshots/{id}/export instead.
     * @return List of file share snapshot exports
     */
    @Deprecated
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public FileSystemExportList getFileSystemSnapshotExportList(@PathParam("id") URI id) {

        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snapshot = queryResource(id);
        FileSystemExportList fileExportListResponse = new FileSystemExportList();

        if (snapshot.getInactive()) {
            return fileExportListResponse;
        }
        // Get export map from snapshot
        FSExportMap exportMap = snapshot.getFsExports();

        Collection<FileExport> fileExports = new ArrayList<FileExport>();
        if (exportMap != null) {
            fileExports = exportMap.values();
        }

        // Process each export from the map and its data to exports in response list.
        for (FileExport fileExport : fileExports) {
            FileSystemExportParam fileExportParam = new FileSystemExportParam();

            fileExportParam.setEndpoints(fileExport.getClients());
            fileExportParam.setSecurityType(fileExport.getSecurityType());
            fileExportParam.setPermissions(fileExport.getPermissions());
            fileExportParam.setRootUserMapping(fileExport.getRootUserMapping());
            fileExportParam.setProtocol(fileExport.getProtocol());
            fileExportParam.setMountPoint(fileExport.getMountPoint());

            fileExportListResponse.getExportList().add(fileExportParam);

        }
        return fileExportListResponse;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/export")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ExportRules getSnapshotExportRules(@PathParam("id") URI id,
            @QueryParam("allDirs") boolean allDirs,
            @QueryParam("subDir") String subDir) {

        _log.info("Request recieved to list snapshotExports with Id : {}",
                new Object[] { id });

        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snapshot = queryResource(id);

        ExportRules exportRules = new ExportRules();
        List<ExportRule> exportRule = new ArrayList<>();

        // Query All Export Rules Specific to a File System.
        List<FileExportRule> exports = queryDBSnapshotExports(snapshot);
        _log.info("Number of existing snapshot exports found : {} ", exports.size());

        // All EXPORTS
        for (FileExportRule rule : exports) {
            ExportRule expRule = new ExportRule();
            // Copy Props
            copyPropertiesToSave(rule, expRule, snapshot);
            exportRule.add(expRule);
        }

        _log.info("Number of snapshot export rules returning {}", exportRule.size());
        exportRules.setExportRules(exportRule);
        return exportRules;

    }

    private List<FileExportRule> queryDBSnapshotExports(Snapshot snapshot) {
        _log.info("Querying all ExportRules Using Snapshot Id {}", snapshot.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getSnapshotExportRulesConstraint(snapshot.getId());
            List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileExportRule.class,
                    containmentConstraint);
            return fileExportRules;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return null;
    }

    private void copyPropertiesToSave(FileExportRule orig, ExportRule dest, Snapshot snapshot) {

        dest.setSnapShotID(snapshot.getId());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        dest.setReadWriteHosts(orig.getReadWriteHosts());
        dest.setRootHosts(orig.getRootHosts());
        dest.setMountPoint(orig.getMountPoint());
    }

    /**
     * Remove file share snapshot export.
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id the URN of a ViPR Snapshot
     * @param protocol Protocol valid values - NFS,NFSv4,CIFS
     * @param securityType Security type valid values - sys,krb5,krb5i,krb5p
     * @param permissions Permissions valid values - ro,rw,root
     * @param rootUserMapping Root user mapping
     * @brief Delete file snapshot export
     * @return Task resource representation
     * @throws InternalException
     */
    @DELETE
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports/{protocol},{secType},{perm},{rootMapping}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep unexport(@PathParam("id") URI id,
            @PathParam("protocol") String protocol, @PathParam("secType") String securityType,
            @PathParam("perm") String permissions,
            @PathParam("rootMapping") String rootUserMapping) throws InternalException {

        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);
        FileShare fs = _permissionsHelper.getObjectById(snap.getParent(), FileShare.class);

        ArgValidator.checkFieldNotNull(protocol, "protocol");
        ArgValidator.checkFieldNotNull(securityType, "secType");
        ArgValidator.checkFieldNotNull(permissions, "perm");
        ArgValidator.checkFieldNotNull(rootUserMapping, "rootMapping");

        if (snap.getFsExports() == null || snap.getFsExports().isEmpty()) {
            // No export to unexport, return success.
            String message = "Export does not exist";
            return getSuccessResponse(snap, task, ResourceOperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, message);
        }

        ArgValidator.checkEntity(snap, id, isIdEmbeddedInURL(id));
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());

        String path = snap.getPath();

        _log.info(String.format("securityType %1$s, permissions %2$s, rootMapping %3$s %4$s", securityType, permissions, rootUserMapping,
                path));
        FileExport fileSnapExport = snap.getFsExports().get(
                FileExport.exportLookupKey(protocol, securityType, permissions, rootUserMapping, path));

        if (fileSnapExport == null) {
            // No export to unexport, return success.
            String message = "Export does not exist";
            return getSuccessResponse(snap, task, ResourceOperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, message);
        }
        List<String> endpoints = new ArrayList<String>(); // empty list for unexport
        FileShareExport export = new FileShareExport(endpoints, securityType, permissions, rootUserMapping, protocol,
                fileSnapExport.getStoragePortName(), fileSnapExport.getStoragePort(), fileSnapExport.getPath());
        export.setIsilonId(fileSnapExport.getIsilonId());
        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(), task, ResourceOperationTypeEnum.UNEXPORT_FILE_SNAPSHOT);
        controller.unexport(device.getId(), snap.getId(), Arrays.asList(export), task);
        auditOp(OperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                snap.getId().toString(), device.getId().toString(), securityType, permissions, rootUserMapping, protocol);

        return toTask(snap, task, op);
    }

    @DELETE
    @Path("/{id}/export")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep deleteSnapshotExportRules(@PathParam("id") URI id) {

        // log input received.
        _log.info("Delete Snapshot Export Rules : request received for {}", new Object[] { id });
        String task = UUID.randomUUID().toString();
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snapshot = queryResource(id);
        FileShare fileShare = _permissionsHelper.getObjectById(snapshot.getParent(), FileShare.class);

        ArgValidator.checkEntity(snapshot, id, isIdEmbeddedInURL(id));

        /* check if the Snapshot has any export rules on it */
        List<FileExportRule> exports = queryDBSnapshotExports(snapshot);
        if (exports == null || exports.isEmpty()) {
            _log.error("Error Processing Export Updates for snapshot {} doesnot have exports", snapshot.getName());
            throw APIException.badRequests.snapshotHasNoExport(snapshot.getId());
        }

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fileShare.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        String path = snapshot.getPath();
        _log.info("Export path found {} ", path);

        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snapshot.getId(), task,
                ResourceOperationTypeEnum.UNEXPORT_FILE_SNAPSHOT);

        try {

            controller.deleteExportRules(device.getId(), snapshot.getId(), false, null, task);

            auditOp(OperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                    snapshot.getId().toString(), device.getId().toString(), false, null);

            return toTask(snapshot, task, op);

        }

        catch (BadRequestException e) {
            _log.error("Error Processing Export Updates {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Processing Export Updates {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }

    }

    /**
     * Returns a task when there is no export to unexport
     * 
     * @param snap Snapshot whose export has to be deleted
     * @param task
     * @return Task resource representation
     */
    private TaskResourceRep getSuccessResponse(Snapshot snap, String task, ResourceOperationTypeEnum type, String message) {
        Operation op = new Operation();
        op.setResourceType(type);
        op.ready(message);
        _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(), task, op);
        return toTask(snap, task, op);
    }

    /**
     * Lists all SMB shares for the specified snapshot.
     * 
     * @param id the URN of a ViPR Snapshot
     * @brief List file snapshot SMB shares
     * @return List of SMB shares
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public FileSystemShareList getFileSystemShareList(@PathParam("id") URI id) {

        _log.info(String.format("Get list of SMB file shares for snapshot: %1$s", id));
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);
        FileSystemShareList fileShareListResponse = new FileSystemShareList();

        if (snap.getInactive()) {
            return fileShareListResponse;
        }

        // Get SMB share map from snapshot
        SMBShareMap smbShareMap = snap.getSMBFileShares();

        Collection<SMBFileShare> smbShares = new ArrayList<SMBFileShare>();
        if (smbShareMap != null) {
            smbShares = smbShareMap.values();
        }

        // Process each share from the map and add its data to shares in response list.
        for (SMBFileShare smbShare : smbShares) {
            SmbShareResponse shareParam = new SmbShareResponse();

            shareParam.setShareName(smbShare.getName());
            shareParam.setDescription(smbShare.getDescription());
            shareParam.setMaxUsers(Integer.toString(smbShare.getMaxUsers()));
            // Check for "unlimited"
            if (shareParam.getMaxUsers().equals("-1")) {
                shareParam.setMaxUsers(FileService.UNLIMITED_USERS);
            }
            shareParam.setPermissionType(smbShare.getPermissionType());
            shareParam.setPermission(smbShare.getPermission());
            shareParam.setMountPoint(smbShare.getMountPoint());
            shareParam.setPath(smbShare.getPath());

            fileShareListResponse.getShareList().add(shareParam);
        }

        return fileShareListResponse;
    }

    /**
     * Creates SMB file share.
     * <p>
     * Note: This is an asynchronous operation.
     * 
     * @param id the URN of a ViPR Snapshot
     * @param param File system share parameters
     * @brief Create file snapshot SMB share
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep share(@PathParam("id") URI id, FileSystemShareParam param)
            throws InternalException {

        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        ArgValidator.checkFieldNotNull(param.getShareName(), "name");
        ArgValidator.checkFieldNotEmpty(param.getShareName(), "name");
        Snapshot snap = queryResource(id);
        FileShare fs = _permissionsHelper.getObjectById(snap.getParent(), FileShare.class);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());

        ArgValidator.checkEntity(snap, id, isIdEmbeddedInURL(id));

        // Let us make sure that a share with the same name does not already exist.
        String shareName = param.getShareName();
        if (CifsShareUtility.doesShareExist(snap, shareName)) {
            _log.error("CIFS share: {}, already exists", shareName);
            throw APIException.badRequests.duplicateEntityWithField("CIFS share", "name");
        }

        // If value of permission is not provided, set the value to read-only
        if (param.getPermission() == null || param.getPermission().isEmpty()) {
            param.setPermission(FileSMBShare.Permission.read.name());
        }

        if (!param.getPermission().equals(FileSMBShare.Permission.read.name())) {
            throw APIException.badRequests.snapshotSMBSharePermissionReadOnly();
        }

        // Locate storage port for sharing snapshot
        // Select IP port of the storage array, owning the parent file system, which belongs to the same varray as the file system.
        // We use file system in the call since file snap belongs to the same neighbourhood as its parent file system
        StoragePort sport = _fileScheduler.placeFileShareExport(fs, StorageProtocol.File.CIFS.name(), null);

        // Check if maxUsers is "unlimited" and set it to -1 in this case.
        if (param.getMaxUsers().equalsIgnoreCase(FileService.UNLIMITED_USERS)) {
            param.setMaxUsers("-1");
        }

        String path = snap.getPath();

        _log.info("Path {}", path);
        _log.info("Param Share Name : {} SubDirectory : {}", param.getShareName(), param.getSubDirectory());

        boolean isSubDirPath = false;

        if (param.getSubDirectory() != null && param.getSubDirectory().length() > 0) {
            path += "/" + param.getSubDirectory();
            isSubDirPath = true;
            _log.info("Sub-directory path {}", path);
        }

        FileSMBShare smbShare = new FileSMBShare(param.getShareName(), param.getDescription(),
                param.getPermissionType(), param.getPermission(), param.getMaxUsers(), null, path);
        smbShare.setStoragePortName(sport.getPortName());
        smbShare.setStoragePortNetworkId(sport.getPortNetworkId());
        smbShare.setStoragePortGroup(sport.getPortGroup());
        smbShare.setSubDirPath(isSubDirPath);

        _log.info(String.format(
                "Create snapshot share --- Snap id: %1$s, Share name: %2$s, StoragePort: %3$s, PermissionType: %4$s, " +
                        "Permissions: %5$s, Description: %6$s, maxUsers: %7$s",
                id, smbShare.getName(), sport.getPortName(), smbShare.getPermissionType(), smbShare.getPermission(),
                smbShare.getDescription(),
                smbShare.getMaxUsers()));

        _log.info("SMB share path: {}", smbShare.getPath());

        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(), task,
                ResourceOperationTypeEnum.CREATE_FILE_SNAPSHOT_SHARE);

        controller.share(device.getId(), snap.getId(), smbShare, task);
        auditOp(OperationTypeEnum.CREATE_FILE_SNAPSHOT_SHARE, true, AuditLogManager.AUDITOP_BEGIN,
                smbShare.getName(), smbShare.getPermissionType(), smbShare.getPermission(),
                smbShare.getMaxUsers(), smbShare.getDescription(), snap.getId().toString());
        return toTask(snap, task, op);
    }

    /**
     * Deletes SMB share.
     * <p>
     * Note: This is an asynchronous operation.
     * 
     * @param id the URN of a ViPR Snapshot
     * @param shareName SMB share name
     * @brief Delete file snapshot SMB share
     * @return Task resource representation
     * @throws InternalException
     */
    @DELETE
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares/{shareName}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep deleteShare(@PathParam("id") URI id,
            @PathParam("shareName") String shareName) throws InternalException {

        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);
        FileShare fs = _permissionsHelper.getObjectById(snap.getParent(), FileShare.class);

        ArgValidator.checkFieldNotNull(shareName, "shareName");
        ArgValidator.checkEntity(snap, id, isIdEmbeddedInURL(id));
        String task = UUID.randomUUID().toString();

        if (!CifsShareUtility.doesShareExist(snap, shareName)) {
            _log.error("CIFS share does not exist", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(id, shareName);
        }

        SMBFileShare smbShare = snap.getSMBFileShares().get(shareName);

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());
        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(),
                task, ResourceOperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE);
        FileSMBShare fileSMBShare = new FileSMBShare(shareName, smbShare.getDescription(),
                smbShare.getPermissionType(), smbShare.getPermission(), Integer.toString(smbShare
                        .getMaxUsers()),
                smbShare.getNativeId(), smbShare.getPath());
        controller.deleteShare(device.getId(), snap.getId(), fileSMBShare, task);
        auditOp(OperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE, true, AuditLogManager.AUDITOP_BEGIN,
                smbShare.getName(), smbShare.getPermissionType(), smbShare.getPermission(),
                smbShare.getMaxUsers(), smbShare.getDescription(), snap.getId().toString());

        return toTask(snap, task, op);
    }

    /**
     * API to update ACLs of an existing share
     * 
     * @param id the file system URI
     * @param shareName name of the share
     * @param param request payload object of type <code>com.emc.storageos.model.file.CifsShareACLUpdateParams</code>
     * @return TaskResponse
     * @throws InternalException
     */

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares/{shareName}/acl")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateSnapshotShareACL(@PathParam("id") URI id,
            @PathParam("shareName") String shareName,
            SnapshotCifsShareACLUpdateParams param) throws InternalException {

        _log.info("Update snapshot share acl request received. Snapshot: {}, Share: {}",
                id.toString(), shareName);
        _log.info("Request body: {}", param.toString());

        ArgValidator.checkFieldNotNull(shareName, "shareName");
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");

        Snapshot snapshot = queryResource(id);
        ArgValidator.checkEntity(snapshot, id, isIdEmbeddedInURL(id));

        if (!CifsShareUtility.doesShareExist(snapshot, shareName)) {
            _log.error("CIFS share does not exist {}", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(snapshot.getId(), shareName);
        }

        FileShare fs = _permissionsHelper.getObjectById(snapshot.getParent(), FileShare.class);

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());

        CifsShareUtility.checkForUpdateShareACLOperationOnStorage(
                device.getSystemType(), OperationTypeEnum.UPDATE_FILE_SNAPSHOT_SHARE_ACL.name());

        String task = UUID.randomUUID().toString();

        // Check for VirtualPool whether it has CIFS enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.CIFS.name())) {
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool doesn't support "
                    + StorageProtocol.File.CIFS.name() + " protocol");
        }

        // Validate the input
        CifsShareUtility util = new CifsShareUtility(_dbClient, null, snapshot, shareName);
        util.verifyShareACLs(param);

        _log.info("Request payload verified. No errors found.");

        FileController controller = getController(FileController.class, device.getSystemType());

        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snapshot.getId(),
                task, ResourceOperationTypeEnum.UPDATE_FILE_SNAPSHOT_SHARE_ACL);

        controller.updateShareACLs(device.getId(), snapshot.getId(), shareName, param, task);

        auditOp(OperationTypeEnum.UPDATE_FILE_SNAPSHOT_SHARE_ACL, true, AuditLogManager.AUDITOP_BEGIN,
                snapshot.getId().toString(), device.getId().toString(), param);

        return toTask(snapshot, task, op);
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares/{shareName}/acl")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ShareACLs getSnapshotShareACLs(@PathParam("id") URI id,
            @PathParam("shareName") String shareName) {

        _log.info("Request recieved to get ACLs with Id: {}  shareName: {}",
                id, shareName);

        // Validate the FS id
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        ArgValidator.checkFieldNotNull(shareName, "shareName");
        Snapshot snapshot = queryResource(id);

        ArgValidator.checkEntity(snapshot, id, isIdEmbeddedInURL(id));

        if (!CifsShareUtility.doesShareExist(snapshot, shareName)) {
            _log.error("CIFS share does not exist {}", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(snapshot.getId(), shareName);
        }

        ShareACLs acls = new ShareACLs();
        CifsShareUtility util = new CifsShareUtility(_dbClient, null, snapshot, shareName);
        List<ShareACL> shareAclList = util.queryExistingShareACLs();

        _log.info("Number of existing ACLs found : {} ", shareAclList.size());
        if (!shareAclList.isEmpty()) {
            acls.setShareACLs(shareAclList);
        }
        return acls;

    }

    @DELETE
    @Path("/{id}/shares/{shareName}/acl")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep deleteSnapshotShareACL(@PathParam("id") URI id,
            @PathParam("shareName") String shareName) {

        // log input received.
        _log.info("Delete ACL of share: Request received for {}, of file snapshot {}",
                shareName, id);
        String taskId = UUID.randomUUID().toString();
        // Validate the snapshot id.
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        ArgValidator.checkFieldNotNull(shareName, "shareName");
        Snapshot snapshot = queryResource(id);

        ArgValidator.checkEntity(snapshot, id, isIdEmbeddedInURL(id));

        if (!CifsShareUtility.doesShareExist(snapshot, shareName)) {
            _log.error("CIFS share does not exist {}", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(snapshot.getId(), shareName);
        }

        FileShare fs = _permissionsHelper.getObjectById(snapshot.getParent(), FileShare.class);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());

        CifsShareUtility.checkForUpdateShareACLOperationOnStorage(
                device.getSystemType(), OperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE_ACL.name());

        FileController controller = getController(FileController.class, device.getSystemType());

        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snapshot.getId(),
                taskId, ResourceOperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE_ACL);
        op.setDescription("Delete ACL of Snapshot Cifs share");

        controller.deleteShareACLs(device.getId(), snapshot.getId(), shareName, taskId);

        auditOp(OperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE_ACL,
                true, AuditLogManager.AUDITOP_BEGIN,
                snapshot.getId().toString(), device.getId().toString(), shareName);

        return toTask(snapshot, taskId, op);
    }

    /**
     * Call will restore this snapshot to the File system that it is associated with.
     * 
     * @param id [required] - the URN of a ViPR file snapshot to restore from
     * @brief Restore file snapshot
     * @return TaskResourceRep - Task resource object for tracking this operation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    @Path("/{id}/restore")
    public TaskResourceRep restore(@PathParam("id") URI id)
            throws InternalException {

        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);
        FileShare fs = _permissionsHelper.getObjectById(snap.getParent(), FileShare.class);
        String task = UUID.randomUUID().toString();
        Operation op = null;
        if ((snap != null) && (!(snap.getInactive()))) {
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
            StorageSystem.Type storageSystemType = StorageSystem.Type.valueOf(device.getSystemType());

            if (storageSystemType.equals(DiscoveredDataObject.Type.isilon)) {
                    _log.error("Invalid Operation. Restore snapshot is not supported by ISILON");
                    throw APIException.badRequests.isilonSnapshotRestoreNotSupported();
            }
            FileController controller = getController(FileController.class,
                    device.getSystemType());
            _log.info(String.format(
                    "Snapshot restore --- Snapshot id: %1$s, FileShare: %2$s, task %3$s", id, fs.getId(), task));
            _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                    task, ResourceOperationTypeEnum.RESTORE_FILE_SNAPSHOT);
            op = _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(),
                    task, ResourceOperationTypeEnum.RESTORE_FILE_SNAPSHOT);

            controller.restoreFS(device.getId(), fs.getId(), snap.getId(), task);
            auditOp(OperationTypeEnum.RESTORE_FILE_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                    snap.getId().toString(), fs.getId().toString());
        } else {
            StringBuilder msg = new StringBuilder("Attempt to use deleted snapshot: " + snap.getName());
            msg.append(" to restore File: " + fs.getName());
            op = new Operation();
            ServiceCoded coded = ServiceError.buildServiceError(
                    ServiceCode.API_BAD_REQUEST, msg.toString());
            op.error(coded);
            op.setMessage(msg.toString());
            op = _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(), task, op);
            _log.error(msg.toString());
        }

        return toTask(snap, task, op);
    }

    /**
     * Deactivate filesystem snapshot, this will move the snapshot to a "marked-for-delete" state.
     * This will be deleted by the garbage collector on a subsequent iteration
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id the URN of a ViPR Snapshot
     * @brief Delete file snapshot
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep deleteSnapshot(@PathParam("id") URI id)
            throws InternalException {

        String task = UUID.randomUUID().toString();
        _log.info(String.format(
                "FileSnapshotDelete --- Snapshot id: %1$s, Task: %2$s", id,
                task));

        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);

        Operation op = null;
        if (snap != null) {
            ArgValidator.checkReference(Snapshot.class, id,
                    checkForDelete(snap));
            if (snap.getInactive()) {
                op = new Operation();
                op.setResourceType(ResourceOperationTypeEnum.DELETE_FILE_SNAPSHOT);
                op.ready();
                _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(), task, op);
            } else {
                FileShare fs = _permissionsHelper.getObjectById(snap.getParent(),
                        FileShare.class);
                if (null != fs) {
                    StorageSystem device = _dbClient.queryObject(
                            StorageSystem.class, fs.getStorageDevice());
                    FileController controller = getController(
                            FileController.class, device.getSystemType());
                    op = _dbClient.createTaskOpStatus(Snapshot.class, snap
                            .getId(), task, ResourceOperationTypeEnum.DELETE_FILE_SNAPSHOT);
                    controller.delete(device.getId(), null, snap.getId(),
                            false, FileControllerConstants.DeleteTypeEnum.FULL.toString(), task);
                    auditOp(OperationTypeEnum.DELETE_FILE_SNAPSHOT, true,
                            AuditLogManager.AUDITOP_BEGIN, snap.getId()
                                    .toString(),
                            device.getId().toString());
                }
            }

        }

        return toTask(snap, task, op);
    }

    /*
     * Generate export path
     * 
     * @param fsName
     * 
     * @param mountpath
     * 
     * @param deviceType
     * 
     * @return
     */
    private String getExportPath(String snapshotName, String mountPath, String deviceType) {
        String path = snapshotName;
        if (deviceType.equals(DiscoveredDataObject.Type.isilon.toString())) {
            path = mountPath;
        }
        return path;
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of file snapshot resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public FileSnapshotBulkRep getBulkResources(BulkIdParam param) {
        return (FileSnapshotBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Snapshot> getResourceClass() {
        return Snapshot.class;
    }

    /**
     * Retrieve FileSnapshot representations based on input ids.
     * 
     * @param ids the URN of a ViPR FileSnapshot list.
     * @return list of FileSnapshot representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @Override
    public FileSnapshotBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Snapshot> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new FileSnapshotBulkRep(BulkList.wrapping(_dbIterator, MapFileSnapshot.getInstance()));
    }

    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        Iterator<Snapshot> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        ResourceFilter<Snapshot> filter = new FileSnapshotFilter(getUserFromContext(), _permissionsHelper);
        return new FileSnapshotBulkRep(BulkList.wrapping(_dbIterator, MapFileSnapshot.getInstance(), filter));
    }

    private class FileSnapshotFilter extends PermissionsEnforcingResourceFilter<Snapshot> {

        protected FileSnapshotFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(Snapshot resource) {
            boolean ret = false;
            ret = isTenantAccessible(getTenantOwner(resource.getId()));
            if (!ret) {
                NamedURI proj = resource.getProject();
                if (proj != null) {
                    ret = isProjectAccessible(proj.getURI());
                }
            }
            return ret;
        }
    }

    /**
     * File snapshot is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.FILE_SNAPSHOT;
    }

    /**
     * Get search results by name in zone or project.
     * 
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getNamedSearchResults(String name, URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        if (projectId == null) {
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getLabelPrefixConstraint(getResourceClass(), name),
                    resRepList);
        } else {
            _dbClient.queryByConstraint(
                    ContainmentPrefixConstraint.Factory.getSnapshotUnderProjectConstraint(
                            projectId, name),
                    resRepList);
        }
        return resRepList;
    }

    /**
     * Get search results by project alone.
     * 
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getProjectSearchResults(URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectFileSnapshotConstraint(projectId),
                resRepList);
        return resRepList;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new ProjOwnedSnapResRepFilter(user, permissionsHelper, Snapshot.class);
    }

    /**
     * 
     * Existing file system exports may have their list of export rules updated.
     * 
     * @param id the URN of a ViPR fileSystem
     * @param subDir sub-directory within a filesystem
     * @brief Update file system export
     * @return Task resource representation
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/export")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateSnapshotExportRules(@PathParam("id") URI id,
            SnapshotExportUpdateParams param) throws InternalException {

        // log input received.
        _log.info("Update Snapshot Export Rules : request received for {}  with {}", id, param);
        String task = UUID.randomUUID().toString();
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, Snapshot.class, "id");
        Snapshot snap = queryResource(id);

        ArgValidator.checkEntity(snap, id, true);
        FileShare fs = _permissionsHelper.getObjectById(snap.getParent(), FileShare.class);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        String path = snap.getPath();
        _log.info("Snapshot Export path found {} ", path);

        Operation op = _dbClient.createTaskOpStatus(Snapshot.class, snap.getId(), task,
                ResourceOperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SNAPSHOT);

        try {

            // Validate the input
            ExportVerificationUtility exportVerificationUtility = new ExportVerificationUtility(_dbClient);
            exportVerificationUtility.verifyExports(fs, snap, param);

            _log.info("No Errors found proceeding further {}, {}, {}", new Object[] { _dbClient, fs, param });

            controller.updateExportRules(device.getId(), snap.getId(), param, task);
            // controller.export(device.getId(), snap.getId(), Arrays.asList(export), task);
            auditOp(OperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), param);

        } catch (URISyntaxException e) {
            op.setStatus(Operation.Status.error.name());
            _log.error("Error Processing Export Updates {}, {}", e.getMessage(), e);
            return toTask(snap, task, op);
        } catch (BadRequestException e) {
            op = _dbClient.error(Snapshot.class, snap.getId(), task, e);
            _log.error("Error Processing Export Updates {}, {}", e.getMessage(), e);
            // throw e;
        } catch (Exception e) {
            op.setStatus(Operation.Status.error.name());
            toTask(snap, task, op);
            // _log.error("Error Processing Export Updates {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }

        return toTask(snap, task, op);
    }

}
