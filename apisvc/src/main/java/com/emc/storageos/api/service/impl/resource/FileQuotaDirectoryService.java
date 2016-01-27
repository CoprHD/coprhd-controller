/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.mapper.functions.MapQuotaDirectory;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.model.*;
import com.emc.storageos.model.file.*;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileShareQuotaDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.emc.storageos.api.mapper.FileMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

@Path("/file/quotadirectories")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
public class FileQuotaDirectoryService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(FileQuotaDirectoryService.class);
    public static final String UNLIMITED_USERS = "unlimited";
    private static final String EVENT_SERVICE_TYPE = "file";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<QuotaDirectory> getResourceClass() {
        return QuotaDirectory.class;
    }

    @Override
    public QuotaDirectoryBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<QuotaDirectory> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new QuotaDirectoryBulkRep(BulkList.wrapping(_dbIterator, MapQuotaDirectory.getInstance()));
    }

    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(
            List<URI> ids) {
        Iterator<QuotaDirectory> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter<QuotaDirectory> filter = new BulkList.ProjectResourceFilter<QuotaDirectory>(
                getUserFromContext(), _permissionsHelper);
        return new QuotaDirectoryBulkRep(BulkList.wrapping(_dbIterator, MapQuotaDirectory.getInstance(), filter));
    }

    private FileStorageScheduler _fileScheduler;
    private NameGenerator _nameGenerator;

    public NameGenerator getNameGenerator() {
        return _nameGenerator;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    public void setFileScheduler(FileStorageScheduler fileScheduler) {
        _fileScheduler = fileScheduler;
    }

    protected FileShare queryFileShareResource(URI id) {
        ArgValidator.checkUri(id);
        FileShare fs = _permissionsHelper.getObjectById(id, FileShare.class);
        ArgValidator.checkEntityNotNull(fs, id, isIdEmbeddedInURL(id));
        return fs;
    }

    @Override
    protected QuotaDirectory queryResource(URI id) {
        ArgValidator.checkUri(id);
        QuotaDirectory qd = _permissionsHelper.getObjectById(id, QuotaDirectory.class);
        ArgValidator.checkEntityNotNull(qd, id, isIdEmbeddedInURL(id));
        return qd;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        QuotaDirectory qd = queryResource(id);
        FileShare fs = queryFileShareResource(qd.getParent().getURI());
        return fs.getTenant().getURI();
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of file share resources
     * @return list of representations.
     * 
     * @throws com.emc.storageos.db.exceptions.DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public QuotaDirectoryBulkRep getBulkResources(BulkIdParam param) {
        return (QuotaDirectoryBulkRep) super.getBulkResources(param);
    }

    /**
     * Filesystem is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.QUOTA_DIR;
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
                    ContainmentPrefixConstraint.Factory.getFileshareUnderProjectConstraint(
                            projectId, name), resRepList);
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
                ContainmentConstraint.Factory.getProjectFileshareConstraint(projectId),
                resRepList);
        return resRepList;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new ProjOwnedResRepFilter(user, permissionsHelper, FileShare.class);
    }

    /**
     * Update Quota Directory for a file share
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id the URN of a ViPR Quota directory
     * @param param File system Quota directory update parameters
     * @brief Update file system Quota directory
     * @return Task resource representation
     * @throws com.emc.storageos.svcs.errorhandling.resources.InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateQuotaDirectory(@PathParam("id") URI id, QuotaDirectoryUpdateParam param)
            throws InternalException {
        _log.info("FileService::Update Quota directory Request recieved {}", id);

        QuotaDirectory quotaDir = queryResource(id);

        String task = UUID.randomUUID().toString();

        if (param.getSecurityStyle() != null) {
            ArgValidator.checkFieldValueFromEnum(param.getSecurityStyle(), "security_style",
                    EnumSet.allOf(QuotaDirectory.SecurityStyles.class));
        }

        // Get the FileSystem object
        FileShare fs = queryFileShareResource(quotaDir.getParent().getURI());
        ArgValidator.checkFieldNotNull(fs, "filesystem");

        // Update the quota directory object to store in ViPR database
        quotaDir.setOpStatus(new OpStatusMap());

        // Set all other optional parameters too.
        if (param.getOpLock() != null) {
            quotaDir.setOpLock(param.getOpLock());
        }

        if (param.getSecurityStyle() != null) {
            quotaDir.setSecurityStyle(param.getSecurityStyle());
        }

        if (param.getSize() != null) {
            Long quotaSize = SizeUtil.translateSize(param.getSize());
            if (quotaSize > 0) {
                ArgValidator.checkFieldMaximum(quotaSize, fs.getCapacity(), " Bytes", "size");
                quotaDir.setSize(quotaSize);
            }
        }
        

        ArgValidator.checkFieldMaximum(param.getSoftLimit(), 100, "softLimit");
        ArgValidator.checkFieldMaximum(param.getNotificationLimit(), 100, "notificationLimit");
        
        if (param.getSoftLimit() != 0L) {
            ArgValidator.checkFieldMinimum(param.getSoftGrace(), 1L, "softGrace");
        }
        quotaDir.setSoftLimit(param.getSoftLimit() != 0 ? param.getSoftLimit() :
            fs.getSoftLimit() != null ? fs.getSoftLimit().intValue() : 0);
        quotaDir.setSoftGrace(param.getSoftGrace() != 0 ? param.getSoftGrace() :
            fs.getSoftGracePeriod() != null ? fs.getSoftGracePeriod() : 0);
        quotaDir.setNotificationLimit(param.getNotificationLimit() != 0 ? param.getNotificationLimit()
                : fs.getNotificationLimit() != null ? fs.getNotificationLimit().intValue() : 0);        
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.UPDATE_FILE_SYSTEM_QUOTA_DIR);
        quotaDir.getOpStatus().createTaskStatus(task, op);
        fs.setOpStatus(new OpStatusMap());
        fs.getOpStatus().createTaskStatus(task, op);
        _dbClient.persistObject(fs);
        _dbClient.persistObject(quotaDir);

        // Create an object of type "FileShareQtree" to be passed into the south-bound layers.
        FileShareQuotaDirectory qt = new FileShareQuotaDirectory(quotaDir);

        // Now get ready to make calls into the controller
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());
        try {
            controller.updateQuotaDirectory(device.getId(), qt, fs.getId(), task);
        } catch (InternalException e) {
            _log.error("Error during update of Quota Directory {}", e);

            // treating all controller exceptions as internal error for now. controller
            // should discriminate between validation problems vs. internal errors
            throw e;
        }

        auditOp(OperationTypeEnum.UPDATE_FILE_SYSTEM_QUOTA_DIR, true, AuditLogManager.AUDITOP_BEGIN,
                quotaDir.getLabel(), quotaDir.getId().toString(), fs.getId().toString());

        fs = _dbClient.queryObject(FileShare.class, fs.getId());
        _log.debug("FileService::Quota directory Before sending response, FS ID : {}, Taks : {} ; Status {}", fs.getOpStatus().get(task),
                fs.getOpStatus().get(task).getStatus());

        return toTask(quotaDir, task, op);
    }

    /**
     * Deactivate Quota directory of file system, this will move the
     * Quota directory to a "marked-for-delete" state
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id the URN of the QuotaDirectory
     * @param param QuotaDirectory delete param for optional force delete
     * @brief Delete file system Quota Dir
     * @return Task resource representation
     * @throws com.emc.storageos.svcs.errorhandling.resources.InternalException
     */

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep deactivateQuotaDirectory(@PathParam("id") URI id, QuotaDirectoryDeleteParam param)
            throws InternalException {

        _log.info("FileService::deactivateQtree Request recieved {}", id);
        String task = UUID.randomUUID().toString();
        ArgValidator.checkFieldUriType(id, QuotaDirectory.class, "id");
        QuotaDirectory quotaDirectory = queryResource(id);
        FileShare fs = queryFileShareResource(quotaDirectory.getParent().getURI());
        ArgValidator.checkFieldNotNull(fs, "filesystem");

        // <TODO> Implement Force delete option when shares and exports for Quota Directory are supported

        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.DELETE_FILE_SYSTEM_QUOTA_DIR);
        quotaDirectory.getOpStatus().createTaskStatus(task, op);
        fs.setOpStatus(new OpStatusMap());
        fs.getOpStatus().createTaskStatus(task, op);
        _dbClient.persistObject(fs);
        _dbClient.persistObject(quotaDirectory);

        // Now get ready to make calls into the controller
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());
        try {
            controller.deleteQuotaDirectory(device.getId(), quotaDirectory.getId(), fs.getId(), task);
            // If delete operation is successful, then remove obj from ViPR db by setting inactive=true
            quotaDirectory.setInactive(true);
            _dbClient.persistObject(quotaDirectory);

        } catch (InternalException e) {
            // treating all controller exceptions as internal error for now. controller
            // should discriminate between validation problems vs. internal errors

            throw e;
        }

        auditOp(OperationTypeEnum.DELETE_FILE_SYSTEM_QUOTA_DIR, true, AuditLogManager.AUDITOP_BEGIN,
                quotaDirectory.getLabel(), quotaDirectory.getId().toString(), fs.getId().toString());

        fs = _dbClient.queryObject(FileShare.class, fs.getId());
        _log.debug("FileService::Quota directory Before sending response, FS ID : {}, Taks : {} ; Status {}", fs.getOpStatus().get(task),
                fs.getOpStatus().get(task).getStatus());

        return toTask(quotaDirectory, task, op);
    }

    /**
     * Get info for file system quota directory
     * 
     * @param id the URN of a ViPR Quota directory
     * @brief Show file system quota directory
     * @return File system quota directory details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public QuotaDirectoryRestRep getQuotaDirectory(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, QuotaDirectory.class, "id");
        QuotaDirectory quotaDir = queryResource(id);
        return map(quotaDir);
    }

    private List<QuotaDirectory> queryDBQuotaDirectories(FileShare fs) {
        _log.info("Querying all quota directories Using FsId {}", fs.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getQuotaDirectoryConstraint(fs.getId());
            List<QuotaDirectory> fsQuotaDirs = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, QuotaDirectory.class,
                    containmentConstraint);
            return fsQuotaDirs;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return null;
    }

}
