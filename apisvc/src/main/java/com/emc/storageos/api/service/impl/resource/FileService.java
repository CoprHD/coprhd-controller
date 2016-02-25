/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

import com.emc.storageos.api.mapper.functions.MapFileShare;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.FilePlacementManager;
import com.emc.storageos.api.service.impl.placement.FileRecommendation;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.CifsShareUtility;
import com.emc.storageos.api.service.impl.resource.utils.ExportVerificationUtility;
import com.emc.storageos.api.service.impl.resource.utils.NfsACLUtility;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.MirrorStatus;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.QuotaDirectory.SecurityStyles;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.filereplicationcontroller.FileReplicationController;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.SnapshotList;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.MirrorList;
import com.emc.storageos.model.file.Copy;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileCifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParam;
import com.emc.storageos.model.file.FileNfsACLUpdateParams;
import com.emc.storageos.model.file.FilePolicyList;
import com.emc.storageos.model.file.FilePolicyRestRep;
import com.emc.storageos.model.file.FileReplicationCreateParam;
import com.emc.storageos.model.file.FileReplicationParam;
import com.emc.storageos.model.file.FileShareBulkRep;
import com.emc.storageos.model.file.FileShareExportUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.storageos.model.file.FileSystemExpandParam;
import com.emc.storageos.model.file.FileSystemExportList;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.model.file.FileSystemShareList;
import com.emc.storageos.model.file.FileSystemShareParam;
import com.emc.storageos.model.file.FileSystemSnapshotParam;
import com.emc.storageos.model.file.FileSystemUpdateParam;
import com.emc.storageos.model.file.FileSystemVirtualPoolChangeParam;
import com.emc.storageos.model.file.NfsACLs;
import com.emc.storageos.model.file.QuotaDirectoryCreateParam;
import com.emc.storageos.model.file.QuotaDirectoryList;
import com.emc.storageos.model.file.ScheduleSnapshotList;
import com.emc.storageos.model.file.ScheduleSnapshotRestRep;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.SmbShareResponse;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
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
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.FileShareExport.Permissions;
import com.emc.storageos.volumecontroller.FileShareExport.SecurityTypes;
import com.emc.storageos.volumecontroller.FileShareQuotaDirectory;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@Path("/file/filesystems")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class FileService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(FileService.class);
    public static final String UNLIMITED_USERS = "unlimited";
    private static final String EVENT_SERVICE_TYPE = "file";
    protected static final String PROTOCOL_NFS = "NFS";
    protected static final String PROTOCOL_CIFS = "CIFS";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<FileShare> getResourceClass() {
        return FileShare.class;
    }

    @Override
    public FileShareBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<FileShare> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new FileShareBulkRep(BulkList.wrapping(_dbIterator, MapFileShare.getInstance()));
    }

    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        Iterator<FileShare> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter<FileShare> filter = new BulkList.ProjectResourceFilter<FileShare>(
                getUserFromContext(), _permissionsHelper);
        return new FileShareBulkRep(BulkList.wrapping(_dbIterator, MapFileShare.getInstance(), filter));
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

    FilePlacementManager _filePlacementManager;

    // File service implementations
    static volatile private Map<String, FileServiceApi> _fileServiceApis;

    public static FileServiceApi getFileServiceApis(String Type) {
        return _fileServiceApis.get(Type);
    }

    public static void setFileServiceApis(Map<String, FileServiceApi> _fileServiceApis) {
        FileService._fileServiceApis = _fileServiceApis;
    }

    public void setFilePlacementManager(FilePlacementManager placementManager) {
        _filePlacementManager = placementManager;
    }

    public enum FileTechnologyType {
        LOCAL_MIRROR, REMOTE_MIRROR,
    };

    // Protection operations that are allowed with /file/filesystems/{id}/protection/continuous-copies/
    public static enum ProtectionOp {
        FAILOVER("failover", ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER),
        FAILBACK("failback", ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILBACK),
        START("start", ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_START),
        STOP("stop", ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_STOP),
        PAUSE("pause", ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_PAUSE),
        RESUME("resume", ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_RESUME),
        REFRESH("refresh", ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_REFRESH),
        UNKNOWN("unknown", ResourceOperationTypeEnum.PERFORM_PROTECTION_ACTION);

        private final String op;
        private final ResourceOperationTypeEnum resourceType;

        ProtectionOp(String op, ResourceOperationTypeEnum resourceType) {
            this.op = op;
            this.resourceType = resourceType;
        }

        // The rest URI operation
        public String getRestOp() {
            return op;
        }

        // The resource type, which contains a good name and description
        public ResourceOperationTypeEnum getResourceType() {
            return resourceType;
        }

        private static final ProtectionOp[] copyOfValues = values();

        public static String getProtectionOpDisplayName(String op) {
            for (ProtectionOp opValue : copyOfValues) {
                if (opValue.getRestOp().contains(op)) {
                    return opValue.getResourceType().getName();
                }
            }
            return ProtectionOp.UNKNOWN.name();
        }

        public static ResourceOperationTypeEnum getResourceOperationTypeEnum(String restOp) {
            for (ProtectionOp opValue : copyOfValues) {
                if (opValue.getRestOp().contains(restOp)) {
                    return opValue.getResourceType();
                }
            }
            return ResourceOperationTypeEnum.FILE_PROTECTION_ACTION;
        }
    }

    /**
     * Creates file system.
     * 
     * The VNX File array does not allow 'root' as the beginning of a file system name. If the generated file system
     * name begins with 'root', then the VNX File array will return an error.
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param param
     *            File system parameters
     * @param id
     *            the URN of a ViPR Project
     * @brief Create file system
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep createFileSystem(FileSystemParam param, @QueryParam("project") URI id) throws InternalException {
        // check project
        ArgValidator.checkFieldUriType(id, Project.class, "project");

        // Make label as mandatory field
        ArgValidator.checkFieldNotNull(param.getLabel(), "label");

        Project project = _permissionsHelper.getObjectById(id, Project.class);
        ArgValidator.checkEntity(project, id, isIdEmbeddedInURL(id));
        ArgValidator.checkFieldNotNull(project.getTenantOrg(), "project");
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());

        return createFSInternal(param, project, tenant, null);

    }

    /*
     * all the common code for provisioning fs both in normal public API use case and
     * the internal object case
     * NOTE - below method should always work with project being null
     */
    public TaskResourceRep createFSInternal(FileSystemParam param, Project project,
            TenantOrg tenant, DataObject.Flag[] flags) throws InternalException {
        ArgValidator.checkFieldUriType(param.getVpool(), VirtualPool.class, "vpool");
        ArgValidator.checkFieldUriType(param.getVarray(), VirtualArray.class, "varray");

        Long fsSize = SizeUtil.translateSize(param.getSize());
        // Convert to MB and check for 20MB min size.
        Long fsSizeMB = fsSize / (1024 * 1024);

        // Convert to MB and check for 20MB min size.
        // VNX file has min 2MB size, NetApp 20MB and Isilon 0
        // VNX File 8.1.6 min 1GB size
        ArgValidator.checkFieldMinimum(fsSizeMB, 1024, "MB", "size");

        ArrayList<String> requestedTypes = new ArrayList<String>();

        // check varray
        VirtualArray neighborhood = _dbClient.queryObject(VirtualArray.class, param.getVarray());
        ArgValidator.checkEntity(neighborhood, param.getVarray(), false);
        _permissionsHelper.checkTenantHasAccessToVirtualArray(tenant.getId(), neighborhood);

        String task = UUID.randomUUID().toString();

        // check vpool reference
        VirtualPool cos = _dbClient.queryObject(VirtualPool.class, param.getVpool());
        _permissionsHelper.checkTenantHasAccessToVirtualPool(tenant.getId(), cos);
        ArgValidator.checkEntity(cos, param.getVpool(), false);
        if (!VirtualPool.Type.file.name().equals(cos.getType())) {
            throw APIException.badRequests.virtualPoolNotForFileBlockStorage(VirtualPool.Type.file.name());
        }

        // prepare vpool capability values
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, fsSize);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(cos.getSupportedProvisioningType())) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        }

        setProtectionCapWrapper(cos, capabilities);

        ArgValidator.checkFieldMaximum(param.getSoftLimit(), 100, "softLimit");
        ArgValidator.checkFieldMaximum(param.getNotificationLimit(), 100, "notificationLimit");

        if (param.getSoftLimit() != 0L) {
            ArgValidator.checkFieldMinimum(param.getSoftGrace(), 1L, "softGrace");
        }

        if (param.getNotificationLimit() != 0) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.SUPPORT_NOTIFICATION_LIMIT, Boolean.TRUE);
        }

        if (param.getSoftLimit() != 0) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.SUPPORT_SOFT_LIMIT, Boolean.TRUE);
        }

        // verify quota
        CapacityUtils.validateQuotasForProvisioning(_dbClient, cos, project, tenant, fsSize, "filesystem");
        String suggestedNativeFsId = param.getFsId() == null ? "" : param.getFsId();

        // Find the implementation that services this vpool and fileshare
        FileServiceApi fileServiceApi = getFileServiceImpl(cos, _dbClient);
        TaskList taskList = createFileTaskList(param, project, tenant, neighborhood, cos, flags, task);

        // call thread that does the work.
        CreateFileSystemSchedulingThread.executeApiTask(this, _asyncTaskService.getExecutorService(), _dbClient,
                neighborhood, project, cos, tenant, flags,
                capabilities, taskList, task, requestedTypes, param,
                fileServiceApi, suggestedNativeFsId);

        auditOp(OperationTypeEnum.CREATE_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                param.getLabel(), param.getSize(), neighborhood.getId().toString(),
                project == null ? null : project.getId().toString());
        // Till we Support multiple file system create
        // return the file share taskrep
        return taskList.getTaskList().get(0);
    }

    /**
     * Allocate, initialize and persist state of the fileSystem being created.
     * 
     * @param param
     * @param project
     * @param neighborhood
     * @param vpool
     * @param placement
     * @param token
     * @return
     */
    private FileShare prepareEmptyFileSystem(FileSystemParam param, Project project, TenantOrg tenantOrg,
            VirtualArray varray, VirtualPool vpool, DataObject.Flag[] flags, String task) {
        _log.debug("prepareEmptyFileSystem start...");
        StoragePool pool = null;
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));

        fs.setLabel(param.getLabel());

        // No need to generate any name -- Since the requirement is to use the customizing label we should use the same.
        // Stripping out the special characters like ; /-+!@#$%^&())";:[]{}\ | but allow underscore character _
        String convertedName = param.getLabel().replaceAll("[^\\dA-Za-z\\_]", "");
        _log.info("Original name {} and converted name {}", param.getLabel(), convertedName);
        fs.setName(convertedName);
        Long fsSize = SizeUtil.translateSize(param.getSize());
        fs.setCapacity(fsSize);
        fs.setNotificationLimit(Long.valueOf(param.getNotificationLimit()));
        fs.setSoftLimit(Long.valueOf(param.getSoftLimit()));
        fs.setSoftGracePeriod(param.getSoftGrace());
        fs.setVirtualPool(param.getVpool());
        if (project != null) {
            fs.setProject(new NamedURI(project.getId(), fs.getLabel()));
        }
        fs.setTenant(new NamedURI(tenantOrg.getId(), param.getLabel()));
        fs.setVirtualArray(varray.getId());

        // When a VPool supports "thin" provisioning
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(vpool.getSupportedProvisioningType())) {
            fs.setThinlyProvisioned(Boolean.TRUE);
        }

        fs.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_FILE_SYSTEM);
        fs.getOpStatus().createTaskStatus(task, op);
        if (flags != null) {
            fs.addInternalFlags(flags);
        }
        _dbClient.createObject(fs);
        return fs;
    }

    private void setProtectionCapWrapper(final VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {
        if (vPool.getFileReplicationType() != null) { // file replication tyep either LOCAL OR REMOTE
            if (vPool.getRpRpoType() != null) { // rpo type can be DAYS or HOURS
                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_RP_RPO_TYPE, vPool.getRpRpoType());
            }

            if (vPool.getFrRpoValue() != null) {
                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_RP_RPO_VALUE, vPool.getFrRpoValue());
            }
            // async or copy
            // async - soure changes will mirror target
            // copy - it kind backup, it is full copy
            if (vPool.getFileReplicationCopyMode() != null) {
                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_RP_COPY_MODE, vPool.getFrRpoValue());
            }

        }
    }

    /**
     * A method that pre-creates task and FileShare object to return to the caller of the API.
     * 
     * @param param
     * @param project
     *            - project of the FileShare
     * @param tenantOrg
     *            - tenant of the FileShare
     * @param varray
     *            - varray of the FileShare
     * @param vpool
     *            - vpool of the Fileshare
     * @param flags
     *            -
     * @param task
     * @return
     */
    private TaskList createFileTaskList(FileSystemParam param, Project project, TenantOrg tenantOrg,
            VirtualArray varray, VirtualPool vpool, DataObject.Flag[] flags, String task) {
        TaskList taskList = new TaskList();
        FileShare fs = prepareEmptyFileSystem(param, project, tenantOrg, varray, vpool, flags, task);
        TaskResourceRep fileTask = toTask(fs, task);
        taskList.getTaskList().add(fileTask);
        _log.info(String.format("FileShare and Task Pre-creation Objects [Init]--  Source FileSystem: %s, Task: %s, Op: %s",
                fs.getId(), fileTask.getId(), task));
        return taskList;
    }

    /**
     * Get info for file system
     * 
     * @param id
     *            the URN of a ViPR File system
     * @brief Show file system
     * @return File system details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public FileShareRestRep getFileSystem(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        return map(fs);
    }

    /**
     * 
     * 'mountPath' is not case sensitive. The complete mountPath should be specified.
     * 
     * If a matching filesystem is not found, an empty list is returned.
     * 
     * Parameters - mountPath String - mountPath of the filesystem
     */
    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {
        SearchResults result = new SearchResults();

        // Here we search by mountPath
        if (!parameters.containsKey("mountPath")) {
            throw APIException.badRequests.invalidParameterSearchMissingParameter(getResourceClass().getName(), "mountPath");
        }

        String mountPath = parameters.get("mountPath").get(0);
        List<SearchResultResourceRep> resRepList = new ArrayList<SearchResultResourceRep>();

        URIQueryResultList fsUriList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getFileSystemMountPathConstraint(
                        mountPath),
                fsUriList);

        _log.info("After query of the database for {} and result {}", mountPath, fsUriList);

        Iterator<URI> fsListIterator = fsUriList.iterator();

        while (fsListIterator.hasNext()) {
            URI uri = fsListIterator.next();
            FileShare fs = _dbClient.queryObject(FileShare.class, uri);
            if (!fs.getInactive()) {
                if (authorized || isAuthorized(fs.getProject().getURI())) {
                    RestLinkRep selfLink = new RestLinkRep("self",
                            RestLinkFactory.newLink(getResourceType(), uri));
                    SearchResultResourceRep r = new SearchResultResourceRep(uri, selfLink, fs.getMountPath());
                    resRepList.add(r);
                    _log.info("Mount path match " + fs.getMountPath());
                } else {
                    _log.info("Mount path match but not authorized " + fs.getMountPath());
                }
            }
        }
        result.setResource(resRepList);
        return result;

    }

    @Override
    protected FileShare queryResource(URI id) {
        ArgValidator.checkUri(id);
        FileShare fs = _permissionsHelper.getObjectById(id, FileShare.class);
        ArgValidator.checkEntityNotNull(fs, id, isIdEmbeddedInURL(id));
        return fs;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        FileShare fs = queryResource(id);
        return fs.getTenant().getURI();
    }

    /**
     * @Deprecated use @Path("/{id}/export") instead.
     *             Get list of file system exports
     * @param id
     *            the URN of a ViPR File system
     * @brief List file system exports.
     *        <p>
     *        Use /file/filesystems/{id}/export instead
     * @return File system exports list.
     */
    @Deprecated
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public FileSystemExportList getFileSystemExportList(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fileShare = queryResource(id);
        FileSystemExportList fileExportListResponse = new FileSystemExportList();

        if (fileShare.getInactive()) {
            return fileExportListResponse;
        }
        // Get export map from fileSystem
        FSExportMap exportMap = fileShare.getFsExports();

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
            fileExportParam.setSubDirectory(fileExport.getSubDirectory());

            fileExportListResponse.getExportList().add(fileExportParam);
        }
        return fileExportListResponse;
    }

    /**
     * Since, Modifying an export is not allowed
     * This method verifies the existing export params with the new one issued to modify.
     * 
     * @param fs
     * @param param
     */

    private void verifyExports(FileShare fs, FileExportUpdateParam param, String permissions, String securityType, String rootUserMapping,
            String path) {

        // Check to see if th permission passed in is valid
        Boolean allowedPermission = false;
        for (Permissions me : Permissions.values()) {
            if (me.name().equalsIgnoreCase(permissions)) {
                allowedPermission = true;
                break;
            }
        }

        if (!allowedPermission) {
            throw APIException.badRequests.invalidPermissionType(permissions);
        }

        // Check to see if the Security Type passed in is valid
        Boolean allowedsecurityType = false;
        for (SecurityTypes secType : SecurityTypes.values()) {
            if (secType.name().equalsIgnoreCase(securityType)) {
                allowedsecurityType = true;
                break;
            }
        }

        if (!allowedsecurityType) {
            throw APIException.badRequests.invalidSecurityType(securityType);
        }

        FSExportMap fsExports = fs.getFsExports();
        URI id = fs.getId();

        // The below logic should satisfy the following test cases
        // 1. if a fresh new export request received it should go through.
        // 2. if an export modify found meaning that, the path matched and the request received for same end point:
        // 2.1 if there is any change in perm, sec, user further processing of request is not allowed.
        // 2.2 if there is no change in perm, sec, user, end point request should process to override the export.
        // 3. if an export modify found and path not matched, let the request proceed as it is.
        // 4. if no export modify found, let the request proceed.

        if (null != fsExports) {

            Iterator<FileExport> it = fs.getFsExports().values().iterator();

            while (it.hasNext()) {
                FileExport fileExport = it.next();
                // If no key found then it should process as it is.
                boolean isAlreadyExportedToSameEndpoint = false;
                if (fileExport.getPath().equals(path)) {
                    List<String> availableEndpoints = fileExport.getClients();
                    List<String> providedEndpoints = param.getAdd();
                    for (String providedEndpoint : providedEndpoints) {
                        if (availableEndpoints.contains(providedEndpoint)) {
                            isAlreadyExportedToSameEndpoint = true;
                            break;
                        }
                    }
                    if (isAlreadyExportedToSameEndpoint) {
                        _log.info(String.format(
                                "Existing Export params for FileShare id: %1$s,  SecurityType: %2$s, " +
                                        "Permissions: %3$s, Root user mapping: %4$s, ",
                                id, fileExport.getSecurityType(), fileExport.getPermissions(), fileExport.getRootUserMapping()));

                        _log.info(String.format(
                                "Recieved Export params for FileShare id: %1$s,  SecurityType: %2$s, " +
                                        "Permissions: %3$s, Root user mapping: %4$s, ",
                                id, securityType, permissions, rootUserMapping));

                        if (!fileExport.getPermissions().equals(permissions)) {
                            throw APIException.badRequests.updatingFileSystemExportNotAllowed("permission");
                        }
                        if (!fileExport.getSecurityType().equals(securityType)) {
                            throw APIException.badRequests.updatingFileSystemExportNotAllowed("security type");
                        }
                        if (!fileExport.getRootUserMapping().equals(rootUserMapping)) {
                            throw APIException.badRequests.updatingFileSystemExportNotAllowed("root user mapping");
                        }
                    }
                }
            }
        }
    }

    /**
     * Export file system.
     * 
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param param
     *            File system export parameters
     * @param id
     *            the URN of a ViPR File system
     * @brief Create file export
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep export(@PathParam("id") URI id, FileSystemExportParam param)
            throws InternalException {

        _log.info("Export request recieved {}", id);

        // check file System
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");

        ArgValidator.checkFieldValueFromEnum(param.getPermissions(), "permissions",
                EnumSet.allOf(FileShareExport.Permissions.class));

        ArgValidator.checkFieldValueFromEnum(param.getSecurityType(), "type",
                EnumSet.allOf(FileShareExport.SecurityTypes.class));

        ArgValidator.checkFieldValueFromEnum(param.getProtocol(), "protocol",
                EnumSet.allOf(StorageProtocol.File.class));

        validateIpInterfacesRegistered(param.getEndpoints(), _dbClient);

        FileShare fs = queryResource(id);
        String task = UUID.randomUUID().toString();
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        // Check for VirtualPool whether it has NFS enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.NFS.name())
                && !vpool.getProtocols().contains(StorageProtocol.File.NFSv4.name())) {
            // Throw an error
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool doesn't support "
                    + StorageProtocol.File.NFS.name() + " or " + StorageProtocol.File.NFSv4 + " protocol");
        }

        // locate storage port for exporting file System
        StoragePort sport = _fileScheduler.placeFileShareExport(fs, param.getProtocol(), param.getEndpoints());

        String path = fs.getPath();
        String mountPath = fs.getMountPath();
        String subDirectory = param.getSubDirectory();
        if (param.getSubDirectory() != null && !param.getSubDirectory().equalsIgnoreCase("null") && param.getSubDirectory().length() > 0) {
            // Add subdirectory to the path as this is a subdirectory export
            path += "/" + param.getSubDirectory();
            mountPath += "/" + param.getSubDirectory();
        }

        FSExportMap exportMap = fs.getFsExports();
        if (exportMap != null) {
            Iterator it = fs.getFsExports().keySet().iterator();
            boolean exportExists = false;
            while (it.hasNext()) {
                String fsExpKey = (String) it.next();
                FileExport fileExport = fs.getFsExports().get(fsExpKey);
                if (fileExport.getPath().equalsIgnoreCase(path)) {
                    exportExists = true;
                    break;
                }
            }
            if (exportExists) {
                throw APIException.badRequests.fileSystemHasExistingExport();
            }
        }

        FileShareExport export = new FileShareExport(param.getEndpoints(), param.getSecurityType(), param.getPermissions(),
                param.getRootUserMapping(), param.getProtocol(), sport.getPortGroup(), sport.getPortNetworkId(), path, mountPath,
                subDirectory, param.getComments());

        _log.info(String.format(
                "FileShareExport --- FileShare id: %1$s, Clients: %2$s, StoragePort: %3$s, SecurityType: %4$s, " +
                        "Permissions: %5$s, Root user mapping: %6$s, Protocol: %7$s, path: %8$s, mountPath: %9$s, SubDirectory: %10$s",
                id, export.getClients(), sport.getPortName(), export.getSecurityType(), export.getPermissions(),
                export.getRootUserMapping(), export.getProtocol(), export.getPath(), export.getMountPath(), export.getSubDirectory()));

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.EXPORT_FILE_SYSTEM);
        op.setDescription("Filesystem export");
        controller.export(device.getId(), fs.getId(), Arrays.asList(export), task);

        auditOp(OperationTypeEnum.EXPORT_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getId().toString(), device.getId().toString(), export.getClients(), param.getSecurityType(),
                param.getPermissions(), param.getRootUserMapping(), param.getProtocol());

        return toTask(fs, task, op);
    }

    /**
     * @Deprecated use @Path("/{id}/export") instead
     * 
     *             Existing file system exports may have their list of endpoints updated. The permission, security, or
     *             root user
     *             mapping of an existing export may not be changed. In order to change one of these attributes, the
     *             export must be
     *             first deleted and then created with the new value.
     * 
     * @param id
     *            the URN of a ViPR Project
     * @param protocol
     *            Protocol valid values - NFS,NFSv4,CIFS
     * @param securityType
     *            Security type valid values - sys,krb5,krb5i,krb5p
     * @param permissions
     *            Permissions valid values - ro,rw,root
     * @param rootUserMapping
     *            Root user mapping
     * @brief Update file system export.
     *        <p>
     *        Use /file/filesystems/{id}/export instead
     * @return Task resource representation
     * @throws InternalException
     */
    @Deprecated
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports/{protocol},{secType},{perm},{root_mapping}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateExport(@PathParam("id") URI id,
            @PathParam("protocol") String protocol,
            @PathParam("secType") String securityType,
            @PathParam("perm") String permissions,
            @PathParam("root_mapping") String rootUserMapping,
            FileExportUpdateParam param) throws InternalException {

        _log.info("Export update request received {}", id);

        // Validate the input.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        ArgValidator.checkFieldNotNull(protocol, "protocol");
        ArgValidator.checkFieldNotNull(securityType, "secType");
        ArgValidator.checkFieldNotNull(permissions, "perm");
        ArgValidator.checkFieldNotNull(rootUserMapping, "root_mapping");
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));
        ArgValidator.checkFieldNotEmpty(fs.getFsExports(), "exports");

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        String path = fs.getPath();
        _log.info("update export for path {} ", path);
        _log.info(String.format("securityType %1$s, permissions %2$s, rootMapping %3$s, protocol %4$s FileSystem %5$s", securityType,
                permissions, rootUserMapping, protocol, path));

        FileExport fExport = fs.getFsExports().get(FileExport.exportLookupKey(protocol, securityType, permissions, rootUserMapping, path));
        if (fExport == null) {
            throw APIException.badRequests.invalidParameterFileSystemNoSuchExport();
        }

        validateIpInterfacesRegistered(param.getAdd(), _dbClient);
        verifyExports(fs, param, permissions, securityType, rootUserMapping, path);

        String task = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.EXPORT_FILE_SYSTEM);

        // Update the list.
        List<String> clients = fExport.getClients();
        if (param.getAdd() != null) {
            for (String addEndpoint : param.getAdd()) {
                clients.add(addEndpoint);
            }
        }

        if (param.getRemove() != null) {
            for (String delEndpoint : param.getRemove()) {
                clients.remove(delEndpoint);
            }
        }

        FileShareExport export = new FileShareExport(clients, securityType, permissions,
                rootUserMapping, protocol, fExport.getStoragePortName(), fExport.getStoragePort(), path, fExport.getMountPath(),
                fExport.getSubDirectory(), param.getComments());

        controller.export(device.getId(), fs.getId(), Arrays.asList(export), task);

        auditOp(OperationTypeEnum.EXPORT_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getId().toString(), device.getId().toString(), export.getClients(), securityType,
                permissions, rootUserMapping, protocol);

        return toTask(fs, task, op);
    }

    /**
     * @Deprecated use @Path("/{id}/export") instead
     * 
     *             <p>
     *             NOTE: This is an asynchronous operation.
     * @param id
     *            the URN of a ViPR Project
     * @param protocol
     *            Protocol valid values - NFS,NFSv4,CIFS
     * @param securityType
     *            Security type valid values - sys,krb5,krb5i,krb5p
     * @param permissions
     *            Permissions valid values - ro,rw,root
     * @param rootUserMapping
     *            Root user mapping
     * @brief Delete file system export.
     *        <p>
     *        Use /file/filesystems/{id}/export instead
     * @return Task resource representation
     * @throws InternalException
     */
    @Deprecated
    @DELETE
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports/{protocol},{secType},{perm},{root_mapping}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep unexport(@PathParam("id") URI id,
            @PathParam("protocol") String protocol, @PathParam("secType") String securityType,
            @PathParam("perm") String permissions,
            @PathParam("root_mapping") String rootUserMapping,
            @QueryParam("subDirectory") String subDirectory) throws InternalException {

        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        ArgValidator.checkFieldNotNull(protocol, "protocol");
        ArgValidator.checkFieldNotNull(securityType, "secType");
        ArgValidator.checkFieldNotNull(permissions, "perm");
        ArgValidator.checkFieldNotNull(rootUserMapping, "root_mapping");
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        String task = UUID.randomUUID().toString();

        if (fs.getFsExports() == null) {
            // No exports present. Return success.
            return getSuccessResponse(fs, task, ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM, "Export does not exist");
        }
        if (fs.getStoragePort() == null) {
            // port associated with export, fail the operation
            return getFailureResponse(fs, task, ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM,
                    "No storage port associated with " + fs.getLabel());
        }

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());

        String path = fs.getPath();
        String mountPath = fs.getMountPath();
        if (subDirectory != null && subDirectory.length() > 0) {
            // Add subdirectory to the path as this is a subdirectory export
            path += "/" + subDirectory;
            mountPath += "/" + subDirectory;
        }

        FileExport fExport = null;
        _log.info("unexport subdirectory passed value is {}", subDirectory);
        if (subDirectory != null) {
            _log.info("unexport subdirectory {} with path {} ", subDirectory, path);
            _log.info(String.format("securityType %1$s, permissions %2$s, rootMapping %3$s, protocol %4$s subDirectory %5$s", securityType,
                    permissions, rootUserMapping, protocol, path));
            fExport = fs.getFsExports().get(FileExport.exportLookupKey(protocol, securityType, permissions, rootUserMapping, path));
        } else {
            _log.info("unexport FS  {} with path {} ", fs.getName(), path);
            _log.info(String.format("securityType %1$s, permissions %2$s, rootMapping %3$s, protocol %4$s FileSystem %5$s", securityType,
                    permissions, rootUserMapping, protocol, path));
            fExport = fs.getFsExports().get(FileExport.exportLookupKey(protocol, securityType, permissions, rootUserMapping, path));
        }

        if (fExport == null) {
            // No export to unexport, return success.
            return getSuccessResponse(fs, task, ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM, "Export does not exist");
        }
        fExport.setStoragePort(fs.getStoragePort().toString());

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM);

        op.setDescription("Filesystem unexport");
        List<String> endpoints = new ArrayList<String>(); // empty list for unexport
        FileShareExport export = new FileShareExport(endpoints, securityType, permissions, rootUserMapping, protocol,
                fExport.getStoragePortName(), fExport.getStoragePort(), fExport.getPath());
        export.setIsilonId(fExport.getIsilonId());
        controller.unexport(device.getId(), fs.getId(), Arrays.asList(export), task);

        auditOp(OperationTypeEnum.UNEXPORT_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getId().toString(), securityType, permissions, rootUserMapping, protocol);

        return toTask(fs, task, op);
    }

    private TaskResourceRep getSuccessResponse(FileShare fs, String task, ResourceOperationTypeEnum type, String message) {
        Operation op = new Operation();
        op.setResourceType(type);
        op.ready(message);
        _dbClient.createTaskOpStatus(FileShare.class, fs.getId(), task, op);
        return toTask(fs, task, op);
    }

    private TaskResourceRep getFailureResponse(FileShare fs, String task, ResourceOperationTypeEnum type, String message) {
        Operation op = new Operation();
        op.setResourceType(type);
        op.setMessage(message);
        ServiceCoded coded = ServiceError.buildServiceError(
                ServiceCode.API_BAD_REQUEST, message);
        op.error(coded);
        _dbClient.createTaskOpStatus(FileShare.class, fs.getId(), task, op);
        return toTask(fs, task, op);
    }

    /**
     * Get list of SMB shares for the specified file system.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @brief List file system SMB shares
     * @return List of file system shares.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public FileSystemShareList getFileSystemShareList(@PathParam("id") URI id) {

        _log.info(String.format("Get list of SMB file shares for file system: %1$s", id));
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fileShare = queryResource(id);

        FileSystemShareList fileShareListResponse = new FileSystemShareList();

        if (fileShare.getInactive()) {
            return fileShareListResponse;
        }

        SMBShareMap smbShareMap = fileShare.getSMBFileShares();

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
                shareParam.setMaxUsers(UNLIMITED_USERS);
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
     * Get the SMB share for the specified file system.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @param shareName
     *            file system share name
     * @brief List file system SMB shares
     * @return List of file system shares.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares/{shareName}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public SmbShareResponse getFileSystemShare(@PathParam("id") URI id,
            @PathParam("shareName") String shareName) throws InternalException {

        _log.info(String.format("Get SMB file share %s for file system: %s", shareName, id.toString()));
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        ArgValidator.checkFieldNotNull(shareName, "shareName");
        FileShare fileShare = queryResource(id);

        SMBFileShare smbShare = null;

        FileSystemShareList fileShareListResponse = new FileSystemShareList();

        SmbShareResponse shareParam = null;

        if (fileShare.getSMBFileShares() != null) {
            _log.info("Number of SMBShares found {} and looking for share to read {} ", fileShare.getSMBFileShares().size(), shareName);
            _log.info(String.format("Get file system share: file system id: %1$s, share name %2$s", id, shareName));
            smbShare = fileShare.getSMBFileShares().get(shareName);
            if (smbShare == null) {
                _log.info("CIFS share does not exist {}", shareName);
            } else {
                shareParam = new SmbShareResponse();

                shareParam.setShareName(smbShare.getName());
                shareParam.setDescription(smbShare.getDescription());
                shareParam.setMaxUsers(Integer.toString(smbShare.getMaxUsers()));
                // Check for "unlimited"
                if (shareParam.getMaxUsers().equals("-1")) {
                    shareParam.setMaxUsers(UNLIMITED_USERS);
                }

                shareParam.setPermissionType(smbShare.getPermissionType());
                shareParam.setPermission(smbShare.getPermission());
                shareParam.setMountPoint(smbShare.getMountPoint());
                shareParam.setPath(smbShare.getPath());
            }
        }

        return shareParam;
    }

    /**
     * Expand file system.
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param param
     *            File system expansion parameters
     * @param id
     *            the URN of a ViPR File system
     * @brief Expand file system
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/expand")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep expand(@PathParam("id") URI id, FileSystemExpandParam param)
            throws InternalException {

        _log.info(String.format(
                "FileShareExpand --- FileShare id: %1$s, New Size: %2$s",
                id, param.getNewSize()));
        // check file System
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        Long newFSsize = SizeUtil.translateSize(param.getNewSize());
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));
        if (newFSsize <= 0) {
            throw APIException.badRequests.parameterMustBeGreaterThan("new_size", 0);
        }

        // checkQuota
        long expand = newFSsize - fs.getCapacity();

        final long MIN_EXPAND_SIZE = SizeUtil.translateSize("1MB") + 1;
        if (expand < MIN_EXPAND_SIZE) {
            throw APIException.badRequests.invalidParameterBelowMinimum("new_size", newFSsize, fs.getCapacity() + MIN_EXPAND_SIZE, "bytes");
        }

        Project project = _dbClient.queryObject(Project.class, fs.getProject().getURI());
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, fs.getTenant().getURI());
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        CapacityUtils.validateQuotasForProvisioning(_dbClient, vpool, project, tenant, expand, "filesystem");

        String task = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.EXPAND_FILE_SYSTEM);
        op.setDescription("Filesystem expand");

        FileServiceApi fileServiceApi = getFileShareServiceImpl(fs, _dbClient);
        try {
            fileServiceApi.expandFileShare(fs, newFSsize, task);
        } catch (InternalException e) {
            if (_log.isErrorEnabled()) {
                _log.error("Expand File Size error", e);
            }

            FileShare fileShare = _dbClient.queryObject(FileShare.class, fs.getId());
            op = fs.getOpStatus().get(task);
            op.error(e);
            fileShare.getOpStatus().updateTaskStatus(task, op);
            _dbClient.updateObject(fs);
            throw e;
        }

        return toTask(fs, task, op);
    }

    /**
     * Expand file system.
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param param File system expansion parameters
     * @param id the URN of a ViPR File system
     * @brief Expand file system
     * @return Task resource representation
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep update(@PathParam("id") URI id, FileSystemUpdateParam param)
            throws InternalException {

        _log.info(String.format("FileShareUpdate --- FileShare id: %1$s", id));
        // check file System
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());

        Boolean deviceSupportsSoftLimit = device.getSupportSoftLimit() != null ? device.getSupportSoftLimit() : false;
        Boolean deviceSupportsNotificationLimit = device.getSupportNotificationLimit() != null ? device.getSupportNotificationLimit()
                : false;

        if (param.getSoftLimit() != 0 && !deviceSupportsSoftLimit) {
            throw APIException.badRequests.unsupportedParameterForStorageSystem("soft_limit");
        }

        if (param.getNotificationLimit() != 0 && !deviceSupportsNotificationLimit) {
            throw APIException.badRequests.unsupportedParameterForStorageSystem("notification_limit");
        }

        ArgValidator.checkFieldMaximum(param.getSoftLimit(), 100, "soft_limit");
        ArgValidator.checkFieldMaximum(param.getNotificationLimit(), 100, "notification_limit");

        if (param.getSoftLimit() != 0L) {
            ArgValidator.checkFieldMinimum(param.getSoftGrace(), 1L, "soft_grace");
        }

        fs.setSoftLimit(Long.valueOf(param.getSoftLimit()));
        fs.setSoftGracePeriod(param.getSoftGrace());
        fs.setNotificationLimit(Long.valueOf(param.getNotificationLimit()));

        _dbClient.updateObject(fs);

        FileController controller = getController(FileController.class,
                device.getSystemType());

        String task = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.UPDATE_FILE_SYSTEM);
        controller.modifyFS(fs.getStorageDevice(), fs.getPool(), id, task);
        op.setDescription("Filesystem update");
        auditOp(OperationTypeEnum.UPDATE_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getId().toString(), fs.getCapacity(), param.getNotificationLimit(),
                param.getSoftLimit(), param.getSoftGrace());

        return toTask(fs, task, op);
    }

    /**
     * Create SMB file share
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @param param
     *            File system share parameters
     * @brief Create file system SMB share
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep share(@PathParam("id") URI id, FileSystemShareParam param)
            throws InternalException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        ArgValidator.checkFieldNotNull(param.getShareName(), "name");
        ArgValidator.checkFieldNotEmpty(param.getShareName(), "name");
        FileShare fs = queryResource(id);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());

        String task = UUID.randomUUID().toString();

        // Check for VirtualPool whether it has CIFS enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.CIFS.name())) {
            // Throw an error
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool Doesnt support "
                    + StorageProtocol.File.CIFS.name() + " protocol");
        }
        // locate storage port for sharing file System
        // Select IP port of the storage array, owning the file system, which belongs to the same varray as the file
        // system.
        StoragePort sport = _fileScheduler.placeFileShareExport(fs, StorageProtocol.File.CIFS.name(), null);

        // Check if maxUsers is "unlimited" and set it to -1 in this case.
        if (param.getMaxUsers().equalsIgnoreCase(UNLIMITED_USERS)) {
            param.setMaxUsers("-1");
        }

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        // If value of permission is not provided, set the value to change
        if (param.getPermission() == null || param.getPermission().isEmpty()) {
            param.setPermission(FileSMBShare.Permission.change.name());
        }

        // Let us make sure that a share with the same name does not already exist.
        String shareName = param.getShareName();
        if (CifsShareUtility.doesShareExist(fs, shareName)) {
            _log.error("CIFS share: {}, already exists", shareName);
            throw APIException.badRequests.duplicateEntityWithField("CIFS share", "name");
        }

        String path = fs.getPath();
        _log.info("Path {}", path);
        _log.info("Param Share Name : {} SubDirectory : {}", param.getShareName(), param.getSubDirectory());

        boolean isSubDirPath = false;

        if (param.getSubDirectory() != null && param.getSubDirectory().length() > 0) {
            path += "/" + param.getSubDirectory();
            isSubDirPath = true;
            _log.info("Sub-directory path {}", path);
        }

        FileSMBShare smbShare = new FileSMBShare(shareName, param.getDescription(), param.getPermissionType(),
                param.getPermission(), param.getMaxUsers(), null, path);
        smbShare.setStoragePortName(sport.getPortName());
        smbShare.setStoragePortNetworkId(sport.getPortNetworkId());
        smbShare.setStoragePortGroup(sport.getPortGroup());
        smbShare.setSubDirPath(isSubDirPath);

        _log.info(String.format(
                "Create file system share --- File system id: %1$s, Share name: %2$s, StoragePort: %3$s, PermissionType: %4$s, " +
                        "Permissions: %5$s, Description: %6$s, maxUsers: %7$s",
                id, smbShare.getName(), sport.getPortName(), smbShare.getPermissionType(), smbShare.getPermission(),
                smbShare.getDescription(), smbShare.getMaxUsers()));

        _log.info("SMB share path {}", smbShare.getPath());

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.CREATE_FILE_SYSTEM_SHARE);
        controller.share(device.getId(), fs.getId(), smbShare, task);
        auditOp(OperationTypeEnum.CREATE_FILE_SYSTEM_SHARE, true, AuditLogManager.AUDITOP_BEGIN,
                smbShare.getName(), smbShare.getPermissionType(), smbShare.getPermission(),
                smbShare.getMaxUsers(), smbShare.getDescription(), fs.getId().toString());

        return toTask(fs, task, op);
    }

    /**
     * Delete specified SMB share.
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @param shareName
     *            file system share name
     * @brief Delete file system SMB share
     * @return Task resource representation
     * @throws InternalException
     */
    @DELETE
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares/{shareName}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep deleteShare(@PathParam("id") URI id,
            @PathParam("shareName") String shareName) throws InternalException {

        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        ArgValidator.checkFieldNotNull(shareName, "shareName");
        ArgValidator.checkFieldNotNull(fs, "filesystem");
        String task = UUID.randomUUID().toString();
        SMBFileShare smbShare = null;

        if (!CifsShareUtility.doesShareExist(fs, shareName)) {
            _log.error("CIFS share does not exist {}", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(id, shareName);
        }

        smbShare = fs.getSMBFileShares().get(shareName);
        _log.info("Deleteing SMBShare {}", shareName);

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());
        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.DELETE_FILE_SYSTEM_SHARE);
        FileSMBShare fileSMBShare = new FileSMBShare(shareName, smbShare.getDescription(), smbShare.getPermissionType(),
                smbShare.getPermission(), Integer.toString(smbShare.getMaxUsers()), smbShare.getNativeId(), smbShare.getPath());
        fileSMBShare.setStoragePortGroup(smbShare.getPortGroup());
        controller.deleteShare(device.getId(), fs.getId(), fileSMBShare, task);
        auditOp(OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE, true, AuditLogManager.AUDITOP_BEGIN,
                smbShare.getName(), smbShare.getPermissionType(), smbShare.getPermission(),
                smbShare.getMaxUsers(), smbShare.getDescription(), fs.getId().toString());

        return toTask(fs, task, op);
    }

    /**
     * Get file system snapshots
     * 
     * @param id
     *            the URN of a ViPR File system
     * @brief List file system snapshots
     * @return List of snapshots
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public SnapshotList getSnapshots(@PathParam("id") URI id) {
        List<URI> snapIDList = _dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(id));
        _log.debug("getSnapshots: FS {}: {} ", id.toString(), snapIDList.toString());
        List<Snapshot> snapList = _dbClient.queryObject(Snapshot.class, snapIDList);
        SnapshotList list = new SnapshotList();
        for (Snapshot snap : snapList) {
            list.getSnapList().add(toNamedRelatedResource(snap));
        }
        return list;
    }

    /**
     * Create file system snapshot
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @param param
     *            file system snapshot parameters
     * @brief Create file system snapshot
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep snapshot(@PathParam("id") URI id, FileSystemSnapshotParam param) throws InternalException {
        String task = UUID.randomUUID().toString();
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class,
                device.getSystemType());
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (vpool == null) {
            throw APIException.badRequests.invalidParameterFileSystemHasNoVirtualPool(id);
        }
        if (getNumSnapshots(fs) >= vpool.getMaxNativeSnapshots()) {
            throw APIException.methodNotAllowed.maximumNumberSnapshotsReached();
        }

        // check duplicate fileshare snapshot names for this fileshare
        checkForDuplicateName(param.getLabel(), Snapshot.class, id, "parent", _dbClient);

        Snapshot snap = new Snapshot();
        snap.setId(URIUtil.createId(Snapshot.class));
        snap.setParent(new NamedURI(id, param.getLabel()));
        snap.setLabel(param.getLabel());
        snap.setOpStatus(new OpStatusMap());
        snap.setProject(new NamedURI(fs.getProject().getURI(), param.getLabel()));

        String convertedName = param.getLabel().replaceAll("[^\\dA-Za-z_]", "");
        _log.info("Original name {} and converted name {}", param.getLabel(), convertedName);
        snap.setName(convertedName);

        fs.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_FILE_SYSTEM_SNAPSHOT);
        snap.getOpStatus().createTaskStatus(task, op);
        fs.getOpStatus().createTaskStatus(task, op);
        _dbClient.createObject(snap);
        _dbClient.persistObject(fs);
        // find storageport for fs and based on protocol
        if (null == fs.getStoragePort()) {
            StoragePort storagePort;
            try {
                // assigned storageport to fs
                storagePort = _fileScheduler.placeFileShareExport(fs, PROTOCOL_NFS, null);
                _log.info(
                        "FS is not mounted so we are mounting the FS first and then creating the Snapshot and the returned storage port- {} and supported protocol-{}",
                        storagePort.getPortName(), PROTOCOL_NFS);
            } catch (APIException e) {
                // if we don't find port for NFS protocol then
                // in catch exception we get port for CIFS protocol
                storagePort = _fileScheduler.placeFileShareExport(fs, PROTOCOL_CIFS, null);
                _log.info(
                        "FS is not mounted so we are mounting the FS first and then creating the Snapshot and the returned storage port- {} and supported protocol-{}",
                        storagePort.getPortName(), PROTOCOL_NFS);
            }
        }

        // send request to controller
        try {
            controller.snapshotFS(device.getId(), snap.getId(), fs.getId(), task);
        } catch (InternalException e) {
            snap.setInactive(true);
            _dbClient.persistObject(snap);

            // treating all controller exceptions as internal error for now. controller
            // should discriminate between validation problems vs. internal errors
            throw e;
        }

        auditOp(OperationTypeEnum.CREATE_FILE_SYSTEM_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                snap.getLabel(), snap.getId().toString(), fs.getId().toString());

        fs = _dbClient.queryObject(FileShare.class, id);
        _log.debug("Before sending response, FS ID : {}, Taks : {} ; Status {}", fs.getOpStatus().get(task), fs.getOpStatus().get(task)
                .getStatus());

        return toTask(snap, task, op);
    }

    // Counts and returns the number of snapshots on a filesystem
    Integer getNumSnapshots(FileShare fs) {
        Integer numSnapshots = 0;
        URI fsId = fs.getId();
        URIQueryResultList snapIDList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(fsId), snapIDList);
        while (snapIDList.iterator().hasNext()) {
            URI uri = snapIDList.iterator().next();
            Snapshot snap = _dbClient.queryObject(Snapshot.class, uri);
            if (!snap.getInactive()) {
                numSnapshots++;
            }
        }
        return numSnapshots;
    }

    /**
     * Deactivate file system, this will move the Filesystem to a "marked-for-delete" state
     * it will be deleted when all references to this filesystem of type Snapshot are deleted.
     * The optional forceDelete param will delete snapshots and exports in case of VNXFile when it sets to true.
     * 
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @param param
     *            File system delete param for optional force delete
     * @brief Delete file system
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep deactivateFileSystem(@PathParam("id") URI id, FileSystemDeleteParam param) throws InternalException {

        String task = UUID.randomUUID().toString();
        _log.info(String.format(
                "FileSystemDelete --- FileSystem id: %1$s, Task: %2$s, ForceDelete: %3$s ,DeleteType: %4$s", id, task,
                param.getForceDelete(), param.getDeleteType()));
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        if (!param.getForceDelete()) {
            ArgValidator.checkReference(FileShare.class, id, checkForDelete(fs));
            if (!fs.getFilePolicies().isEmpty()) {
                throw APIException.badRequests
                        .resourceCannotBeDeleted("Please unassign the policy from file system. " + fs.getLabel());
            }
        }
        StringBuffer notSuppReasonBuff = new StringBuffer();
        // Verify the file system is having any active replication targets!!
        if (filesystemHasActiveReplication(fs, notSuppReasonBuff, param.getDeleteType(), param.getForceDelete())) {
            throw APIException.badRequests
                    .resourceCannotBeDeleted(notSuppReasonBuff.toString());
        }
        List<URI> fileShareURIs = new ArrayList<URI>();
        fileShareURIs.add(id);
        FileServiceApi fileServiceApi = getFileShareServiceImpl(fs, _dbClient);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.DELETE_FILE_SYSTEM);
        op.setDescription("Filesystem deactivate");

        auditOp(OperationTypeEnum.DELETE_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getId().toString(), device.getId().toString());
        try {
            fileServiceApi.deleteFileSystems(device.getId(), fileShareURIs,
                    param.getDeleteType(), param.getForceDelete(), false, task);
        } catch (InternalException e) {
            if (_log.isErrorEnabled()) {
                _log.error("Delete error", e);
            }

            FileShare fileShare = _dbClient.queryObject(FileShare.class, fs.getId());
            op = fs.getOpStatus().get(task);
            op.error(e);
            fileShare.getOpStatus().updateTaskStatus(task, op);
            _dbClient.persistObject(fs);
            throw e;
        }

        return toTask(fs, task, op);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param
     *            POST data containing the id list.
     * @brief List data of file share resources
     * @return list of representations.
     * 
     * @throws DatabaseException
     *             When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public FileShareBulkRep getBulkResources(BulkIdParam param) {
        return (FileShareBulkRep) super.getBulkResources(param);
    }

    /**
     * Validates the clients are registered IP Interfaces if they exist in the database.
     * 
     * @param clients
     *            list of clients
     * @param dbClient
     *            DbClient
     */
    public static void validateIpInterfacesRegistered(List<String> clients, DbClient dbClient) {
        if (clients != null) {
            for (String client : clients) {
                List<IpInterface> ipInterfaces = CustomQueryUtility.queryActiveResourcesByAltId(
                        dbClient, IpInterface.class, "ipAddress", client);
                for (IpInterface ipInterface : ipInterfaces) {
                    if (ipInterface.getRegistrationStatus().equals(RegistrationStatus.UNREGISTERED.toString())) {
                        throw APIException.badRequests.invalidParameterIpInterfaceIsDeregistered(client);
                    }
                }
            }
        }
    }

    /**
     * Allocate, initialize and persist state of the fileSystem being created.
     * 
     * @param param
     * @param project
     * @param tenantOrg
     * @param neighborhood
     * @param vpool
     * @param flags
     * @param placement
     * @param token
     * @return
     */
    private FileShare prepareFileSystem(FileSystemParam param, Project project, TenantOrg tenantOrg,
            VirtualArray neighborhood, VirtualPool vpool, DataObject.Flag[] flags, FileRecommendation placement, String token) {
        _log.info("prepareFile System");
        StoragePool pool = null;
        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));

        fs.setLabel(param.getLabel());

        // No need to generate any name -- Since the requirement is to use the customizing label we should use the same.
        // Stripping out the special characters like ; /-+!@#$%^&())";:[]{}\ | but allow underscore character _
        String convertedName = param.getLabel().replaceAll("[^\\dA-Za-z\\_]", "");
        _log.info("Original name {} and converted name {}", param.getLabel(), convertedName);
        fs.setName(convertedName);
        Long fsSize = SizeUtil.translateSize(param.getSize());
        fs.setCapacity(fsSize);
        fs.setVirtualPool(param.getVpool());
        if (project != null) {
            fs.setProject(new NamedURI(project.getId(), fs.getLabel()));
        }
        fs.setTenant(new NamedURI(tenantOrg.getId(), param.getLabel()));
        fs.setVirtualArray(neighborhood.getId());

        if (null != placement.getSourceStoragePool()) {
            pool = _dbClient.queryObject(StoragePool.class, placement.getSourceStoragePool());
            if (null != pool) {
                fs.setProtocol(new StringSet());
                fs.getProtocol().addAll(VirtualPoolUtil.getMatchingProtocols(vpool.getProtocols(), pool.getProtocols()));
            }
        }

        fs.setStorageDevice(placement.getSourceStorageSystem());
        fs.setPool(placement.getSourceStoragePool());
        if (param.getSoftLimit() != 0) {
            fs.setSoftLimit(new Long(param.getSoftLimit()));
        }
        if (param.getNotificationLimit() != 0) {
            fs.setNotificationLimit(new Long(param.getNotificationLimit()));
        }
        if (param.getSoftGrace() > 0) {
            fs.setSoftGracePeriod(new Integer(param.getSoftGrace()));
        }
        if (placement.getStoragePorts() != null && !placement.getStoragePorts().isEmpty()) {
            fs.setStoragePort(placement.getStoragePorts().get(0));
        }

        // When a VPool supports "thin" provisioning
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(vpool.getSupportedProvisioningType())) {
            fs.setThinlyProvisioned(Boolean.TRUE);
        }

        if (placement.getvNAS() != null) {
            fs.setVirtualNAS(placement.getvNAS());
        }

        fs.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_FILE_SYSTEM);
        fs.getOpStatus().createTaskStatus(token, op);
        if (flags != null) {
            fs.addInternalFlags(flags);
        }
        _dbClient.createObject(fs);
        return fs;
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
        return ResourceTypeEnum.FILE;
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
            PermissionsHelper permissionsHelper) {
        return new ProjOwnedResRepFilter(user, permissionsHelper, FileShare.class);
    }

    /**
     * Create Quota directory for a file system
     * <p>
     * NOTE: This is an asynchronous operation.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @param param
     *            File system Quota directory parameters
     * @brief Create file system Quota directory
     * @return Task resource representation
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/quota-directories")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep createQuotaDirectory(@PathParam("id") URI id, QuotaDirectoryCreateParam param)
            throws InternalException {
        _log.info("FileService::createQtree Request recieved {}", id);
        String origQtreeName = param.getQuotaDirName();
        ArgValidator.checkQuotaDirName(origQtreeName, "name");

        ArgValidator.checkFieldMaximum(param.getSoftLimit(), 100, "softLimit");
        ArgValidator.checkFieldMaximum(param.getNotificationLimit(), 100, "notificationLimit");

        if (param.getSoftLimit() != 0L) {
            ArgValidator.checkFieldMinimum(param.getSoftGrace(), 1L, "softGrace");
        }

        // check duplicate QuotaDirectory names for this fileshare
        checkForDuplicateName(origQtreeName, QuotaDirectory.class, id, "parent", _dbClient);

        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        if (param.getSecurityStyle() != null) {
            ArgValidator.checkFieldValueFromEnum(param.getSecurityStyle(), "security_style",
                    EnumSet.allOf(FileShareQuotaDirectory.SecurityStyles.class));
        }

        // Get the FileSystem object from the URN
        FileShare fs = queryResource(id);
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        // Create the QuotaDirectory object for the DB
        QuotaDirectory quotaDirectory = new QuotaDirectory();
        quotaDirectory.setId(URIUtil.createId(QuotaDirectory.class));
        quotaDirectory.setParent(new NamedURI(id, origQtreeName)); // ICICIC - Curious !
        quotaDirectory.setLabel(origQtreeName);
        quotaDirectory.setOpStatus(new OpStatusMap());
        quotaDirectory.setProject(new NamedURI(fs.getProject().getURI(), origQtreeName));
        quotaDirectory.setTenant(new NamedURI(fs.getTenant().getURI(), origQtreeName));
        quotaDirectory.setSoftLimit(
                param.getSoftLimit() != 0 ? param.getSoftLimit() : fs.getSoftLimit() != null ? fs.getSoftLimit().intValue() : 0);
        quotaDirectory.setSoftGrace(
                param.getSoftGrace() != 0 ? param.getSoftGrace() : fs.getSoftGracePeriod() != null ? fs.getSoftGracePeriod() : 0);
        quotaDirectory.setNotificationLimit(param.getNotificationLimit() != 0 ? param.getNotificationLimit()
                : fs.getNotificationLimit() != null ? fs.getNotificationLimit().intValue() : 0);

        String convertedName = origQtreeName.replaceAll("[^\\dA-Za-z_]", "");
        _log.info("FileService::QuotaDirectory Original name {} and converted name {}", origQtreeName, convertedName);
        quotaDirectory.setName(convertedName);

        if (param.getOpLock() != null) {
            quotaDirectory.setOpLock(param.getOpLock());
        } else {
            quotaDirectory.setOpLock(true);
        }

        if (param.getSecurityStyle() != null) {
            quotaDirectory.setSecurityStyle(param.getSecurityStyle());
        } else {
            quotaDirectory.setSecurityStyle(SecurityStyles.parent.toString());
        }

        if (param.getSize() != null) {
            Long quotaSize = SizeUtil.translateSize(param.getSize()); // converts the input string in format "<value>GB"
                                                                      // to bytes
            ArgValidator.checkFieldMaximum(quotaSize, fs.getCapacity(), SizeUtil.SIZE_B, "size");
            quotaDirectory.setSize(quotaSize);
        } else {
            quotaDirectory.setSize((long) 0);
        }

        fs.setOpStatus(new OpStatusMap());

        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_FILE_SYSTEM_QUOTA_DIR);
        quotaDirectory.getOpStatus().createTaskStatus(task, op);
        fs.getOpStatus().createTaskStatus(task, op);
        _dbClient.createObject(quotaDirectory);
        _dbClient.persistObject(fs);

        // Create an object of type "FileShareQuotaDirectory" to be passed into the south-bound layers.
        FileShareQuotaDirectory qt = new FileShareQuotaDirectory(quotaDirectory);

        // Now get ready to make calls into the controller
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());
        try {
            controller.createQuotaDirectory(device.getId(), qt, fs.getId(), task);
        } catch (InternalException e) {
            quotaDirectory.setInactive(true);
            _dbClient.persistObject(quotaDirectory);

            // treating all controller exceptions as internal error for now. controller
            // should discriminate between validation problems vs. internal errors
            throw e;
        }

        auditOp(OperationTypeEnum.CREATE_FILE_SYSTEM_QUOTA_DIR, true, AuditLogManager.AUDITOP_BEGIN,
                quotaDirectory.getLabel(), quotaDirectory.getId().toString(), fs.getId().toString());

        fs = _dbClient.queryObject(FileShare.class, id);
        _log.debug("FileService::QuotaDirectory Before sending response, FS ID : {}, Taks : {} ; Status {}", fs.getOpStatus().get(task), fs
                .getOpStatus().get(task).getStatus());

        return toTask(quotaDirectory, task, op);
    }

    /**
     * Get list of quota directories for the specified file system.
     * 
     * @param id
     *            the URN of a ViPR File system
     * @brief List file system quota directories
     * @return List of file system quota directories.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/quota-directories")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public QuotaDirectoryList getQuotaDirectoryList(@PathParam("id") URI id) {

        _log.info(String.format("Get list of quota directories for file system: %1$s", id));
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fileShare = queryResource(id);

        QuotaDirectoryList quotaDirList = new QuotaDirectoryList();

        if (fileShare.getInactive()) {
            return quotaDirList;
        }

        // Get SMB share map from file system
        List<QuotaDirectory> quotaDirs = queryDBQuotaDirectories(fileShare);

        // Process each share from the map and add its data to shares in response list.
        for (QuotaDirectory quotaDir : quotaDirs) {
            quotaDirList.getQuotaDirs().add(toNamedRelatedResource(quotaDir));
        }

        return quotaDirList;
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

    /**
     * 
     * Existing file system exports may have their list of export rules updated.
     * 
     * @param id
     *            the URN of a ViPR fileSystem
     * @param subDir
     *            sub-directory within a filesystem
     * @brief Update file system export
     * @return Task resource representation
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/export")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateFSExportRules(@PathParam("id") URI id,
            @QueryParam("subDir") String subDir,
            FileShareExportUpdateParams param) throws InternalException {

        // log input received.
        _log.info("Update FS Export Rules : request received for {}  with {}", id, param);
        String task = UUID.randomUUID().toString();
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        // Check for VirtualPool whether it has NFS enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.NFS.name())
                && !vpool.getProtocols().contains(StorageProtocol.File.NFSv4.name())) {
            // Throw an error
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool Doesnt support "
                    + StorageProtocol.File.NFS.name() + " or " + StorageProtocol.File.NFSv4 + " protocol");
        }

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        String path = fs.getPath();
        _log.info("Export path found {} ", path);

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SYSTEM);
        op.setDescription("Filesystem export rules update");

        try {
            _log.info("Sub Dir Provided {}", subDir);
            // Set Sub Directory
            param.setSubDir(subDir);

            // Validate the input
            ExportVerificationUtility exportVerificationUtility = new ExportVerificationUtility(_dbClient);
            exportVerificationUtility.verifyExports(fs, null, param);

            _log.info("No Errors found proceeding further {}, {}, {}", new Object[] { _dbClient, fs, param });

            controller.updateExportRules(device.getId(), fs.getId(), param, task);

            auditOp(OperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), param);

        } catch (URISyntaxException e) {
            _log.error("Error Processing Export Updates {}, {}", e.getMessage(), e);
        } catch (BadRequestException e) {
            op = _dbClient.error(FileShare.class, fs.getId(), task, e);
            _log.error("Error Processing Export Updates {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            // _log.error("Error Processing Export Updates {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }

        return toTask(fs, task, op);
    }

    /**
     * 
     * Existing file system exports may have their list of export rules deleted.
     * 
     * @param id
     *            the URN of a ViPR fileSystem
     * @param subDir
     *            sub-directory within a filesystem
     * @param allDirs
     *            All Dirs within a filesystem
     * @return Task resource representation
     * @throws InternalException
     */

    @DELETE
    @Path("/{id}/export")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep deleteFSExportRules(@PathParam("id") URI id,
            @QueryParam("allDirs") boolean allDirs,
            @QueryParam("subDir") String subDir) {

        // log input received.
        _log.info("Delete Export Rules : request received for {}, with allDirs : {}, subDir : {}", new Object[] { id, allDirs, subDir });
        String task = UUID.randomUUID().toString();
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        String path = fs.getPath();
        _log.info("Export path found {} ", path);

        // Before running operation check if subdirectory exists

        List<FileExportRule> exportFileRulesTemp = queryDBFSExports(fs);
        boolean subDirFound = false;

        if (subDir != null && !subDir.isEmpty()) {

            for (FileExportRule rule : exportFileRulesTemp) {
                if (rule.getExportPath().endsWith("/" + subDir)) {
                    subDirFound = true;
                }
            }

            if (!subDirFound) {
                _log.info("Sub-Directory {} doesnot exists, so deletion of Sub-Directory export rule from DB failed ", subDir);
                throw APIException.badRequests.subDirNotFound(subDir);
            }
        }

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM);
        op.setDescription("Filesystem unexport");

        try {

            controller.deleteExportRules(device.getId(), fs.getId(), allDirs, subDir, task);

            auditOp(OperationTypeEnum.UNEXPORT_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), allDirs, subDir);

        }

        catch (BadRequestException e) {
            op = _dbClient.error(FileShare.class, fs.getId(), task, e);
            _log.error("Error Processing Export Updates {}", e.getMessage(), e);
            // throw e;
        } catch (Exception e) {
            _log.error("Error Processing Export Updates {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }
        return toTask(fs, task, op);
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/export")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ExportRules getFSExportRules(@PathParam("id") URI id,
            @QueryParam("allDirs") boolean allDirs,
            @QueryParam("subDir") String subDir) {

        _log.info("Request recieved for Exports  with Id : {}  allDirs : {} subDir : {}",
                new Object[] { id, allDirs, subDir });

        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        ExportRules rules = new ExportRules();
        List<ExportRule> exportRule = new ArrayList<>();

        // Query All Export Rules Specific to a File System.
        List<FileExportRule> exports = queryDBFSExports(fs);
        _log.info("Number of existing exports found : {} ", exports.size());
        if (allDirs) {
            // ALl EXPORTS
            for (FileExportRule rule : exports) {
                ExportRule expRule = new ExportRule();
                // Copy Props
                copyPropertiesToSave(rule, expRule, fs);
                exportRule.add(expRule);
            }
        } else if (subDir != null && subDir.length() > 0) {
            // Filter for a specific Sub Directory export
            for (FileExportRule rule : exports) {
                if (rule.getExportPath().endsWith("/" + subDir)) {
                    ExportRule expRule = new ExportRule();
                    // Copy Props
                    copyPropertiesToSave(rule, expRule, fs);
                    exportRule.add(expRule);
                }
            }
        } else {
            // Filter for No SUBDIR - main export rules with no sub dirs
            for (FileExportRule rule : exports) {
                if (rule.getExportPath().equalsIgnoreCase(fs.getPath())) {
                    ExportRule expRule = new ExportRule();
                    // Copy Props
                    copyPropertiesToSave(rule, expRule, fs);
                    exportRule.add(expRule);
                }
            }
        }
        _log.info("Number of export rules returning {}", exportRule.size());
        if (!exportRule.isEmpty()) {
            rules.setExportRules(exportRule);
        }
        return rules;

    }

    /**
     * API to update ACLs of an existing share
     * 
     * @param id
     *            the file system URI
     * @param shareName
     *            name of the share
     * @param param
     *            request payload object of type <code>com.emc.storageos.model.file.CifsShareACLUpdateParams</code>
     * @return TaskResponse
     * @throws InternalException
     */

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares/{shareName}/acl")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateShareACL(@PathParam("id") URI id,
            @PathParam("shareName") String shareName,
            FileCifsShareACLUpdateParams param) throws InternalException {

        _log.info("Update file share acl request received. Filesystem: {}, Share: {}",
                id.toString(), shareName);
        _log.info("Request body: {}", param.toString());

        ArgValidator.checkFieldNotNull(shareName, "shareName");
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");

        FileShare fs = queryResource(id);
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());

        CifsShareUtility.checkForUpdateShareACLOperationOnStorage(
                device.getSystemType(), OperationTypeEnum.UPDATE_FILE_SYSTEM_SHARE_ACL.name());

        if (!CifsShareUtility.doesShareExist(fs, shareName)) {
            _log.error("CIFS share does not exist {}", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(fs.getId(), shareName);
        }

        String task = UUID.randomUUID().toString();

        // Check for VirtualPool whether it has CIFS enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.CIFS.name())) {
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool doesn't support "
                    + StorageProtocol.File.CIFS.name() + " protocol");
        }

        // Validate the input
        CifsShareUtility util = new CifsShareUtility(_dbClient, fs, null, shareName);
        util.verifyShareACLs(param);

        _log.info("Request payload verified. No errors found.");

        FileController controller = getController(FileController.class, device.getSystemType());

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.UPDATE_FILE_SYSTEM_SHARE_ACL);
        op.setDescription("Update file system share ACLs");

        controller.updateShareACLs(device.getId(), fs.getId(), shareName, param, task);

        auditOp(OperationTypeEnum.UPDATE_FILE_SYSTEM_SHARE_ACL, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getId().toString(), device.getId().toString(), param);

        return toTask(fs, task, op);
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/shares/{shareName}/acl")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ShareACLs getShareACLs(@PathParam("id") URI id,
            @PathParam("shareName") String shareName) {

        _log.info("Request recieved to get ACLs with Id: {}  shareName: {}",
                id, shareName);

        // Validate the FS id
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        ArgValidator.checkFieldNotNull(shareName, "shareName");
        FileShare fs = queryResource(id);

        if (!CifsShareUtility.doesShareExist(fs, shareName)) {
            _log.error("CIFS share does not exist {}", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(fs.getId(), shareName);
        }

        ShareACLs acls = new ShareACLs();
        CifsShareUtility util = new CifsShareUtility(_dbClient, fs, null, shareName);
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
    public TaskResourceRep deleteFileShareACL(@PathParam("id") URI id,
            @PathParam("shareName") String shareName) {

        // log input received.
        _log.info("Delete ACL of share: Request received for share: {}, of filesystem: {}",
                shareName, id);
        String taskId = UUID.randomUUID().toString();
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        ArgValidator.checkFieldNotNull(shareName, "shareName");
        FileShare fs = queryResource(id);

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());

        CifsShareUtility.checkForUpdateShareACLOperationOnStorage(
                device.getSystemType(), OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE_ACL.name());

        if (!CifsShareUtility.doesShareExist(fs, shareName)) {
            _log.error("CIFS share does not exist {}", shareName);
            throw APIException.notFound.invalidParameterObjectHasNoSuchShare(fs.getId(), shareName);
        }

        FileController controller = getController(FileController.class, device.getSystemType());

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                taskId, ResourceOperationTypeEnum.DELETE_FILE_SYSTEM_SHARE_ACL);
        op.setDescription("Delete ACL of Cifs share");

        controller.deleteShareACLs(device.getId(), fs.getId(), shareName, taskId);

        auditOp(OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE_ACL, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getId().toString(), device.getId().toString(), shareName);

        return toTask(fs, taskId, op);
    }

    /**
     * GET all ACLs for a fileSystem
     * 
     * @param id
     *            the URN of a ViPR fileSystem
     * @param allDirs
     *            all directory within a fileSystem
     * @param subDir
     *            sub-directory within a fileSystem
     * @return list of ACLs for file system.
     * @throws InternalException
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/acl")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public NfsACLs getFileShareACLs(@PathParam("id") URI id,
            @QueryParam("allDirs") boolean allDirs,
            @QueryParam("subDir") String subDir) {

        _log.info("Request recieved for Acl  with Id : {}  allDirs : {} subDir : {}",
                new Object[] { id, allDirs, subDir });

        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        // Check for VirtualPool whether it has NFS v4 enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.NFSv4.name())) {
            // Throw an error
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool does not support "
                    + StorageProtocol.File.NFSv4.name() + " protocol");
        }

        // Get All ACLs of FS from data base and group them based on path!!
        NfsACLUtility util = new NfsACLUtility(_dbClient, fs, null, subDir);
        NfsACLs acls = util.getNfsAclFromDB(allDirs);

        if (acls.getNfsACLs() != null && !acls.getNfsACLs().isEmpty()) {
            _log.info("Found {} Acl rules for filesystem {}", acls.getNfsACLs().size(), fs.getId());
        } else {
            _log.info("No Acl rules found for filesystem  {}", fs.getId());
        }
        return acls;
    }

    /**
     * 
     * Update existing file system ACL
     * 
     * @param id
     *            the URN of a ViPR fileSystem
     * @param param
     *            FileNfsACLUpdateParams
     * @brief Update file system ACL
     * @return Task resource representation
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/acl")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateFileSystemAcls(@PathParam("id") URI id,
            FileNfsACLUpdateParams param) throws InternalException {
        // log input received.
        _log.info("Update FS ACL : request received for {}  with {}", id, param);
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        // Check for VirtualPool whether it has NFS v4 enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.NFSv4.name())) {
            // Throw an error
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool does not support "
                    + StorageProtocol.File.NFSv4.name() + " protocol");
        }

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());
        String task = UUID.randomUUID().toString();
        String path = fs.getPath();
        _log.info("fileSystem  path {} ", path);
        Operation op = new Operation();
        try {
            _log.info("Sub Dir Provided {}", param.getSubDir());
            // Validate the input
            NfsACLUtility util = new NfsACLUtility(_dbClient, fs, null, param.getSubDir());

            util.verifyNfsACLs(param);

            _log.info("No Errors found proceeding further {}, {}, {}", new Object[] { _dbClient, fs, param });
            op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                    task, ResourceOperationTypeEnum.UPDATE_FILE_SYSTEM_NFS_ACL);
            op.setDescription("Filesystem NFS ACL update");

            controller.updateNFSAcl(device.getId(), fs.getId(), param, task);

            auditOp(OperationTypeEnum.UPDATE_FILE_SYSTEM_NFS_ACL, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), param);

        } catch (BadRequestException e) {
            op = _dbClient.error(FileShare.class, fs.getId(), task, e);
            _log.error("Error Processing File System ACL Updates {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Processing File System ACL Updates  {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }

        return toTask(fs, task, op);
    }

    /**
     * Delete all the existing ACLs of a fileSystem or subDirectory
     * 
     * @param id
     *            the URN of a ViPR fileSystem
     * @param subDir
     *            sub-directory within a fileSystem
     * @return Task resource representation
     */
    @DELETE
    @Path("/{id}/acl")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep deleteFileSystemACL(@PathParam("id") URI id,
            @QueryParam("subDir") String subDir) {

        // log input received.
        _log.info("Delete ACL of fileSystem: Request received for  filesystem: {} with subDir {} ",
                id, subDir);

        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        String task = UUID.randomUUID().toString();
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        // Check for VirtualPool whether it has NFS v4 enabled
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        if (!vpool.getProtocols().contains(StorageProtocol.File.NFSv4.name())) {
            // Throw an error
            throw APIException.methodNotAllowed.vPoolDoesntSupportProtocol("Vpool does not support "
                    + StorageProtocol.File.NFSv4.name() + " protocol");
        }
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.DELETE_FILE_SYSTEM_NFS_ACL);
        op.setDescription("Delete ACL of file system ");

        try {
            controller.deleteNFSAcls(device.getId(), fs.getId(), subDir, task);

            auditOp(OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE_ACL, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), subDir);

        } catch (BadRequestException e) {
            op = _dbClient.error(FileShare.class, fs.getId(), task, e);
            _log.error("Error Processing File System ACL Delete {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Processing File System ACL Delete  {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }

        return toTask(fs, task, op);
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/vpool-change")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep changeFileSystemVirtualPool(@PathParam("id") URI id, FileSystemVirtualPoolChangeParam param)
            throws InternalException, APIException {

        _log.info("Request to change VirtualPool for filesystem {}", id);

        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        FileShare orgFs = queryResource(id);
        String task = UUID.randomUUID().toString();
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));
        TaskList taskList = new TaskList();

        // Make sure that we don't have some pending
        // operation against the file system!!!
        checkForPendingTasks(Arrays.asList(fs.getTenant().getURI()), Arrays.asList(fs));

        // target vPool
        VirtualPool newVpool = null;

        // Get the project.
        URI projectURI = fs.getProject().getURI();
        Project project = _permissionsHelper.getObjectById(projectURI,
                Project.class);
        ArgValidator.checkEntity(project, projectURI, false);
        _log.info("Found filesystem project {}", projectURI);

        // Verify the user is authorized for the volume's project.
        // BlockServiceUtils.verifyUserIsAuthorizedForRequest(project, getUserFromContext(), _permissionsHelper);
        _log.info("User is authorized for volume's project");

        // Get the VirtualPool for the request and verify that the
        // project's tenant has access to the VirtualPool.
        newVpool = getVirtualPoolForRequest(project, param.getVirtualPool(),
                _dbClient, _permissionsHelper);
        _log.info("Found new VirtualPool {}", newVpool.getId());

        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        StringBuffer notSuppReasonBuff = new StringBuffer();
        // Verify the vPool change is supported!!!
        if (!VirtualPoolChangeAnalyzer.isSupportedFileReplicationChange(currentVpool, newVpool, notSuppReasonBuff)) {
            _log.error("Virtual Pool change is not supported due to {}", notSuppReasonBuff.toString());
            throw APIException.badRequests.invalidVirtualPoolForVirtualPoolChange(
                    newVpool.getLabel(), notSuppReasonBuff.toString());
        }

        // Get the virtual array!!!
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, fs.getVirtualArray());

        // Total provisioned capacity to check for vPool quota.
        long totalProvisionedCapacity = fs.getCapacity();
        // verify target vPool quota
        if (!CapacityUtils.validateVirtualPoolQuota(_dbClient, newVpool,
                totalProvisionedCapacity)) {
            throw APIException.badRequests.insufficientQuotaForVirtualPool(
                    newVpool.getLabel(), "filesystem");
        }
        // Change the virtual pool of source file system!!
        fs.setVirtualPool(newVpool.getId());
        // New operation
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CHANGE_FILE_SYSTEM_VPOOL);
        op.setDescription("Change vpool operation");
        op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(), task, op);

        // Update the new pool to DB!!
        _dbClient.updateObject(fs);

        TaskResourceRep fileSystemTask = toTask(fs, task, op);
        taskList.getTaskList().add(fileSystemTask);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());

        // prepare vpool capability values
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, fs.getCapacity());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(newVpool.getSupportedProvisioningType())) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        }
        // Set the source file system details
        // source fs details used in finding recommendations for target fs!!
        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_SYSTEM_CREATE_MIRROR_COPY, Boolean.TRUE);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.EXISTING_SOURCE_FILE_SYSTEM, fs);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SOURCE_STORAGE_SYSTEM, device);

        FileServiceApi fileServiceApi = getFileServiceImpl(newVpool, _dbClient);

        try {
            // Call out placementManager to get the recommendation for placement.
            List recommendations = _filePlacementManager.getRecommendationsForFileCreateRequest(
                    varray, project, newVpool, capabilities);

            // Verify the source virtual pool recommendations meets source fs storage!!!
            fileServiceApi.createTargetsForExistingSource(fs, project,
                    newVpool, varray, taskList, task, recommendations, capabilities);
        } catch (InternalException e) {
            if (_log.isErrorEnabled()) {
                _log.error("Delete error", e);
            }

            FileShare fileShare = _dbClient.queryObject(FileShare.class, fs.getId());
            op = fs.getOpStatus().get(task);
            op.error(e);
            fileShare.getOpStatus().updateTaskStatus(task, op);
            // Revert the file system to original state!!!
            restoreFromOriginalFs(orgFs, fs);
            _dbClient.updateObject(fs);
            throw e;
        }
        auditOp(OperationTypeEnum.CHANGE_FILE_SYSTEM_VPOOL, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getLabel(), currentVpool.getLabel(), newVpool.getLabel(),
                project == null ? null : project.getId().toString());

        return taskList.getTaskList().get(0);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/create")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep createContinuousCopies(@PathParam("id") URI id, FileReplicationCreateParam param)
            throws InternalException, APIException {

        _log.info("Request to create replication copies for filesystem {}", id);

        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        FileShare orgFs = queryResource(id);
        String task = UUID.randomUUID().toString();
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));
        TaskList taskList = new TaskList();

        // Make sure that we don't have some pending
        // operation against the file system!!!
        checkForPendingTasks(Arrays.asList(fs.getTenant().getURI()), Arrays.asList(fs));

        // Get the project.
        URI projectURI = fs.getProject().getURI();
        Project project = _permissionsHelper.getObjectById(projectURI,
                Project.class);
        ArgValidator.checkEntity(project, projectURI, false);
        _log.info("Found filesystem project {}", projectURI);

        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        StringBuffer notSuppReasonBuff = new StringBuffer();

        // Verify the file system and its vPool are capable of doing replication!!!
        if (!isSupportedFileReplicationCreate(fs, currentVpool, notSuppReasonBuff)) {
            _log.error("create mirror copies is not supported for file system {} due to {}",
                    fs.getId().toString(), notSuppReasonBuff.toString());
            throw APIException.badRequests.unableToCreateMirrorCopies(
                    fs.getId(), notSuppReasonBuff.toString());
        }

        // Get the virtual array!!!
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, fs.getVirtualArray());

        // New operation
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_FILE_SYSTEM_MIRROR_COPIES);
        op.setDescription("Create file system mirror operation");
        op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(), task, op);

        TaskResourceRep fileSystemTask = toTask(fs, task, op);
        taskList.getTaskList().add(fileSystemTask);
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());

        // prepare vpool capability values
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, fs.getCapacity());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(currentVpool.getSupportedProvisioningType())) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        }
        // Set the source file system details
        // source fs details used in finding recommendations for target fs!!
        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_SYSTEM_CREATE_MIRROR_COPY, Boolean.TRUE);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.EXISTING_SOURCE_FILE_SYSTEM, fs);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SOURCE_STORAGE_SYSTEM, device);

        FileServiceApi fileServiceApi = getFileShareServiceImpl(fs, _dbClient);

        try {
            // Call out placementManager to get the recommendation for placement.
            List recommendations = _filePlacementManager.getRecommendationsForFileCreateRequest(
                    varray, project, currentVpool, capabilities);

            // Verify the source virtual pool recommendations meets source fs storage!!!
            fileServiceApi.createTargetsForExistingSource(fs, project,
                    currentVpool, varray, taskList, task, recommendations, capabilities);
        } catch (InternalException e) {
            if (_log.isErrorEnabled()) {
                _log.error("createContinuousCopies error ", e);
            }

            FileShare fileShare = _dbClient.queryObject(FileShare.class, fs.getId());
            op = fs.getOpStatus().get(task);
            op.error(e);
            fileShare.getOpStatus().updateTaskStatus(task, op);
            // Revert the file system to original state!!!
            restoreFromOriginalFs(orgFs, fs);
            _dbClient.updateObject(fs);
            throw e;
        }
        auditOp(OperationTypeEnum.CREATE_MIRROR_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getLabel(), currentVpool.getLabel(), fs.getLabel(),
                project == null ? null : project.getId().toString());

        return taskList.getTaskList().get(0);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep deactivateContinuousCopies(@PathParam("id") URI id, FileSystemDeleteParam param)
            throws InternalException, APIException {

        _log.info("Request to deactivate replication copies for filesystem {}", id);

        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        FileShare orgFs = queryResource(id);
        String task = UUID.randomUUID().toString();
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));
        TaskList taskList = new TaskList();

        // Make sure that we don't have some pending
        // operation against the file system!!!
        checkForPendingTasks(Arrays.asList(fs.getTenant().getURI()), Arrays.asList(fs));

        // Get the project.
        URI projectURI = fs.getProject().getURI();
        Project project = _permissionsHelper.getObjectById(projectURI,
                Project.class);
        ArgValidator.checkEntity(project, projectURI, false);
        _log.info("Found filesystem project {}", projectURI);

        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        StringBuffer notSuppReasonBuff = new StringBuffer();

        // Verify the file system and its vPool are capable of doing replication!!!
        if (!validateDeleteMirrorCopies(fs, currentVpool, notSuppReasonBuff)) {
            _log.error("delete mirror copies is not supported for file system {} due to {}",
                    fs.getId().toString(), notSuppReasonBuff.toString());
            throw APIException.badRequests.unableToDeleteMirrorCopies(
                    fs.getId(), notSuppReasonBuff.toString());
        }

        // Get the virtual array!!!
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, fs.getVirtualArray());

        // New operation
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.DELETE_MIRROR_FILE_SYSTEMS);
        op.setDescription("Deactivate file system mirror operation");
        op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(), task, op);

        TaskResourceRep fileSystemTask = toTask(fs, task, op);
        taskList.getTaskList().add(fileSystemTask);
        List<URI> fileShareURIs = new ArrayList<URI>();
        fileShareURIs.add(id);
        boolean deleteMirrorCopies = true;

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileServiceApi fileServiceApi = getFileShareServiceImpl(fs, _dbClient);

        try {
            fileServiceApi.deleteFileSystems(device.getId(), fileShareURIs,
                    param.getDeleteType(), param.getForceDelete(), deleteMirrorCopies, task);
        } catch (InternalException e) {
            if (_log.isErrorEnabled()) {
                _log.error("deactivate continuous copies error ", e);
            }

            FileShare fileShare = _dbClient.queryObject(FileShare.class, fs.getId());
            op = fs.getOpStatus().get(task);
            op.error(e);
            fileShare.getOpStatus().updateTaskStatus(task, op);
            // Revert the file system to original state!!!
            restoreFromOriginalFs(orgFs, fs);
            _dbClient.updateObject(fs);
            throw e;
        }
        auditOp(OperationTypeEnum.DELETE_MIRROR_FILE_SYSTEM, true, AuditLogManager.AUDITOP_BEGIN,
                fs.getLabel(), currentVpool.getLabel(), fs.getLabel(),
                project == null ? null : project.getId().toString());

        return taskList.getTaskList().get(0);
    }

    /**
     * 
     * Start continuous copies.
     * 
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR Source fileshare
     * @param param
     *            List of copies to start
     * 
     * @brief Start continuous copies.
     * @return TaskList
     * 
     * @throws ControllerException
     * 
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/start")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList startContinuousCopies(@PathParam("id") URI id, FileReplicationParam param)
            throws ControllerException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");

        return performFileProtectionAction(param, id, ProtectionOp.START.getRestOp());
    }

    /**
     * 
     * Refresh continuous copies.
     * 
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR Source fileshare
     * @param param
     *            List of copies to refresh
     * 
     * @brief Refresh continuous copies.
     * @return TaskList
     * 
     * @throws ControllerException
     * 
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/refresh")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList refreshContinuousCopies(@PathParam("id") URI id, FileReplicationParam param)
            throws ControllerException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        return performFileProtectionAction(param, id, ProtectionOp.REFRESH.getRestOp());
    }

    /**
     * Stop continuous copies.
     * 
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR Source fileshare
     * @param param
     * 
     * @brief Stop continuous copies.
     * @return TaskList
     * 
     * @throws ControllerException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/stop")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList stopContinuousCopies(@PathParam("id") URI id, FileReplicationParam param)
            throws ControllerException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        return performFileProtectionAction(param, id, ProtectionOp.STOP.getRestOp());
    }

    /**
     * Pause continuous copies for given source fileshare
     * 
     * NOTE: This is an asynchronous operation.
     * 
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR Source fileshare
     * @param param
     * 
     * @brief Pause continuous copies
     * @return TaskList
     * 
     * @throws ControllerException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/pause")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList pauseContinuousCopies(@PathParam("id") URI id, FileReplicationParam param) throws ControllerException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        return performFileProtectionAction(param, id, ProtectionOp.PAUSE.getRestOp());
    }

    /**
     * Resume continuous copies for given source fileshare
     * 
     * NOTE: This is an asynchronous operation.
     * 
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR Source fileshare
     * @param param
     * 
     * @brief Resume continuous copies
     * @return TaskList
     * 
     * @throws ControllerException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/resume")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList resumeContinuousCopies(@PathParam("id") URI id, FileReplicationParam param)
            throws ControllerException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        return performFileProtectionAction(param, id, ProtectionOp.RESUME.getRestOp());
    }

    /**
     * 
     * Request to failover the protection link associated with the param.copyID.
     * 
     * NOTE: This is an asynchronous operation.
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR Source fileshare
     * @param param
     *            FileReplicationParam to failover to
     * 
     * @brief Failover the fileShare protection link
     * @return TaskList
     * 
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/failover")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList failoverProtection(@PathParam("id") URI id, FileReplicationParam param) throws ControllerException {
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        Copy copy = param.getCopies().get(0);
        if (copy.getType().equalsIgnoreCase(FileTechnologyType.REMOTE_MIRROR.name())) {
            return performFileProtectionAction(param, id, ProtectionOp.FAILOVER.getRestOp());
        } else {
            throw APIException.badRequests.invalidCopyType(copy.getType());
        }

    }

    /**
     * 
     * Request to fail Back the protection link associated with the param.copyID.
     * 
     * NOTE: This is an asynchronous operation.
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR Source files hare
     * @param param
     *            FileReplicationParam to fail Back to
     * 
     * @brief Fail Back the fileShare protection link
     * @return TaskList
     * 
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/failback")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList failbackProtection(@PathParam("id") URI id, FileReplicationParam param) throws ControllerException {

        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        Copy copy = param.getCopies().get(0);
        if (copy.getType().equalsIgnoreCase(FileTechnologyType.REMOTE_MIRROR.name())) {
            return performFileProtectionAction(param, id, ProtectionOp.FAILBACK.getRestOp());
        } else {
            throw APIException.badRequests.invalidCopyType(copy.getType());
        }
    }

    /**
     * List FileShare mirrors
     * 
     * 
     * @prereq none
     * 
     * @param id
     *            the URN of a ViPR FileShare to list mirrors
     * 
     * @brief List fileShare mirrors
     * @return FileShare mirror response containing a list of mirror identifiers
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public MirrorList getNativeContinuousCopies(@PathParam("id") URI id) {
        MirrorList list = new MirrorList();
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, id);

        StringSet sourceFileShareMirrors = sourceFileShare.getMirrorfsTargets();

        if (sourceFileShareMirrors == null || sourceFileShareMirrors.isEmpty()) {
            return list;
        }

        for (String uriStr : sourceFileShareMirrors) {

            FileShare fileMirror = _dbClient.queryObject(FileShare.class, URI.create(uriStr));

            if (fileMirror == null || fileMirror.getInactive()) {
                _log.warn("Stale mirror {} found for fileShare {}", uriStr, sourceFileShare.getId());
                continue;
            }
            list.getMirrorList().add(toNamedRelatedResource(fileMirror));
        }

        return list;
    }

    private TaskResourceRep performProtectionAction(URI id, String op) throws InternalException {
        String task = UUID.randomUUID().toString();

        FileShare sourceFileShare = _dbClient.queryObject(FileShare.class, id);
        ArgValidator.checkEntity(sourceFileShare, id, true);

        // Make sure that we don't have some pending
        // operation against the file share
        checkForPendingTasks(Arrays.asList(sourceFileShare.getTenant().getURI()), Arrays.asList(sourceFileShare));

        // Get the project.
        URI projectURI = sourceFileShare.getProject().getURI();
        Project project = _permissionsHelper.getObjectById(projectURI,
                Project.class);
        ArgValidator.checkEntity(project, projectURI, false);
        _log.info("Found filesystem project {}", projectURI);

        VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, sourceFileShare.getVirtualPool());
        StringBuffer notSuppReasonBuff = new StringBuffer();

        // Verify the file system and its vPool are capable of doing replication!!!
        if (!validateMirrorOperationSupported(sourceFileShare, currentVpool, notSuppReasonBuff, op)) {
            _log.error("Mirror Operation {} is not supported for the file system {} as : {}", op.toUpperCase(),
                    sourceFileShare.getLabel(), notSuppReasonBuff.toString());
            throw APIException.badRequests.unableToPerformMirrorOperation(op.toUpperCase(), sourceFileShare.getId(),
                    notSuppReasonBuff.toString());

        }
        Operation status = new Operation();
        status.setResourceType(ProtectionOp.getResourceOperationTypeEnum(op));
        _dbClient.createTaskOpStatus(FileShare.class, sourceFileShare.getId(), task, status);

        // get storage system list
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());

        FileReplicationController controller = getController(FileReplicationController.class, system.getSystemType());

        // call controller service
        controller.performRemoteContinuousCopies(system.getId(), id, op, task);

        return toTask(sourceFileShare, task, status);
    }

    /**
     * perform file protection action
     * 
     * @param param
     * @param id
     * @param op
     * @return
     */
    private TaskList performFileProtectionAction(FileReplicationParam param, URI id, String op) {
        TaskResourceRep taskResp = null;
        TaskList taskList = new TaskList();
        Copy copy = param.getCopies().get(0);
        if (copy.getType().equalsIgnoreCase(FileTechnologyType.REMOTE_MIRROR.name()) ||
                copy.getType().equalsIgnoreCase(FileTechnologyType.LOCAL_MIRROR.name())) {
            taskResp = performProtectionAction(id, op);
            taskList.getTaskList().add(taskResp);
            return taskList;
        } else {
            throw APIException.badRequests.invalidCopyType(copy.getType());
        }

    }

    /**
     * copy exports rules
     * 
     * @param orig
     * @param dest
     * @param fs
     */
    private void copyPropertiesToSave(FileExportRule orig, ExportRule dest, FileShare fs) {

        dest.setFsID(fs.getId());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        dest.setReadWriteHosts(orig.getReadWriteHosts());
        dest.setRootHosts(orig.getRootHosts());
        dest.setMountPoint(orig.getMountPoint());
        // Test
        _log.info("Expor Rule : {} - {}", orig, dest);
    }

    private List<FileExportRule> queryDBFSExports(FileShare fs) {
        _log.info("Querying all ExportRules Using FsId {}", fs.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
            List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileExportRule.class,
                    containmentConstraint);
            return fileExportRules;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return null;
    }

    private List<ExportRule> queryFSExports(FileShare fs) {
        List<ExportRule> rules = null;
        _log.info("Querying all ExportRules Using FsId {}", fs.getId());
        try {
            List<FileExportRule> fileExportRules = queryDBFSExports(fs);

            rules = new ArrayList<>();

            for (FileExportRule fileExportRule : fileExportRules) {
                ExportRule rule = new ExportRule();
                getExportRule(fileExportRule, rule);
                rules.add(rule);
            }
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return rules;
    }

    private void getExportRule(FileExportRule orig, ExportRule dest) {

        dest.setFsID(orig.getFileSystemId());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        dest.setReadWriteHosts(orig.getReadWriteHosts());
        dest.setRootHosts(orig.getRootHosts());
        dest.setMountPoint(orig.getMountPoint());
    }

    /**
     * Returns the bean responsible for servicing the request
     * 
     * @param fileShare fileshare
     * @param dbClient db client
     * @return file service implementation object
     */
    public static FileServiceApi getFileShareServiceImpl(FileShare fileShare, DbClient dbClient) {
        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, fileShare.getVirtualPool());
        return getFileServiceImpl(vPool, dbClient);
    }

    /**
     * Returns the bean responsible for servicing the request
     * 
     * @param vpool
     *            Virtual Pool
     * @param dbClient
     *            db client
     * @return file service implementation object
     */
    private static FileServiceApi getFileServiceImpl(VirtualPool vpool, DbClient dbClient) {
        // Mutually exclusive logic that selects an implementation of the file service

        if (VirtualPool.vPoolSpecifiesFileReplication(vpool)) {
            if (vpool.getFileReplicationType().equals(VirtualPool.FileReplicationType.LOCAL.name())) {
                return getFileServiceApis("localmirror");
            } else if (vpool.getFileReplicationType().equals(VirtualPool.FileReplicationType.REMOTE.name())) {
                return getFileServiceApis("remotemirror");
            }
        }

        return getFileServiceApis("default");
    }

    /**
     * 
     * Assign existing file system to file policy.
     * 
     * @param id
     *            the URN of a ViPR fileSystem
     * @param filePolicyUri
     *            the URN of a Policy
     * @brief Update file system with Policy detail
     * @return Task resource representation
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/assign-file-policy/{filePolicyUri}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep assignFilePolicy(@PathParam("id") URI id,
            @PathParam("filePolicyUri") URI filePolicyUri) throws InternalException {

        // log input received.
        _log.info("Assigning file policy {} to file system {}", filePolicyUri, id);
        String task = UUID.randomUUID().toString();
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        ArgValidator.checkFieldUriType(filePolicyUri, SchedulePolicy.class, "filePolicyUri");
        ArgValidator.checkUri(filePolicyUri);
        SchedulePolicy fp = _permissionsHelper.getObjectById(filePolicyUri, SchedulePolicy.class);
        ArgValidator.checkEntityNotNull(fp, filePolicyUri, isIdEmbeddedInURL(filePolicyUri));
        // verify the file system tenant is same as policy tenant
        if (!fp.getTenantOrg().getURI().toString().equalsIgnoreCase(fs.getTenant().getURI().toString())) {
            throw APIException.badRequests.associatedPolicyTenantMismatch(filePolicyUri, id);
        }
        // Check for VirtualPool support snapshot or not
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());

        if (!vpool.getScheduleSnapshots()) {
            // Throw an error
            throw APIException.methodNotAllowed.notSupportedWithReason("Snapshot schedule is not supported by vpool: " + vpool.getLabel());
        }

        if (fs.getFilePolicies().contains(filePolicyUri.toString())) {
            throw APIException.badRequests.duplicatePolicyAssociation(filePolicyUri);
        }
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        String path = fs.getMountPath();
        _log.info("Mount path found {} ", path);

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.ASSIGN_FILE_SYSTEM_SNAPSHOT_SCHEDULE);
        op.setDescription("Filesystem assign policy");

        try {

            _log.info("No Errors found proceeding further {}, {}, {}", new Object[] { _dbClient, fs, fp });

            controller.assignFileSystemSnapshotPolicy(device.getId(), fs.getId(), fp.getId(), task);

            auditOp(OperationTypeEnum.ASSIGN_FILE_SYSTEM_SNAPSHOT_SCHEDULE, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), fp.getId());

        } catch (BadRequestException e) {
            op = _dbClient.error(FileShare.class, fs.getId(), task, e);
            _log.error("Error Assigning Filesystem policy {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Assigning Filesystem policy {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }

        return toTask(fs, task, op);

    }

    /**
     * 
     * Unassign existing file system to file policy.
     * 
     * @param id
     *            the URN of a ViPR fileSystem
     * @param filePolicyUri
     *            the URN of a Policy
     * @brief Update file system with Policy detail
     * @return Task resource representation
     * @throws InternalException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/unassign-file-policy/{filePolicyUri}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep unAssignFilePolicy(@PathParam("id") URI id,
            @PathParam("filePolicyUri") URI filePolicyUri) throws InternalException {

        // log input received.
        _log.info("Unassign Policy on File System : request received for {}  with {}", id, filePolicyUri);
        String task = UUID.randomUUID().toString();
        // Validate the FS id.
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));

        ArgValidator.checkFieldUriType(filePolicyUri, SchedulePolicy.class, "filePolicyUri");
        ArgValidator.checkUri(filePolicyUri);
        SchedulePolicy fp = _permissionsHelper.getObjectById(filePolicyUri, SchedulePolicy.class);
        ArgValidator.checkEntityNotNull(fp, filePolicyUri, isIdEmbeddedInURL(filePolicyUri));
        // verify the file system tenant is same as policy tenant
        if (!fp.getTenantOrg().getURI().toString().equalsIgnoreCase(fs.getTenant().getURI().toString())) {
            throw APIException.badRequests.associatedPolicyTenantMismatch(filePolicyUri, id);
        }
        // verify the schedule policy is associated with file system or not.
        if (!fs.getFilePolicies().contains(filePolicyUri.toString())) {
            throw APIException.badRequests.cannotFindAssociatedPolicy(filePolicyUri);
        }

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());

        String path = fs.getMountPath();
        _log.info("Mount path found {} ", path);

        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.UNASSIGN_FILE_SYSTEM_SNAPSHOT_SCHEDULE);
        op.setDescription("Filesystem unassign policy");

        try {

            _log.info("No Errors found proceeding further {}, {}, {}", new Object[] { _dbClient, fs, fp });

            controller.unassignFileSystemSnapshotPolicy(device.getId(), fs.getId(), fp.getId(), task);

            auditOp(OperationTypeEnum.ASSIGN_FILE_SYSTEM_SNAPSHOT_SCHEDULE, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), fp.getId());

        } catch (BadRequestException e) {
            op = _dbClient.error(FileShare.class, fs.getId(), task, e);
            _log.error("Error Unassigning Filesystem policy {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Unassigning Filesystem policy {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }

        return toTask(fs, task, op);

    }

    /**
     * Get Policy for file system
     * 
     * @param id
     *            the URN of a ViPR File system
     * @brief Show file system
     * @return File system Policy details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/file-policies")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public FilePolicyList getFileSystemPolicy(@PathParam("id") URI id) {
        FilePolicyList fpList = new FilePolicyList();
        List<FilePolicyRestRep> fpRestList = new ArrayList<FilePolicyRestRep>();
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);

        StringSet fpolicies = fs.getFilePolicies();
        for (String fpolicy : fpolicies) {
            FilePolicyRestRep fpRest = new FilePolicyRestRep();
            URI fpURI = URI.create(fpolicy);
            if (fpURI != null) {
                SchedulePolicy fp = _permissionsHelper.getObjectById(fpURI, SchedulePolicy.class);
                if (fp != null) {
                    ArgValidator.checkEntityNotNull(fp, fpURI, isIdEmbeddedInURL(fpURI));
                    getFilePolicyRestRep(fpRest, fp, fs);
                }
            }
            fpRestList.add(fpRest);

        }
        fpList.setFilePolicies(fpRestList);
        return fpList;
    }

    /**
     * Get file system Snapshot created by policy
     * 
     * @param id
     *            The URN of a ViPR file system
     * @param filePolicyUri
     *            The URN of a file policy schedule
     * @return List snapshot created by a file policy
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/file-policies/{filePolicyUri}/snapshots")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ScheduleSnapshotList getFileSystemSchedulePolicySnapshots(@PathParam("id") URI id,
            @PathParam("filePolicyUri") URI filePolicyUri, @QueryParam("timeout") int timeout) {
        // valid value of timout is 10 sec to 10 min
        if (timeout < 10 || timeout > 600) {
            timeout = 30;// default timeout value.
        }

        ScheduleSnapshotList list = new ScheduleSnapshotList();
        ArgValidator.checkFieldUriType(id, FileShare.class, "id");
        FileShare fs = queryResource(id);
        ArgValidator.checkEntity(fs, id, isIdEmbeddedInURL(id));
        ArgValidator.checkFieldUriType(filePolicyUri, SchedulePolicy.class, "filePolicyUri");
        ArgValidator.checkUri(filePolicyUri);
        SchedulePolicy fp = _permissionsHelper.getObjectById(filePolicyUri, SchedulePolicy.class);
        ArgValidator.checkEntityNotNull(fp, filePolicyUri, isIdEmbeddedInURL(filePolicyUri));

        // verify the file system tenant is same as policy tenant
        if (!fp.getTenantOrg().getURI().toString().equalsIgnoreCase(fs.getTenant().getURI().toString())) {
            throw APIException.badRequests.associatedPolicyTenantMismatch(filePolicyUri, id);
        }
        // verify the schedule policy is associated with file system or not.
        if (!fs.getFilePolicies().contains(filePolicyUri.toString())) {
            throw APIException.badRequests.cannotFindAssociatedPolicy(filePolicyUri);
        }

        String task = UUID.randomUUID().toString();
        StorageSystem device = _dbClient.queryObject(StorageSystem.class, fs.getStorageDevice());
        FileController controller = getController(FileController.class, device.getSystemType());
        Operation op = _dbClient.createTaskOpStatus(FileShare.class, fs.getId(),
                task, ResourceOperationTypeEnum.GET_FILE_SYSTEM_SNAPSHOT_BY_SCHEDULE);
        op.setDescription("list snapshot created by a policy");

        try {

            _log.info("No Errors found proceeding further {}, {}, {}", new Object[] { _dbClient, fs, fp });

            controller.listSanpshotByPolicy(device.getId(), fs.getId(), fp.getId(), task);
            Task taskObject = null;
            auditOp(OperationTypeEnum.GET_FILE_SYSTEM_SNAPSHOT_BY_SCHEDULE, true, AuditLogManager.AUDITOP_BEGIN,
                    fs.getId().toString(), device.getId().toString(), fp.getId());
            int timeoutCounter = 0;
            // wait till timout or result from controler service ,whichever is erlier
            do {
                TimeUnit.SECONDS.sleep(1);
                taskObject = TaskUtils.findTaskForRequestId(_dbClient, fs.getId(), task);
                timeoutCounter++;
            } while ((taskObject != null && !taskObject.isReady()) && timeoutCounter < timeout);

            if (taskObject.isReady()) {
                URIQueryResultList snapshotsURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(id),
                        snapshotsURIs);
                List<Snapshot> snapList = _dbClient.queryObject(Snapshot.class, snapshotsURIs);

                for (Snapshot snap : snapList) {

                    if (!snap.getInactive() && snap.getExtensions().containsKey("schedule")) {
                        ScheduleSnapshotRestRep snapRest = new ScheduleSnapshotRestRep();
                        getScheduleSnapshotRestRep(snapRest, snap);
                        list.getScheduleSnapList().add(snapRest);
                        snap.setInactive(true);
                        _dbClient.updateObject(snap);
                    }
                }

            } else {
                throw APIException.badRequests
                        .unableToProcessRequest("Time out occurred, please increased timeout value, default is 30 sec");

            }

        } catch (BadRequestException e) {
            op = _dbClient.error(FileShare.class, fs.getId(), task, e);
            _log.error("Error getting  Filesystem policy  Snapshots {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error getting  Filesystem policy  Snapshots {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }
        return list;

    }

    private void getScheduleSnapshotRestRep(ScheduleSnapshotRestRep target, Snapshot source) {

        if (source.getExtensions().containsKey("created")) {
            target.setCreated(source.getExtensions().get("created"));

        }
        if (source.getExtensions().containsKey("expires")) {
            target.setExpires(source.getExtensions().get("expires"));

        }
        target.setId(source.getId());
        target.setMountPath(source.getMountPath());
        target.setName(source.getName());
    }

    /**
     * Create FilePolicyRestRep object from the SchedulePolicy object
     * 
     * @param fpRest
     *            FilePolicyRestRep object
     * @param fp
     *            SchedulePolicy object
     * @param fs
     *            FileShare object
     */
    private void getFilePolicyRestRep(FilePolicyRestRep fpRest, SchedulePolicy fp, FileShare fs) {
        String snapshotScheduleName = fp.getPolicyName() + "_" + fs.getName();
        String pattern = snapshotScheduleName + "_YYYY-MM-DD_HH-MM";
        fpRest.setPolicyId(fp.getId());
        fpRest.setPolicyName(fp.getPolicyName());
        fpRest.setScheduleDayOfMonth(fp.getScheduleDayOfMonth());
        fpRest.setScheduleDayOfWeek(fp.getScheduleDayOfWeek());
        fpRest.setScheduleFrequency(fp.getScheduleFrequency());
        fpRest.setScheduleRepeat(fp.getScheduleRepeat());
        fpRest.setScheduleTime(fp.getScheduleTime());
        fpRest.setSnapshotExpireTime(fp.getSnapshotExpireTime());
        fpRest.setSnapshotExpireType(fp.getSnapshotExpireType());
        fpRest.setSnapshotPattern(pattern);

    }

    /**
     * Gets and verifies the VirtualPool passed in the request.
     * 
     * @param project
     *            A reference to the project.
     * @param cosURI
     *            The URI of the VirtualPool.
     * @param dbClient
     *            Reference to a database client.
     * @param permissionsHelper
     *            Reference to a permissions helper.
     * 
     * @return A reference to the VirtualPool.
     */
    public static VirtualPool getVirtualPoolForRequest(Project project, URI cosURI, DbClient dbClient,
            PermissionsHelper permissionsHelper) {
        ArgValidator.checkUri(cosURI);
        VirtualPool cos = dbClient.queryObject(VirtualPool.class, cosURI);
        ArgValidator.checkEntity(cos, cosURI, false);
        if (!VirtualPool.Type.file.name().equals(cos.getType())) {
            throw APIException.badRequests.virtualPoolNotForFileBlockStorage(VirtualPool.Type.file.name());
        }

        permissionsHelper.checkTenantHasAccessToVirtualPool(project.getTenantOrg().getURI(), cos);
        return cos;
    }

    private void restoreFromOriginalFs(FileShare orgFs, FileShare fs) {
        // Vpool
        fs.setVirtualPool(orgFs.getVirtualPool());
        // Replication file attributes!!
        fs.setAccessState(orgFs.getAccessState());
        fs.setMirrorfsTargets(orgFs.getMirrorfsTargets());
        fs.setParentFileShare(orgFs.getParentFileShare());
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param currentVpool
     *            the source virtual pool
     * @param newVpool
     *            the target virtual pool
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * @return
     */
    private boolean isSupportedFileReplicationCreate(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff) {
        _log.info(String.format("Checking isSupportedFileReplicationCreate for Fs [%s] with vpool [%s]...", fs.getLabel(),
                currentVpool.getLabel()));

        if (!doBasicMirrorValidation(fs, currentVpool, notSuppReasonBuff)) {
            return false;
        }
        // File system should not be the active source file system!!
        if (fs.getPersonality() != null
                && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.name())
                && !MirrorStatus.DETACHED.name().equalsIgnoreCase(fs.getMirrorStatus())) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request is an active source file system %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }

        // File system should not have any active mirror copies!!
        if (fs.getMirrorfsTargets() != null
                && !fs.getMirrorfsTargets().isEmpty()) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request has active target file system %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }
        return true;
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param currentVpool
     *            the source virtual pool
     * @param newVpool
     *            the target virtual pool
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * @return
     */
    private boolean validateDeleteMirrorCopies(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff) {
        _log.info(String.format("Checking validateDeleteMirrorCopies for Fs [%s] ", fs.getLabel()));

        if (!doBasicMirrorValidation(fs, currentVpool, notSuppReasonBuff)) {
            return false;
        }
        // File system should not be the failover state
        // Failover state, the mirror copy would be in production!!!
        if (fs.getPersonality() != null
                && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.name())
                && (MirrorStatus.FAILED_OVER.name().equalsIgnoreCase(fs.getMirrorStatus())
                || MirrorStatus.SUSPENDED.name().equalsIgnoreCase(fs.getMirrorStatus()))) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request is in active or failover state %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }

        // File system should not have any active mirror copies!!
        if (fs.getMirrorfsTargets() == null
                || fs.getMirrorfsTargets().isEmpty()) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request has no active target file system %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }
        return true;
    }

    /**
     * Checks to see if the file replication operation is supported.
     * 
     * @param fs file share object
     * @param currentVpool source virtual pool
     * @param notSuppReasonBuff the not supported reason string buffer
     * @param operation mirror operation to be checked
     */
    private boolean validateMirrorOperationSupported(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff,
            String operation) {

        _log.info("Checking if mirror operation {} is supported for file system {} ", operation, fs.getLabel());

        if (!doBasicMirrorValidation(fs, currentVpool, notSuppReasonBuff)) {
            return false;
        }
        String currentMirrorStatus = fs.getMirrorStatus();
        boolean isSupported = false;

        switch (operation) {

        // Refresh operation can be performed without any check.
            case "refresh":
                isSupported = true;
                break;

            // START operation can be performed only if Mirror status is UNKNOWN or DETACHED
            case "start":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.UNKNOWN.toString())
                        || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.DETACHED.toString()))
                    isSupported = true;
                break;

            // STOP operation can be performed only if Mirror status is SYNCHRONIZED or IN_SYNC
            case "stop":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.SYNCHRONIZED.toString())
                        || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.IN_SYNC.toString()))
                    isSupported = true;
                break;

            // PAUSE operation can be performed only if Mirror status is SYNCHRONIZED or IN_SYNC
            case "pause":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.SYNCHRONIZED.toString())
                        || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.IN_SYNC.toString()))
                    isSupported = true;
                break;

            // RESUME operation can be performed only if Mirror status is SUSPENDED.
            case "resume":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.SUSPENDED.toString()))
                    isSupported = true;
                break;

            // Fail over can be performed if Mirror status is NOT UNKNOWN or FAILED_OVER.
            case "failover":
                if (!(currentMirrorStatus.equalsIgnoreCase(MirrorStatus.UNKNOWN.toString())
                || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.FAILED_OVER.toString())))
                    isSupported = true;
                break;

            // Fail back can be performed only if Mirror status is FAILED_OVER.
            case "failback":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.FAILED_OVER.toString()))
                    isSupported = true;
                break;
        }
        notSuppReasonBuff.append(String.format(" : file system %s is in %s state", fs.getLabel(), currentMirrorStatus.toUpperCase()));
        return isSupported;
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param fs
     * @param currentVpool
     *            the source virtual pool
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * @return
     */
    private boolean doBasicMirrorValidation(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff) {

        // file system virtual pool must be enabled with replication..
        if (!VirtualPool.vPoolSpecifiesFileReplication(currentVpool)) {
            notSuppReasonBuff.append(String.format("File replication is not enabled in virtual pool - %s"
                    + " of the requested file system -%s ", currentVpool.getLabel(), fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }

        // File system should not be the target file system..
        if (fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            notSuppReasonBuff.append(String.format("File system - %s given in request is an active Target file system.",
                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }
        return true;
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param fs
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * @return
     */
    private boolean filesystemHasActiveReplication(FileShare fs, StringBuffer notSuppReasonBuff,
            String deleteType, boolean forceDelete) {

        // File system should not be the target file system..
        if (fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            notSuppReasonBuff.append(String.format("File system - %s given in request is an active Target file system.",
                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return true;
        }

        // File system should not have active replication targets!!
        // For resource delete (forceDelete=false)
        // For VIPR_ONLY type, till we support ingestion of replication file systems
        // avoid deleting file systems if it has active mirrors!!
        if (forceDelete == false || FileControllerConstants.DeleteTypeEnum.VIPR_ONLY.toString().equalsIgnoreCase(deleteType)) {
            if (fs.getMirrorfsTargets() != null
                    && !fs.getMirrorfsTargets().isEmpty()) {
                notSuppReasonBuff
                        .append(String
                                .format("File system %s given in request has active target file systems.",
                                        fs.getLabel()));
                _log.info(notSuppReasonBuff.toString());
                return true;

            }
        }

        return false;
    }
}
