/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.FilePolicyMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.FilePolicyMapper;
import com.emc.storageos.api.mapper.functions.MapFilePolicy;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.FileMirrorRecommendation;
import com.emc.storageos.api.service.impl.placement.FileMirrorRecommendation.Target;
import com.emc.storageos.api.service.impl.placement.FilePlacementManager;
import com.emc.storageos.api.service.impl.placement.FileRecommendation;
import com.emc.storageos.api.service.impl.resource.utils.FilePolicyServiceUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyPriority;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.FileReplicationType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.FileReplicationTopology;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationUtils;
import com.emc.storageos.fileorchestrationcontroller.FileStorageSystemAssociation;
import com.emc.storageos.fileorchestrationcontroller.FileStorageSystemAssociation.TargetAssociation;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.file.policy.FilePolicyAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyBulkRep;
import com.emc.storageos.model.file.policy.FilePolicyCreateParam;
import com.emc.storageos.model.file.policy.FilePolicyCreateResp;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyStorageResourceRestRep;
import com.emc.storageos.model.file.policy.FilePolicyStorageResources;
import com.emc.storageos.model.file.policy.FilePolicyUnAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.storageos.model.file.policy.FileReplicationPolicyParam;
import com.emc.storageos.model.file.policy.FileReplicationTopologyParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * @author jainm15
 */
