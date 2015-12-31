/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.FileMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.*;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/*
 * Internal api for object provisioning
 */
@Path("/internal/file/filesystems")
public class InternalFileResource extends ResourceService {

    private final static Logger _log = LoggerFactory.getLogger(InternalFileResource.class);
    private static final String EVENT_SERVICE_TYPE = "file";
    private static final Project _internalProject = createInternalProject();
    private static final DataObject.Flag[] INTERNAL_FILESHARE_FLAGS = new DataObject.Flag[] {
            Flag.INTERNAL_OBJECT, Flag.NO_PUBLIC_ACCESS, Flag.NO_METERING };

    @Autowired
    private FileService _fileService;

    /*
     * check if the fileshare is accessible via internal api
     */
    private void checkFileShareInternal(FileShare fs) {
        if (!fs.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
            throw APIException.forbidden.internalAPICannotBeUsed();
        }
    }

    /*
     * POST to create
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TaskResourceRep createFileSystemInternal(FileSystemParam param) {
        TenantOrg tenant = _permissionsHelper.getRootTenant();
        TaskResourceRep rep = null;
        if (!_permissionsHelper.userHasGivenRole(getUserFromContext(), tenant.getId(),
                Role.SYSTEM_ADMIN, Role.TENANT_ADMIN)) {
            rep = new TaskResourceRep();
            _log.error("Unable to process the request as Only [system_admin, tenant_admin] can provision file systems for object");
            rep.setMessage("Only [system_admin, tenant_admin] can provision file systems for object");
            rep.setState(Operation.Status.error.name());
            return rep;
            
        }
        try {
        	rep = _fileService.createFSInternal(param, _internalProject, tenant, INTERNAL_FILESHARE_FLAGS);
        } catch (Exception ex) {
            rep = new TaskResourceRep();
            _log.error("Exception occurred while creating file system due to:", ex);
            rep.setMessage(ex.getMessage());
            rep.setState(Operation.Status.error.name());
        }

        return rep;
    }

    /*
     * GET filesystem by id
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public FileShareRestRep getFileSystemInternal(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);
        return map(fs);
    }

    /*
     * GET task status
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/tasks/{op_id}/")
    public TaskResourceRep getTaskInternal(@PathParam("id") URI id,
            @PathParam("op_id") URI op_id) throws DatabaseException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);
        return toTask(fs, op_id.toString());
    }

    /*
     * GET list of file system exports
     * 
     * @param id the URN of a ViPR File system
     * 
     * @return File system exports list.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    public FileSystemExportList getFileSystemExportListInternal(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);
        return _fileService.getFileSystemExportList(id);
    }

    /*
     * POST to create a new export
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    public TaskResourceRep exportInternal(@PathParam("id") URI id, FileSystemExportParam param)
            throws InternalException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);
        return _fileService.export(id, param);
    }

    /**
     * Modifies existing export
     * 
     * @param id the URN of a ViPR file share
     * @param protocol protocol to be used for export
     * @param securityType security type for export
     * @param permissions export permissions
     * @param rootUserMapping user mapping for export
     * @param updateParam parameter indicating the information to be updated for this export, which contains the list of
     *            endpoints
     * @return returns a task corresponding to this operation
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports/{protocol},{secType},{perm},{root_mapping}")
    public TaskResourceRep modifyExportInternal(@PathParam("id") URI id,
            @PathParam("protocol") String protocol, @PathParam("secType") String securityType,
            @PathParam("perm") String permissions,
            @PathParam("root_mapping") String rootUserMapping,
            FileExportUpdateParam updateParam) throws InternalException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);
        return _fileService.updateExport(id, protocol, securityType, permissions, rootUserMapping, updateParam);
    }

    /*
     * DELETE filesystem export
     */
    @DELETE
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports/{protocol},{secType},{perm},{root_mapping}")
    public TaskResourceRep unexportInternal(@PathParam("id") URI id,
            @PathParam("protocol") String protocol, @PathParam("secType") String securityType,
            @PathParam("perm") String permissions,
            @PathParam("root_mapping") String rootUserMapping,
            @QueryParam("subDirectory") String subDirectory) throws InternalException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);
        return _fileService.unexport(id, protocol, securityType, permissions, rootUserMapping, subDirectory);
    }

    /*
     * POST to deactivate filesystem
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    public TaskResourceRep deactivateFileSystemInternal(@PathParam("id") URI id, FileSystemDeleteParam param)
            throws InternalException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);
        TenantOrg tenant = _permissionsHelper.getRootTenant();
        if (!_permissionsHelper.userHasGivenRole(getUserFromContext(), tenant.getId(),
                Role.SYSTEM_ADMIN, Role.TENANT_ADMIN)) {
            throw APIException.forbidden.onlyAdminsCanDeactivateFileSystems(
                    Role.SYSTEM_ADMIN.toString(), Role.TENANT_ADMIN.toString());
        }
        return _fileService.deactivateFileSystem(id, param);
    }

    /**
     * Release a file system from its current tenant & project for internal object usage
     * 
     * @param id the URN of a ViPR file system to be released
     * @return the updated file system
     * @throws InternalException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/release")
    public FileShareRestRep releaseFileSystemInternal(@PathParam("id") URI id)
            throws InternalException {

        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);

        // if the FS is already marked as internal, we can skip all this logic
        // and just return success down at the bottom
        if (!fs.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
            URI tenantURI = fs.getTenant().getURI();
            if (!_permissionsHelper.userHasGivenRole(getUserFromContext(), tenantURI,
                    Role.TENANT_ADMIN)) {
                throw APIException.forbidden.onlyAdminsCanReleaseFileSystems(
                        Role.TENANT_ADMIN.toString());
            }

            // we can't release a fs that has exports
            FSExportMap exports = fs.getFsExports();
            if ((exports != null) && (!exports.isEmpty())) {
                throw APIException.badRequests.cannotReleaseFileSystemExportExists(exports.keySet().toString());
            }

            // we can't release a fs that has shares
            SMBShareMap shares = fs.getSMBFileShares();
            if ((shares != null) && (!shares.isEmpty())) {
                throw APIException.badRequests.cannotReleaseFileSystemSharesExists(shares.keySet().toString());
            }

            // files systems with pending operations can't be released
            if (fs.getOpStatus() != null) {
                for (String opId : fs.getOpStatus().keySet()) {
                    Operation op = fs.getOpStatus().get(opId);
                    if (Operation.Status.pending.name().equals(op.getStatus())) {
                        throw APIException.badRequests.cannotReleaseFileSystemWithTasksPending();
                    }
                }
            }

            // file systems with snapshots can't be released
            Integer snapCount = _fileService.getNumSnapshots(fs);
            if (snapCount > 0) {
                throw APIException.badRequests.cannotReleaseFileSystemSnapshotExists(snapCount);
            }

            TenantOrg rootTenant = _permissionsHelper.getRootTenant();

            // we can't release the file system to the root tenant if the root tenant has no access
            // to the filesystem's virtual pool
            ArgValidator.checkFieldNotNull(fs.getVirtualPool(), "virtualPool");
            VirtualPool virtualPool = _permissionsHelper.getObjectById(fs.getVirtualPool(), VirtualPool.class);
            ArgValidator.checkEntity(virtualPool, fs.getVirtualPool(), false);
            if (!_permissionsHelper.tenantHasUsageACL(rootTenant.getId(), virtualPool)) {
                throw APIException.badRequests.cannotReleaseFileSystemRootTenantLacksVPoolACL(virtualPool.getId().toString());
            }

            fs.setOriginalProject(fs.getProject().getURI());
            fs.setTenant(new NamedURI(rootTenant.getId(), fs.getLabel()));
            fs.setProject(new NamedURI(_internalProject.getId(), fs.getLabel()));
            fs.addInternalFlags(INTERNAL_FILESHARE_FLAGS);
            _dbClient.updateAndReindexObject(fs);

            // audit against the source project, not the new dummy internal project
            auditOp(OperationTypeEnum.RELEASE_FILE_SYSTEM, true, null, fs.getId().toString(), fs.getOriginalProject().toString());
        }

        return map(fs);
    }

    /**
     * Undo the release of a file system
     * 
     * @param id the URN of a ViPR file system to undo
     * @return the updated file system
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/release/undo")
    public FileShareRestRep undoReleaseFileSystemInternal(@PathParam("id") URI id)
            throws InternalException {

        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = _fileService.queryResource(id);
        checkFileShareInternal(fs);

        URI releasedProject = fs.getOriginalProject();
        if (releasedProject == null) {
            throw APIException.forbidden.onlyPreviouslyReleasedFileSystemsCanBeUndone();
        }

        Project project = _permissionsHelper.getObjectById(releasedProject, Project.class);
        ArgValidator.checkEntity(project, releasedProject, false);
        ArgValidator.checkFieldNotNull(project.getTenantOrg(), "tenantOrg");
        ArgValidator.checkFieldNotNull(project.getTenantOrg().getURI(), "tenantOrg");

        fs.setTenant(new NamedURI(project.getTenantOrg().getURI(), fs.getLabel()));
        fs.setProject(new NamedURI(releasedProject, fs.getLabel()));
        fs.setOriginalProject(null);
        fs.clearInternalFlags(INTERNAL_FILESHARE_FLAGS);
        _dbClient.updateAndReindexObject(fs);

        // audit against the new project, not the old dummy internal project
        auditOp(OperationTypeEnum.UNDO_RELEASE_FILE_SYSTEM, true, null, fs.getId().toString(), project.getId().toString());

        return map(fs);
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Create a non-persisted project object to be used with internal object resources
     * 
     * @return the dummy project
     */
    private static Project createInternalProject() {
        Project project = new Project();
        project.setId(FileShare.INTERNAL_OBJECT_PROJECT_URN);
        return project;
    }
}