@Path("/file/file-policies")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class FilePolicyService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "FilePolicyService";

    private static final String EVENT_SERVICE_TYPE = "FilePolicy";

    // File service implementations
    static volatile private Map<String, FileServiceApi> _fileServiceApis;

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    private FilePlacementManager _filePlacementManager;

    public void setFilePlacementManager(FilePlacementManager placementManager) {
        _filePlacementManager = placementManager;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    public static void setFileServiceApis(Map<String, FileServiceApi> _fileServiceApis) {
        FilePolicyService._fileServiceApis = _fileServiceApis;
    }

    public static FileServiceApi getDefaultFileServiceApi() {
        return _fileServiceApis.get("default");
    }

    @Override
    protected FilePolicy queryResource(URI id) {
        ArgValidator.checkUri(id);
        FilePolicy filePolicy = _permissionsHelper.getObjectById(id, FilePolicy.class);
        ArgValidator.checkEntityNotNull(filePolicy, id, isIdEmbeddedInURL(id));
        return filePolicy;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.FILE_POLICY;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<FilePolicy> getResourceClass() {
        return FilePolicy.class;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public FilePolicyBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<FilePolicy> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.FilePolicyResourceFilter(getUserFromContext(), _permissionsHelper);
        return new FilePolicyBulkRep(BulkList.wrapping(_dbIterator,
                MapFilePolicy.getInstance(_dbClient), filter));
    }

    @Override
    public FilePolicyBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        return queryBulkResourceReps(ids);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param
     *            POST data containing the id list.
     * @brief List of file policies of given ids.
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public FilePolicyBulkRep getBulkResources(BulkIdParam param) {
        return (FilePolicyBulkRep) super.getBulkResources(param);
    }

    /**
     * @brief Create file policy for file snapshot, file replication or file quota
     * 
     * @param param
     *            FilePolicyCreateParam
     * @return FilePolicyCreateResp
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public FilePolicyCreateResp createFilePolicy(FilePolicyCreateParam param) {
        FilePolicyCreateResp resp = new FilePolicyCreateResp();
        // Make policy name as mandatory field
        ArgValidator.checkFieldNotNull(param.getPolicyName(), "policyName");

        // Make apply at as mandatory field
        ArgValidator.checkFieldNotNull(param.getApplyAt(), "apply_at");

        // Check for duplicate policy name
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }

        // check policy type is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getPolicyType(), "policy_type",
                EnumSet.allOf(FilePolicyType.class));

        // check the policy apply level is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getApplyAt(), "apply_at",
                EnumSet.allOf(FilePolicyApplyLevel.class));

        _log.info("file policy creation started -- ");
        if (param.getPolicyType().equals(FilePolicyType.file_replication.name())) {
            return createFileReplicationPolicy(param);

        } else if (param.getPolicyType().equals(FilePolicyType.file_snapshot.name())) {
            return createFileSnapshotPolicy(param);
        }
        return resp;
    }

    /**
     * Gets the ids and self links of all file policies.
     * 
     * @brief List file policies
     * @return A list of file policy reference specifying the ids and self links.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyListRestRep getFilePolicies() {
        return getFilePoliciesForGivenUser();
    }

    private boolean userHasTenantAdminRoles() {
        StorageOSUser user = getUserFromContext();

        if (_permissionsHelper.userHasGivenRole(user,
                null, Role.TENANT_ADMIN)) {
            return true;
        }
        return false;
    }

    private boolean userHasSystemAdminRoles() {
        StorageOSUser user = getUserFromContext();

        if (_permissionsHelper.userHasGivenRole(user,
                null, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN)) {
            return true;
        }
        return false;
    }

    protected FilePolicyListRestRep getFilePoliciesForGivenUser() {

        FilePolicyListRestRep filePolicyList = new FilePolicyListRestRep();
        List<URI> ids = _dbClient.queryByType(FilePolicy.class, true);
        List<FilePolicy> filePolicies = _dbClient.queryObject(FilePolicy.class, ids);

        StorageOSUser user = getUserFromContext();
        // full list if role is {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR} AND no tenant restriction from input
        // else only return the list, which input tenant has access.
        if (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR)) {
            for (FilePolicy filePolicy : filePolicies) {
                filePolicyList.add(toNamedRelatedResource(filePolicy, filePolicy.getFilePolicyName()));
            }
        } else {
            // otherwise, filter by only authorized to use
            URI tenant = null;

            tenant = URI.create(user.getTenantId());

            Set<FilePolicy> policySet = new HashSet<FilePolicy>();
            for (FilePolicy filePolicy : filePolicies) {
                if (_permissionsHelper.tenantHasUsageACL(tenant, filePolicy)) {
                    policySet.add(filePolicy);
                }
            }

            // Also adding vpools which sub-tenants of the user have access to.
            List<URI> subtenants = _permissionsHelper.getSubtenantsWithRoles(user);
            for (FilePolicy filePolicy : filePolicies) {
                if (_permissionsHelper.tenantHasUsageACL(subtenants, filePolicy)) {
                    policySet.add(filePolicy);
                }
            }

            for (FilePolicy filePolicy : policySet) {
                filePolicyList.add(toNamedRelatedResource(filePolicy, filePolicy.getFilePolicyName()));
            }
        }

        return filePolicyList;

    }

    /**
     * @brief Get details of a file policy.
     * 
     * @param id
     *            of the file policy.
     * @return File policy information.
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyRestRep getFilePolicy(@PathParam("id") URI id) {

        _log.info("Request recieved to get the file policy of id: {}", id);
        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);
        return map(filepolicy, _dbClient);
    }

    /**
     * @brief Delete file policy.
     * @param id
     *            of the file policy.
     * @return
     */
    @DELETE
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteFilePolicy(@PathParam("id") URI id) {

        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkReference(FilePolicy.class, filepolicy.getFilePolicyName(), checkForDelete(filepolicy));

        StringSet assignedResources = filepolicy.getAssignedResources();

        if (assignedResources != null && !assignedResources.isEmpty()) {
            _log.error("Delete file pocicy failed because the policy has associacted resources");
            throw APIException.badRequests.failedToDeleteFilePolicy(filepolicy.getFilePolicyName(), "This policy has assigned resources.");
        }

        _dbClient.markForDeletion(filepolicy);

        auditOp(OperationTypeEnum.DELETE_FILE_POLICY, true, null, filepolicy.getId().toString(),
                filepolicy.getLabel());
        return Response.ok().build();
    }

    /**
     * Assign File Policy
     * 
     * @param id
     *            of the file policy.
     * @param param
     *            FilePolicyAssignParam
     * @brief Assign file policy to vpool, project, file system           
     * @return FilePolicyAssignResp
     */
    @POST
    @Path("/{id}/assign-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep assignFilePolicy(@PathParam("id") URI id, FilePolicyAssignParam param) {

        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filepolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filepolicy, id, true);

        // Verify user has permission to assign policy
        canUserAssignPolicyAtGivenLevel(filepolicy);

        String applyAt = filepolicy.getApplyAt();

        FilePolicyApplyLevel appliedAt = FilePolicyApplyLevel.valueOf(applyAt);
        switch (appliedAt) {
            case vpool:
                return assignFilePolicyToVpools(param, filepolicy);
            case project:
                return assignFilePolicyToProjects(param, filepolicy);
            default:
                throw APIException.badRequests.invalidFilePolicyApplyLevel(appliedAt.name());
        }
    }

    /**
     * Unassign File Policy
     * 
     * @param id
     *            of the file policy.
     * @param FilePolicyUnAssignParam
     * @brief Unassign file policy from vpool, project, file system
     * @return TaskResourceRep
     */
    @POST
    @Path("/{id}/unassign-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep unassignFilePolicy(@PathParam("id") URI id, FilePolicyUnAssignParam param) {

        _log.info("Unassign File Policy :{}  request received.", id);
        String task = UUID.randomUUID().toString();

        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filepolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filepolicy, id, true);
        StringBuilder errorMsg = new StringBuilder();

        Operation op = _dbClient.createTaskOpStatus(FilePolicy.class, filepolicy.getId(),
                task, ResourceOperationTypeEnum.UNASSIGN_FILE_POLICY);
        op.setDescription("unassign File Policy from resources ");

        // As the action done by tenant/system admin
        // Set corresponding tenant uri as task's tenant!!!
        Task taskObj = op.getTask(filepolicy.getId());
        StorageOSUser user = getUserFromContext();
        URI userTenantUri = URI.create(user.getTenantId());
        FilePolicyServiceUtils.updateTaskTenant(_dbClient, filepolicy, "unassign", taskObj, userTenantUri);

        if (filepolicy.getAssignedResources() == null || filepolicy.getAssignedResources().isEmpty()) {
            _log.info("File Policy: " + id + " doesn't have any assigned resources.");
            _dbClient.ready(FilePolicy.class, filepolicy.getId(), task);
            return toTask(filepolicy, task, op);
        }

        ArgValidator.checkFieldNotNull(param.getUnassignfrom(), "unassign_from");
        Set<URI> unassignFrom = param.getUnassignfrom();
        if (unassignFrom != null) {
            for (URI uri : unassignFrom) {
                canUserUnAssignPolicyAtGivenLevel(filepolicy, uri);
                if (!filepolicy.getAssignedResources().contains(uri.toString())) {
                    errorMsg.append("Provided resource URI is either being not assigned to the file policy:" + filepolicy.getId()
                            + " or it is a invalid URI");
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyUnAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            }
        }

        FileOrchestrationController controller = getController(FileOrchestrationController.class,
                FileOrchestrationController.FILE_ORCHESTRATION_DEVICE);
        try {
            controller.unassignFilePolicy(filepolicy.getId(), unassignFrom, task);
            auditOp(OperationTypeEnum.UNASSIGN_FILE_POLICY, true, "BEGIN", filepolicy.getId().toString(),
                    filepolicy.getLabel());

        } catch (BadRequestException e) {
            op = _dbClient.error(FilePolicy.class, filepolicy.getId(), task, e);
            _log.error("Error Unassigning File policy {}, {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            _log.error("Error Unassigning Files Policy {}, {}", e.getMessage(), e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }
        return toTask(filepolicy, task, op);
    }

    /**
     * Add or remove individual File Policy ACL entry(s). Request body must include at least one add or remove
     * operation.
     * 
     * @param id
     *            the URN of a ViPR File Policy
     * @param changes
     *            ACL assignment changes
     * @brief Add or remove ACL entries from file store VirtualPool
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {

        FilePolicy policy = queryResource(id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        _permissionsHelper.updateACLs(policy, changes,
                new PermissionsHelper.UsageACLFilter(_permissionsHelper));
        _dbClient.updateObject(policy);
        return getAclsOnPolicy(id);
    }

    /**
     * Get File Policy ACLs
     * 
     * @param id
     *            the URI of a ViPR FilePolicy
     * @brief Show ACL entries for file policy
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnPolicy(id);
    }

    protected ACLAssignments getAclsOnPolicy(URI id) {
        FilePolicy policy = queryResource(id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(policy.getAcls()));
        return response;
    }

    /**
     * @brief Update the file policy
     * 
     * @param id
     *            the URI of a ViPR FilePolicy
     * @param param
     *            FilePolicyUpdateParam
     * @return FilePolicyCreateResp
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateFilePolicy(@PathParam("id") URI id, FilePolicyUpdateParam param) {
        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filePolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filePolicy, id, true);

        _log.info("validate and update file policy parameters started -- ");
        if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_replication.name())) {
            updateFileReplicationPolicy(filePolicy, param);
        } else if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_snapshot.name())) {
            updateFileSnapshotPolicy(filePolicy, param);
        }
        // if No storage resource, update the original policy template!!
        _dbClient.updateObject(filePolicy);

        String task = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(FilePolicy.class, filePolicy.getId(),
                task, ResourceOperationTypeEnum.UPDATE_FILE_POLICY_BY_POLICY_STORAGE_RESOURCE);
        op.setDescription("update file protection policy by policy storage resource");

        // As the action done by system admin
        // Set system uri as task's tenant!!!
        Task taskObj = op.getTask(filePolicy.getId());
        StorageOSUser user = getUserFromContext();
        URI userTenantUri = URI.create(user.getTenantId());
        FilePolicyServiceUtils.updateTaskTenant(_dbClient, filePolicy, "update", taskObj, userTenantUri);

        if (filePolicy.getPolicyStorageResources() != null && !filePolicy.getPolicyStorageResources().isEmpty()) {
            _log.info("Updating the storage system policy started..");
            updateStorageSystemFileProtectionPolicy(filePolicy, param, task);
            return toTask(filePolicy, task, op);
        } else {
            op = _dbClient.ready(FilePolicy.class, filePolicy.getId(), task);
            return toTask(filePolicy, task, op);
        }
    }

    /**
     * @brief Get the list of policy storage resources of a file policy.
     * 
     * @param id of the file policy.
     * @return List of policy storage resource information.
     */
    @GET
    @Path("/{id}/policy-storage-resources")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public FilePolicyStorageResources getFilePolicyStorageResources(@PathParam("id") URI id) {

        _log.info("Request recieved to list storage resources for the policy {}", id);
        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);
        FilePolicyStorageResources resources = new FilePolicyStorageResources();

        List<FilePolicyStorageResourceRestRep> policyResources = new ArrayList<FilePolicyStorageResourceRestRep>();
        for (PolicyStorageResource storageRes : FileOrchestrationUtils.getFilePolicyStorageResources(_dbClient, filepolicy)) {
            policyResources.add(FilePolicyMapper.mapPolicyStorageResource(storageRes, filepolicy, _dbClient));
        }
        resources.setStorageResources(policyResources);
        return resources;
    }

    /**
     * Validate and create replication policy.
     * 
     * @param param
     * @return
     */
    private FilePolicyCreateResp createFileReplicationPolicy(FilePolicyCreateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        FilePolicy fileReplicationPolicy = new FilePolicy();

        if (param.getReplicationPolicyParams() == null) {
            errorMsg.append("Required parameter replication_params was missing or empty");
            _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFileReplicationPolicyParam(param.getPolicyName(), errorMsg.toString());
        }

        // Make sure replication type and copy mode are provided
        ArgValidator.checkFieldNotNull(param.getReplicationPolicyParams().getReplicationCopyMode(), "replication_copy_mode");
        ArgValidator.checkFieldNotNull(param.getReplicationPolicyParams().getReplicationType(), "replication_type");

        // Validate replication policy schedule parameters
        boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicyScheduleParam(
                param.getReplicationPolicyParams().getPolicySchedule(), fileReplicationPolicy, errorMsg);
        if (!isValidSchedule && errorMsg.length() > 0) {
            _log.error("Failed to create file replication policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
        }
        // validate replication type and copy mode parameters
        ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationType(), "replicationType",
                EnumSet.allOf(FilePolicy.FileReplicationType.class));

        ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationCopyMode(), "replicationCopyMode",
                EnumSet.allOf(FilePolicy.FileReplicationCopyMode.class));

        fileReplicationPolicy.setId(URIUtil.createId(FilePolicy.class));
        fileReplicationPolicy.setFilePolicyName(param.getPolicyName());
        fileReplicationPolicy.setLabel(param.getPolicyName());
        fileReplicationPolicy.setFilePolicyType(param.getPolicyType());
        if (param.getPriority() != null) {
            ArgValidator.checkFieldValueFromEnum(param.getPriority(), "priority",
                    EnumSet.allOf(FilePolicyPriority.class));
            fileReplicationPolicy.setPriority(param.getPriority());
        }
        fileReplicationPolicy.setNumWorkerThreads((long) param.getNumWorkerThreads());
        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            fileReplicationPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }
        fileReplicationPolicy.setScheduleFrequency(param.getReplicationPolicyParams().getPolicySchedule().getScheduleFrequency());
        fileReplicationPolicy.setFileReplicationType(param.getReplicationPolicyParams().getReplicationType());
        fileReplicationPolicy.setFileReplicationCopyMode(param.getReplicationPolicyParams().getReplicationCopyMode());
        fileReplicationPolicy.setApplyAt(param.getApplyAt());
        _dbClient.createObject(fileReplicationPolicy);
        _log.info("Policy {} created successfully", fileReplicationPolicy);

        return new FilePolicyCreateResp(fileReplicationPolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                fileReplicationPolicy.getId()), fileReplicationPolicy.getLabel());
    }

    /**
     * Validate and create snapshot policy.
     * 
     * @param param
     * @return
     */
    private FilePolicyCreateResp createFileSnapshotPolicy(FilePolicyCreateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        FilePolicy fileSnapshotPolicy = new FilePolicy();

        if (param.getSnapshotPolicyPrams() == null) {
            errorMsg.append("Required parameter snapshot_params was missing or empty");
            _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFileSnapshotPolicyParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot policy schedule parameters
        boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicyScheduleParam(
                param.getSnapshotPolicyPrams().getPolicySchedule(), fileSnapshotPolicy, errorMsg);
        if (!isValidSchedule && errorMsg.length() > 0) {
            _log.error("Failed to create file snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot policy expire parameters..
        FilePolicyServiceUtils.validateSnapshotPolicyExpireParam(param.getSnapshotPolicyPrams());

        fileSnapshotPolicy.setId(URIUtil.createId(FilePolicy.class));
        fileSnapshotPolicy.setLabel(param.getPolicyName());
        fileSnapshotPolicy.setFilePolicyType(param.getPolicyType());
        fileSnapshotPolicy.setFilePolicyName(param.getPolicyName());
        fileSnapshotPolicy.setLabel(param.getPolicyName());
        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            fileSnapshotPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }
        fileSnapshotPolicy.setScheduleFrequency(param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency());
        fileSnapshotPolicy.setSnapshotExpireType(param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType());
        fileSnapshotPolicy.setSnapshotNamePattern(param.getSnapshotPolicyPrams().getSnapshotNamePattern());
        fileSnapshotPolicy.setApplyAt(param.getApplyAt());
        if (!param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType()
                .equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
            fileSnapshotPolicy.setSnapshotExpireTime((long) param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireValue());
        } else {
            fileSnapshotPolicy.setSnapshotExpireTime(0L);
        }
        _dbClient.createObject(fileSnapshotPolicy);
        _log.info("Snapshot policy {} created successfully", fileSnapshotPolicy);

        return new FilePolicyCreateResp(fileSnapshotPolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                fileSnapshotPolicy.getId()), fileSnapshotPolicy.getLabel());
    }

    /**
     * Update the replication policy.
     * 
     * @param fileReplicationPolicy
     * @param param
     * @return
     */
    private void updateFileReplicationPolicy(FilePolicy fileReplicationPolicy, FilePolicyUpdateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        // validate and update common parameters!!
        updatePolicyCommonParameters(fileReplicationPolicy, param);

        if (param.getPriority() != null) {
            ArgValidator.checkFieldValueFromEnum(param.getPriority(), "priority",
                    EnumSet.allOf(FilePolicyPriority.class));
            fileReplicationPolicy.setPriority(param.getPriority());
        }

        if (param.getNumWorkerThreads() > 0) {
            fileReplicationPolicy.setNumWorkerThreads((long) param.getNumWorkerThreads());
        }

        // validate and update replication parameters!!!
        if (param.getReplicationPolicyParams() != null) {
            FileReplicationPolicyParam replParam = param.getReplicationPolicyParams();

            if (replParam.getReplicationCopyMode() != null && !replParam.getReplicationCopyMode().isEmpty()) {
                ArgValidator.checkFieldValueFromEnum(replParam.getReplicationCopyMode(), "replicationCopyMode",
                        EnumSet.allOf(FilePolicy.FileReplicationCopyMode.class));
                fileReplicationPolicy.setFileReplicationCopyMode(replParam.getReplicationCopyMode());
            }

            if (replParam.getReplicationType() != null && !replParam.getReplicationType().isEmpty()) {
                ArgValidator.checkFieldValueFromEnum(replParam.getReplicationType(), "replicationType",
                        EnumSet.allOf(FilePolicy.FileReplicationType.class));
                // Dont change the replication type, if policy was applied to storage resources!!
                if (!replParam.getReplicationType().equalsIgnoreCase(fileReplicationPolicy.getFileReplicationType())) {
                    if (fileReplicationPolicy.getPolicyStorageResources() != null
                            && !fileReplicationPolicy.getPolicyStorageResources().isEmpty()) {
                        errorMsg.append("Active resources exist on the policy");
                        _log.error("Failed to update file replication policy due to {} ", errorMsg.toString());
                        throw APIException.badRequests.invalidFileReplicationPolicyParam(fileReplicationPolicy.getFilePolicyName(),
                                errorMsg.toString());
                    } else {
                        fileReplicationPolicy.setFileReplicationType(replParam.getReplicationType());
                    }
                }
            }

            // Validate replication policy schedule parameters
            if (replParam.getPolicySchedule() != null) {
                boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicyScheduleParam(
                        replParam.getPolicySchedule(), fileReplicationPolicy, errorMsg);
                if (!isValidSchedule && errorMsg.length() > 0) {
                    _log.error("Failed to update file replication policy due to {} ", errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyScheduleParam(fileReplicationPolicy.getFilePolicyName(),
                            errorMsg.toString());
                }
                if (replParam.getPolicySchedule().getScheduleFrequency() != null &&
                        !replParam.getPolicySchedule().getScheduleFrequency().isEmpty()) {
                    fileReplicationPolicy
                            .setScheduleFrequency(replParam.getPolicySchedule().getScheduleFrequency());
                }
            }
        }
    }

    /**
     * Update snapshot policy.
     * 
     * @param param
     * @param fileSnapshotPolicy
     * @return
     */
    private void updateFileSnapshotPolicy(FilePolicy fileSnapshotPolicy, FilePolicyUpdateParam param) {
        StringBuilder errorMsg = new StringBuilder();
        // validate and update common parameters!!
        updatePolicyCommonParameters(fileSnapshotPolicy, param);

        // Validate snapshot policy expire parameters..
        if (param.getSnapshotPolicyPrams() != null) {

            // Validate snapshot policy schedule parameters
            if (param.getSnapshotPolicyPrams().getPolicySchedule() != null) {
                boolean isValidSchedule = FilePolicyServiceUtils.validateAndUpdatePolicyScheduleParam(
                        param.getSnapshotPolicyPrams().getPolicySchedule(), fileSnapshotPolicy, errorMsg);
                if (!isValidSchedule) {
                    _log.error("Failed to update file snapshot policy due to {} ", errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyScheduleParam(fileSnapshotPolicy.getFilePolicyName(),
                            errorMsg.toString());
                }
                if (param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency() != null &&
                        !param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency().isEmpty()) {
                    fileSnapshotPolicy.setScheduleFrequency(param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency());
                }
            }

            if (param.getSnapshotPolicyPrams().getSnapshotExpireParams() != null) {
                // Validate the snapshot expire parameters!!
                FileSnapshotPolicyExpireParam snapExpireParam = param.getSnapshotPolicyPrams().getSnapshotExpireParams();
                FilePolicyServiceUtils.validateSnapshotPolicyExpireParam(param.getSnapshotPolicyPrams());
                if (snapExpireParam.getExpireType() != null) {
                    fileSnapshotPolicy.setSnapshotExpireType(snapExpireParam.getExpireType());
                    if (!SnapshotExpireType.NEVER.toString().equalsIgnoreCase(snapExpireParam.getExpireType())) {
                        fileSnapshotPolicy.setSnapshotExpireTime((long) snapExpireParam.getExpireValue());
                    } else {
                        fileSnapshotPolicy.setSnapshotExpireTime(0L);
                    }
                }
            }

            if (param.getSnapshotPolicyPrams().getSnapshotNamePattern() != null) {
                fileSnapshotPolicy.setSnapshotNamePattern(param.getSnapshotPolicyPrams().getSnapshotNamePattern());
            }
        }
    }

    private void updateStorageSystemFileProtectionPolicy(FilePolicy policy, FilePolicyUpdateParam param, String task) {

        try {
            FileServiceApi fileServiceApi = getDefaultFileServiceApi();
            fileServiceApi.updateFileProtectionPolicy(policy.getId(), param, task);
            _log.info("Updated file protection policy {}", policy.getFilePolicyName());
        } catch (BadRequestException e) {
            Operation op = _dbClient.error(FilePolicy.class, policy.getId(), task, e);
            _log.error("Error updating file policy on backend storage.", e);
            throw e;
        } catch (Exception e) {
            _log.error("Error updating file policy on backend storage.", e);
            throw APIException.badRequests.unableToProcessRequest(e.getMessage());
        }
    }

    private boolean updatePolicyCommonParameters(FilePolicy existingPolicy, FilePolicyUpdateParam param) {

        // Verify and updated the policy name!!!
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()
                && !existingPolicy.getLabel().equalsIgnoreCase(param.getPolicyName())) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
            existingPolicy.setLabel(param.getPolicyName());
            existingPolicy.setFilePolicyName(param.getPolicyName());
            existingPolicy.setLabel(param.getPolicyName());
        }

        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            existingPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }

        if (param.getApplyAt() != null && !param.getApplyAt().isEmpty()
                && !param.getApplyAt().equalsIgnoreCase(existingPolicy.getApplyAt())) {
            if (existingPolicy.getAssignedResources() != null && !existingPolicy.getAssignedResources().isEmpty()) {
                String errorMsg = "Policy has active resources, can not change applied at to " + param.getApplyAt();
                _log.error(errorMsg);
                throw APIException.badRequests.unableToProcessRequest(errorMsg);
            }
            existingPolicy.setApplyAt(param.getApplyAt());
        }

        return true;
    }

    private List<FileReplicationTopology> queryDBReplicationTopologies(FilePolicy policy) {
        _log.info("Querying all DB replication topologies Using policy Id {}", policy.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory
                    .getFileReplicationPolicyTopologyConstraint(policy.getId());
            List<FileReplicationTopology> topologies = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    FileReplicationTopology.class,
                    containmentConstraint);
            return topologies;
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return null;
    }

    private void updateFileReplicationTopologyInfo(FilePolicyAssignParam param, FilePolicy filepolicy) {

        if (FilePolicyType.file_replication.name().equalsIgnoreCase(filepolicy.getFilePolicyType())
                && filepolicy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.REMOTE.name())) {
            if (param.getFileReplicationtopologies() != null && !param.getFileReplicationtopologies().isEmpty()) {
                List<FileReplicationTopology> dbTopologies = queryDBReplicationTopologies(filepolicy);
                for (FileReplicationTopologyParam topologyParam : param.getFileReplicationtopologies()) {
                    // Get existing topologies for given policy
                    Boolean foundExistingTopology = false;
                    if (dbTopologies != null && !dbTopologies.isEmpty()) {
                        for (FileReplicationTopology topology : dbTopologies) {
                            if (topology.getSourceVArray() != null
                                    && topology.getSourceVArray().toString().equalsIgnoreCase(topologyParam.getSourceVArray().toString())) {
                                _log.info("Updating the existing topology...");

                                if (topologyParam.getTargetVArrays() != null && !topologyParam.getTargetVArrays().isEmpty()) {
                                    StringSet requestTargetVarraySet = new StringSet();
                                    for (Iterator<URI> iterator = topologyParam.getTargetVArrays().iterator(); iterator.hasNext();) {
                                        URI targetVArray = iterator.next();
                                        requestTargetVarraySet.add(targetVArray.toString());
                                    }
                                    // Thow an error if admin want to change the topology for policy with resources!!
                                    if (filepolicy.getPolicyStorageResources() != null
                                            && !filepolicy.getPolicyStorageResources().isEmpty()) {
                                        if (topology.getTargetVArrays() != null
                                                && !topology.getTargetVArrays().containsAll(requestTargetVarraySet)) {
                                            StringBuffer errorMsg = new StringBuffer();
                                            errorMsg.append("Topology can not be changed for policy {} with existing resources "
                                                    + filepolicy.getFilePolicyName());
                                            _log.error(errorMsg.toString());
                                            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(),
                                                    errorMsg.toString());
                                        }

                                    }
                                    topology.setTargetVArrays(requestTargetVarraySet);
                                    _dbClient.updateObject(topology);
                                }

                                if (filepolicy.getReplicationTopologies() == null
                                        || !filepolicy.getReplicationTopologies().contains(topology.getId().toString())) {
                                    filepolicy.addReplicationTopology(topology.getId().toString());
                                    _dbClient.updateObject(filepolicy);
                                }
                                foundExistingTopology = true;
                                break;
                            }
                        }
                    }

                    if (!foundExistingTopology) {
                        // Create DB entry for Replication topology
                        FileReplicationTopology dbReplTopology = new FileReplicationTopology();
                        dbReplTopology.setId(URIUtil.createId(FileReplicationTopology.class));
                        dbReplTopology.setPolicy(filepolicy.getId());
                        dbReplTopology.setSourceVArray(topologyParam.getSourceVArray());
                        StringSet targetArrays = new StringSet();
                        if (topologyParam.getTargetVArrays() != null && !topologyParam.getTargetVArrays().isEmpty()) {
                            for (URI uriTargetArray : topologyParam.getTargetVArrays()) {
                                targetArrays.add(uriTargetArray.toString());
                            }
                            dbReplTopology.setTargetVArrays(targetArrays);
                        }
                        _dbClient.createObject(dbReplTopology);
                        if (filepolicy.getReplicationTopologies() == null
                                || !filepolicy.getReplicationTopologies().contains(dbReplTopology.getId().toString())) {
                            filepolicy.addReplicationTopology(dbReplTopology.getId().toString());
                            _dbClient.updateObject(filepolicy);
                        }
                    }
                }
            }
        }
    }

    /**
     * Assigning policy at vpool level
     * 
     * @param param
     * @param filepolicy
     */
    private TaskResourceRep assignFilePolicyToVpools(FilePolicyAssignParam param, FilePolicy filePolicy) {
        StringBuilder errorMsg = new StringBuilder();
        StringBuilder recommendationErrorMsg = new StringBuilder();

        ArgValidator.checkFieldNotNull(param.getVpoolAssignParams(), "vpool_assign_param");
        // Policy has to be applied on specified file vpools..
        ArgValidator.checkFieldNotEmpty(param.getVpoolAssignParams().getAssigntoVpools(), "assign_to_vpools");
        Set<URI> vpoolURIs = param.getVpoolAssignParams().getAssigntoVpools();
        Map<URI, List<URI>> vpoolToStorageSystemMap = new HashMap<URI, List<URI>>();

        List<URI> filteredVpoolURIs = new ArrayList<URI>();
        StringBuffer vPoolWithNoStoragePools = new StringBuffer();
        for (URI vpoolURI : vpoolURIs) {
            ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "vpool");
            VirtualPool virtualPool = _permissionsHelper.getObjectById(vpoolURI, VirtualPool.class);
            ArgValidator.checkEntity(virtualPool, vpoolURI, false);
            if (filePolicy.getAssignedResources() != null && filePolicy.getAssignedResources().contains(virtualPool.getId().toString())) {
                _log.info("File policy: {} has already been assigned to vpool: {} ", filePolicy.getFilePolicyName(),
                        virtualPool.getLabel());
                continue;
            }

            // Verify the vpool has any replication policy!!!
            // only single replication policy per vpool.
            if (filePolicy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_replication.name())
                    && FilePolicyServiceUtils.vPoolHasReplicationPolicy(_dbClient, vpoolURI)) {
                errorMsg.append("Provided vpool : " + virtualPool.getLabel() + " already assigned with replication policy.");
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }

            if (filePolicy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_snapshot.name())
                    && FilePolicyServiceUtils.vPoolHasSnapshotPolicyWithSameSchedule(_dbClient, vpoolURI, filePolicy)) {
                errorMsg.append("Snapshot policy with similar schedule is already present on vpool " + virtualPool.getLabel());
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }

            FilePolicyServiceUtils.validateVpoolSupportPolicyType(filePolicy, virtualPool);
            List<URI> storageSystems = getAssociatedStorageSystemsByVPool(virtualPool);
            if (storageSystems != null && !storageSystems.isEmpty()) {
                vpoolToStorageSystemMap.put(vpoolURI, storageSystems);
                filteredVpoolURIs.add(vpoolURI);
            } else {
                vPoolWithNoStoragePools.append(virtualPool.getLabel()).append(",");
            }
        }

        if (filteredVpoolURIs.isEmpty()) {
            String errorMessage = "No matching storage pools exists for given vpools ";
            _log.error(errorMessage);
            throw APIException.badRequests.noStoragePoolsExists(vPoolWithNoStoragePools.toString());
        }

        if (param.getApplyOnTargetSite() != null) {
            filePolicy.setApplyOnTargetSite(param.getApplyOnTargetSite());
        }

        FileServiceApi fileServiceApi = getDefaultFileServiceApi();

        FilePolicyType policyType = FilePolicyType.valueOf(filePolicy.getFilePolicyType());

        String task = UUID.randomUUID().toString();
        TaskResourceRep taskResponse = null;

        switch (policyType) {
            case file_snapshot:
                taskResponse = createAssignFilePolicyTask(filePolicy, task);
                AssignFileSnapshotPolicyToVpoolSchedulingThread.executeApiTask(this, _asyncTaskService.getExecutorService(), _dbClient,
                        filePolicy.getId(), vpoolToStorageSystemMap, fileServiceApi, taskResponse, task);
                break;
            case file_replication:
                // update replication topology info
                updateFileReplicationTopologyInfo(param, filePolicy);
                List<URI> validRecommendationVpools = new ArrayList<URI>();
                List<FileStorageSystemAssociation> associations = new ArrayList<FileStorageSystemAssociation>();
                for (URI vpoolURI : filteredVpoolURIs) {
                    VirtualPool vpool = _permissionsHelper.getObjectById(vpoolURI, VirtualPool.class);
                    StringSet sourceVArraysSet = getSourceVArraySet(vpool, filePolicy);

                    VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
                    updatePolicyCapabilities(_dbClient, sourceVArraysSet, vpool, filePolicy, capabilities, errorMsg);

                    // Replication policy has to be created on each applicable source storage system!!
                    List<URI> storageSystems = getAssociatedStorageSystemsByVPool(vpool);
                    if (storageSystems != null && !storageSystems.isEmpty()) {
                        List<FileStorageSystemAssociation> vpoolAssociations = new ArrayList<FileStorageSystemAssociation>();
                        for (URI storageSystem : storageSystems) {
                            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_PROTECTION_SOURCE_STORAGE_SYSTEM, storageSystem);

                            for (Iterator<String> iterator = sourceVArraysSet.iterator(); iterator.hasNext();) {
                                String vArrayURI = iterator.next();
                                VirtualArray srcVarray = _dbClient.queryObject(VirtualArray.class, URI.create(vArrayURI));
                                try {
                                    List<FileRecommendation> newRecs = _filePlacementManager.getRecommendationsForFileCreateRequest(
                                            srcVarray,
                                            null,
                                            vpool, capabilities);
                                    if (newRecs != null && !newRecs.isEmpty()) {
                                        vpoolAssociations.addAll(convertRecommendationsToStorageSystemAssociations(newRecs,
                                                filePolicy.getApplyAt(), vpool.getId(), null));
                                    }
                                } catch (Exception ex) {
                                    _log.error("No recommendations found for storage system {} and virtualArray {} with error {} ",
                                            storageSystem, srcVarray.getLabel(), ex.getMessage());
                                    if (ex.getMessage() != null) {
                                        recommendationErrorMsg.append(ex.getMessage());
                                    }
                                    // Continue to get the recommendations for next storage system!!
                                    continue;

                                }
                            }
                        }
                        if (!vpoolAssociations.isEmpty()) {
                            validRecommendationVpools.add(vpoolURI);
                            associations.addAll(vpoolAssociations);
                        }
                    } else {
                        String errorMessage = "No matching storage pools exists for vpool " + vpool.getLabel();
                        _log.error(errorMessage);
                        recommendationErrorMsg.append(errorMessage);
                    }
                }
                // If there is no recommendations found to assign replication policy
                // Throw an exception!!
                if (associations == null || associations.isEmpty()) {
                    // If no other resources are assigned to replication policy
                    // Remove the replication topology from the policy
                    FileOrchestrationUtils.removeTopologyInfo(filePolicy, _dbClient);
                    _log.error("No matching storage pools recommendations found for policy {} with due to {}",
                            filePolicy.getFilePolicyName(), recommendationErrorMsg.toString());
                    throw APIException.badRequests.noFileStorageRecommendationsFound(filePolicy.getFilePolicyName());

                }
                taskResponse = createAssignFilePolicyTask(filePolicy, task);
                fileServiceApi.assignFileReplicationPolicyToVirtualPools(associations, validRecommendationVpools, filePolicy.getId(),
                        task);
                break;
            default:
                break;
        }

        auditOp(OperationTypeEnum.ASSIGN_FILE_POLICY, true, AuditLogManager.AUDITOP_BEGIN,
                filePolicy.getLabel());

        if (taskResponse != null) {
            // As the action done by system admin
            // Set system uri as task's tenant!!!
            Task taskObj = _dbClient.queryObject(Task.class, taskResponse.getId());
            StorageOSUser user = getUserFromContext();
            URI userTenantUri = URI.create(user.getTenantId());
            FilePolicyServiceUtils.updateTaskTenant(_dbClient, filePolicy, "assign", taskObj, userTenantUri);
        }

        return taskResponse;

    }

    private static boolean updatePolicyCapabilities(DbClient dbClient, StringSet sourceVArraySet, VirtualPool vPool, FilePolicy policy,
            VirtualPoolCapabilityValuesWrapper capabilities, StringBuilder errorMsg) {

        // Update replication policy capabilities!!
        capabilities.put(VirtualPoolCapabilityValuesWrapper.VPOOL_PROJECT_POLICY_ASSIGN, true);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, policy.getFileReplicationType());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_COPY_MODE, policy.getFileReplicationCopyMode());
        if (vPool.getFrRpoType() != null) { // rpo type can be DAYS or HOURS
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_RPO_TYPE, vPool.getFrRpoType());
        }
        if (vPool.getFrRpoValue() != null) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_RPO_VALUE, vPool.getFrRpoValue());
        }
        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_APPLIED_AT, policy.getApplyAt());

        // Update target varrays for file placement!!
        if (policy.getFileReplicationType() != null
                && policy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.REMOTE.name())) {
            if (policy.getReplicationTopologies() != null && !policy.getReplicationTopologies().isEmpty()) {
                Set<String> targetVArrays = new HashSet<String>();
                for (String strTopology : policy.getReplicationTopologies()) {
                    FileReplicationTopology dbTopology = dbClient.queryObject(FileReplicationTopology.class,
                            URI.create(strTopology));
                    if (sourceVArraySet.contains(dbTopology.getSourceVArray().toString())) {
                        targetVArrays.addAll(dbTopology.getTargetVArrays());
                        break;
                    }
                }
                if (targetVArrays.isEmpty()) {
                    errorMsg.append("Target Varrays are not defined in replication topology for source varrays "
                            + sourceVArraySet + ". ");
                    return false;
                }
                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET_VARRAYS,
                        targetVArrays);

                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET_VPOOL,
                        vPool.getId());

            } else {
                errorMsg.append("Replication Topology is not defined for policy " + policy.getFilePolicyName() + ". ");
                return false;
            }
        }
        return true;
    }

    private List<FileStorageSystemAssociation> convertRecommendationsToStorageSystemAssociations(List<FileRecommendation> recs,
            String appliedAt, URI vPoolURI, URI projectURI) {

        List<FileStorageSystemAssociation> associations = new ArrayList<FileStorageSystemAssociation>();

        for (FileRecommendation rec : recs) {
            FileMirrorRecommendation mirrorRec = (FileMirrorRecommendation) rec;
            FileStorageSystemAssociation association = new FileStorageSystemAssociation();
            association.setSourceSystem(mirrorRec.getSourceStorageSystem());
            association.setSourceVNAS(mirrorRec.getvNAS());
            if (appliedAt.equalsIgnoreCase(FilePolicyApplyLevel.vpool.name())) {
                association.setAppliedAtResource(vPoolURI);
            } else if (appliedAt.equalsIgnoreCase(FilePolicyApplyLevel.project.name())) {
                association.setProjectvPool(vPoolURI);
                association.setAppliedAtResource(projectURI);
            }

            Map<URI, Target> virtualArrayTargetMap = mirrorRec.getVirtualArrayTargetMap();
            // Getting the first target because we support one-to-one replication now.
            URI targetVArray = virtualArrayTargetMap.entrySet().iterator().next().getKey();
            Target target = virtualArrayTargetMap.entrySet().iterator().next().getValue();
            URI targetStorageDevice = target.getTargetStorageDevice();
            URI targetVNasURI = target.getTargetvNASURI();

            TargetAssociation targetAssociation = new TargetAssociation();
            targetAssociation.setStorageSystemURI(targetStorageDevice);
            targetAssociation.setvArrayURI(targetVArray);
            targetAssociation.setvNASURI(targetVNasURI);
            association.addTargetAssociation(targetAssociation);

            associations.add(association);
        }
        return associations;
    }

    /**
     * Assign policy at project level
     * 
     * @param param
     * @param filepolicy
     */

    private TaskResourceRep assignFilePolicyToProjects(FilePolicyAssignParam param, FilePolicy filePolicy) {
        StringBuilder errorMsg = new StringBuilder();
        StringBuilder recommendationErrorMsg = new StringBuilder();
        ArgValidator.checkFieldNotNull(param.getProjectAssignParams(), "project_assign_param");
        ArgValidator.checkFieldUriType(param.getProjectAssignParams().getVpool(), VirtualPool.class, "vpool");
        URI vpoolURI = param.getProjectAssignParams().getVpool();
        VirtualPool vpool = null;

        if (NullColumnValueGetter.isNullURI(filePolicy.getFilePolicyVpool())) {
            ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "vpool");
            vpool = _permissionsHelper.getObjectById(vpoolURI, VirtualPool.class);
            ArgValidator.checkEntity(vpool, vpoolURI, false);

            // Check if the vpool supports provided policy type..
            FilePolicyServiceUtils.validateVpoolSupportPolicyType(filePolicy, vpool);

            // Check if the vpool supports policy at project level..
            if (!vpool.getAllowFilePolicyAtProjectLevel()) {
                errorMsg.append("Provided vpool :" + vpool.getLabel() + " doesn't support policy at project level");
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }

        } else if (vpoolURI != null) {
            vpool = _dbClient.queryObject(VirtualPool.class, filePolicy.getFilePolicyVpool());
            if (!vpoolURI.equals(filePolicy.getFilePolicyVpool())) {
                errorMsg.append("File policy: " + filePolicy.getFilePolicyName()
                        + " is already assigned at project level under the vpool: "
                        + vpool.getLabel());
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }
        }

        ArgValidator.checkFieldNotEmpty(param.getProjectAssignParams().getAssigntoProjects(), "assign_to_projects");
        Set<URI> projectURIs = param.getProjectAssignParams().getAssigntoProjects();
        List<URI> filteredProjectURIs = new ArrayList<URI>();
        for (URI projectURI : projectURIs) {
            ArgValidator.checkFieldUriType(projectURI, Project.class, "project");
            Project project = _permissionsHelper.getObjectById(projectURI, Project.class);
            ArgValidator.checkEntity(project, projectURI, false);

            if (filePolicy.getAssignedResources() != null && filePolicy.getAssignedResources().contains(project.getId().toString())) {
                _log.info("Policy {} is already assigned to project {} ", filePolicy.getFilePolicyName(), project.getLabel());
                continue;
            }

            // Verify the vpool - project has any replication policy!!!
            // only single replication policy per vpool-project combination.
            if (filePolicy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_replication.name())
                    && FilePolicyServiceUtils.projectHasReplicationPolicy(_dbClient, projectURI, vpool.getId())) {
                errorMsg.append("Virtual pool " + vpool.getLabel() + " project " + project.getLabel()
                        + "pair is already assigned with replication policy.");
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }

            if (filePolicy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_snapshot.name())
                    && FilePolicyServiceUtils.projectHasSnapshotPolicyWithSameSchedule(_dbClient, projectURI, vpool.getId(), filePolicy)) {
                errorMsg.append("Snapshot policy with similar schedule is already present on project " + project.getLabel());
                _log.error(errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(), errorMsg.toString());
            }
            filteredProjectURIs.add(projectURI);
        }

        if (param.getApplyOnTargetSite() != null) {
            filePolicy.setApplyOnTargetSite(param.getApplyOnTargetSite());
        }

        // Verify the virtual pool has storage pools!!!
        List<URI> storageSystems = getAssociatedStorageSystemsByVPool(vpool);
        if (storageSystems == null || storageSystems.isEmpty()) {
            String errorMessage = "No matching storage pools exists for given vpools ";
            _log.error(errorMessage);
            throw APIException.badRequests.noStoragePoolsExists(vpool.getLabel());
        }

        String task = UUID.randomUUID().toString();
        TaskResourceRep taskResponse = createAssignFilePolicyTask(filePolicy, task);
        FileServiceApi fileServiceApi = getDefaultFileServiceApi();

        FilePolicyType policyType = FilePolicyType.valueOf(filePolicy.getFilePolicyType());
        switch (policyType) {
            case file_snapshot:
                Map<URI, List<URI>> vpoolToStorageSystemMap = new HashMap<URI, List<URI>>();
                vpoolToStorageSystemMap.put(vpoolURI, getAssociatedStorageSystemsByVPool(vpool));
                AssignFileSnapshotPolicyToProjectSchedulingThread.executeApiTask(this, _asyncTaskService.getExecutorService(), _dbClient,
                        filePolicy.getId(), vpoolToStorageSystemMap, filteredProjectURIs, fileServiceApi, taskResponse, task);
                break;
            case file_replication:
                if (filteredProjectURIs.isEmpty()) {
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filePolicy.getFilePolicyName(),
                            "No projects to assign to policy.");
                }
                // update replication topology info
                updateFileReplicationTopologyInfo(param, filePolicy);
                List<URI> validRecommendationProjects = new ArrayList<URI>();
                List<FileStorageSystemAssociation> associations = new ArrayList<FileStorageSystemAssociation>();
                VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
                StringSet sourceVArraysSet = getSourceVArraySet(vpool, filePolicy);
                updatePolicyCapabilities(_dbClient, sourceVArraysSet, vpool, filePolicy, capabilities, errorMsg);

                // Replication policy has to be created on each applicable source storage system!!
                if (storageSystems != null && !storageSystems.isEmpty()) {
                    for (Iterator<String> iterator = sourceVArraysSet.iterator(); iterator.hasNext();) {
                        String vArrayURI = iterator.next();
                        for (URI projectURI : filteredProjectURIs) {
                            List<FileStorageSystemAssociation> projectAssociations = new ArrayList<FileStorageSystemAssociation>();
                            Project project = _dbClient.queryObject(Project.class, projectURI);
                            VirtualArray srcVarray = _dbClient.queryObject(VirtualArray.class, URI.create(vArrayURI));
                            for (URI storageSystem : storageSystems) {
                                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_PROTECTION_SOURCE_STORAGE_SYSTEM, storageSystem);
                                try {
                                    List<FileRecommendation> newRecs = _filePlacementManager.getRecommendationsForFileCreateRequest(
                                            srcVarray,
                                            project,
                                            vpool, capabilities);
                                    if (newRecs != null && !newRecs.isEmpty()) {
                                        projectAssociations
                                                .addAll(convertRecommendationsToStorageSystemAssociations(newRecs, filePolicy.getApplyAt(),
                                                        vpool.getId(), projectURI));
                                    }
                                } catch (Exception ex) {
                                    _log.error("No recommendations found for storage system {} and virtualArray {} with error {} ",
                                            storageSystem, srcVarray.getLabel(), ex.getMessage());
                                    if (ex.getMessage() != null) {
                                        recommendationErrorMsg.append(ex.getMessage());
                                    }
                                    // Continue to get the recommedations for next storage system!!
                                    continue;

                                }
                            }
                            if (!projectAssociations.isEmpty()) {
                                associations.addAll(projectAssociations);
                                validRecommendationProjects.add(projectURI);
                            }
                        }
                    }
                } else {
                    String errorMessage = "No matching storage pools exists for vpool " + vpool.getLabel();
                    _log.error(errorMessage);
                    recommendationErrorMsg.append(errorMessage);
                }

                // If there is no recommendations found to assign replication policy
                // Throw an exception!!
                if (associations == null || associations.isEmpty()) {
                    // If no other resources are assigned to replication policy
                    // Remove the replication topology from the policy
                    FileOrchestrationUtils.removeTopologyInfo(filePolicy, _dbClient);
                    _log.error("No matching storage pools recommendations found for policy {} with due to {}",
                            filePolicy.getFilePolicyName(), recommendationErrorMsg.toString());
                    throw APIException.badRequests.noFileStorageRecommendationsFound(filePolicy.getFilePolicyName());

                }

                fileServiceApi.assignFileReplicationPolicyToProjects(associations, vpoolURI,
                        validRecommendationProjects, filePolicy.getId(), task);
                break;
            default:
                break;
        }

        auditOp(OperationTypeEnum.ASSIGN_FILE_POLICY, true, AuditLogManager.AUDITOP_BEGIN,
                filePolicy.getLabel());

        if (taskResponse != null) {
            // As the action done by system admin
            // Set system uri as task's tenant!!!
            Task taskObj = _dbClient.queryObject(Task.class, taskResponse.getId());
            StorageOSUser user = getUserFromContext();
            URI userTenantUri = URI.create(user.getTenantId());
            FilePolicyServiceUtils.updateTaskTenant(_dbClient, filePolicy, "assign", taskObj, userTenantUri);
        }

        return taskResponse;

    }

    private void canUserUnAssignPolicyAtGivenLevel(FilePolicy policy, URI res) {
        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(policy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                if (!userHasSystemAdminRoles()) {
                    _log.error("User does not sufficient roles to unassign policy at vpool");
                    throw APIException.forbidden.onlySystemAdminsCanAssignVpoolPolicies(policy.getFilePolicyName());
                } else {
                    _permissionsHelper.getObjectById(res, VirtualPool.class);
                }
                break;
            case project:
                if (!userHasTenantAdminRoles()) {
                    _log.error("User does not sufficient roles to unassign policy at project");
                    throw APIException.forbidden.onlyTenantAdminsCanAssignProjectPolicies(policy.getFilePolicyName());
                } else {
                    _permissionsHelper.getObjectById(res, Project.class);
                }
                break;
            case file_system:
                if (!userHasTenantAdminRoles()) {
                    _log.error("User does not sufficient roles to unassign policy at file system");
                    throw APIException.forbidden.onlyTenantAdminsCanAssignFileSystemPolicies(policy.getFilePolicyName());
                }
                break;
            default:
                _log.error("Not a valid policy apply level: " + applyLevel);
        }
    }

    private boolean canUserAssignPolicyAtGivenLevel(FilePolicy policy) {

        // user should have system admin role to assign policy to vpool
        if (policy.getApplyAt() != null) {
            switch (policy.getApplyAt()) {
                case "vpool":
                    if (!userHasSystemAdminRoles()) {
                        _log.error("User does not sufficient roles to assign policy at vpool");
                        throw APIException.forbidden.onlySystemAdminsCanAssignVpoolPolicies(policy.getFilePolicyName());
                    }
                    break;
                case "project":
                    if (!userHasTenantAdminRoles()) {
                        _log.error("User does not sufficient roles to assign policy at project");
                        throw APIException.forbidden.onlyTenantAdminsCanAssignProjectPolicies(policy.getFilePolicyName());
                    }
                    // Verify the user has an access to given projects
                    break;
                case "file_system":
                    if (!userHasTenantAdminRoles()) {
                        _log.error("User does not sufficient roles to assign policy at file system");
                        throw APIException.forbidden.onlyTenantAdminsCanAssignFileSystemPolicies(policy.getFilePolicyName());
                    }
                    break;
                default:
                    return false;
            }
            return true;
        }

        return false;
    }

    private boolean canAccessFilePolicy(FilePolicy filePolicy) {
        StringSet tenants = filePolicy.getTenantOrg();

        if (tenants == null || tenants.isEmpty()) {
            return true;
        }

        if (isSystemAdmin()) {
            return true;
        }

        StorageOSUser user = getUserFromContext();
        String userTenantId = user.getTenantId();

        return tenants.contains(userTenantId);

    }

    private boolean doesResHasPolicyOfSameType(StringSet existingPolicies, FilePolicy filePolicy) {
        if (existingPolicies != null && !existingPolicies.isEmpty()) {
            List<URI> existingFilePolicyURIs = new ArrayList<URI>();
            for (String existingPolicy : existingPolicies) {
                existingFilePolicyURIs.add(URI.create(existingPolicy));
            }
            Iterator<FilePolicy> iterator = _dbClient.queryIterativeObjects(FilePolicy.class, existingFilePolicyURIs, true);

            if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_replication.name())) {
                while (iterator.hasNext()) {
                    FilePolicy fp = iterator.next();
                    if (FilePolicy.FilePolicyType.file_replication.name().equals(fp.getFilePolicyType())) {
                        return true;
                    }
                }

            } else if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_snapshot.name())) {
                while (iterator.hasNext()) {
                    FilePolicy fp = iterator.next();
                    if (FilePolicy.FilePolicyType.file_snapshot.name().equals(fp.getFilePolicyType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<URI> getAssociatedStorageSystemsByVPool(VirtualPool vpool) {

        Set<URI> storageSystemURISet = new HashSet<URI>();

        StringSet storagePoolURISet = null;
        if (vpool.getUseMatchedPools()) {
            storagePoolURISet = vpool.getMatchedStoragePools();
        } else {
            storagePoolURISet = vpool.getAssignedStoragePools();
        }

        if (storagePoolURISet != null && !storagePoolURISet.isEmpty()) {
            for (Iterator<String> iterator = storagePoolURISet.iterator(); iterator.hasNext();) {
                URI storagePoolURI = URI.create(iterator.next());
                StoragePool spool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
                if (spool != null && !spool.getInactive()) {
                    storageSystemURISet.add(spool
                            .getStorageDevice());
                }
            }
        }
        return new ArrayList<URI>(storageSystemURISet);
    }

    private TaskResourceRep createAssignFilePolicyTask(FilePolicy filepolicy, String taskId) {
        filepolicy.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.ASSIGN_FILE_POLICY);
        filepolicy.getOpStatus().createTaskStatus(taskId, op);
        _dbClient.updateObject(filepolicy);
        return toTask(filepolicy, taskId);
    }

    private StringSet getSourceVArraySet(VirtualPool vpool, FilePolicy filePolicy) {

        StringSet vpoolVArraySet = vpool.getVirtualArrays();

        if (filePolicy.getFileReplicationType() != null
                && filePolicy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.REMOTE.name())) {
            Set<String> topologyVArraySet = new HashSet<String>();
            List<FileReplicationTopology> dbTopologies = queryDBReplicationTopologies(filePolicy);

            if (dbTopologies != null && !dbTopologies.isEmpty()) {
                for (Iterator<FileReplicationTopology> iterator = dbTopologies.iterator(); iterator.hasNext();) {
                    FileReplicationTopology fileReplicationTopology = iterator.next();
                    topologyVArraySet.add(fileReplicationTopology.getSourceVArray().toString());
                }
            }
            vpoolVArraySet.retainAll(topologyVArraySet);
        }

        return vpoolVArraySet;
    }
}
