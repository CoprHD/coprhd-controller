/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.utils.FilePolicyServiceUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.FilePolicyAssignParam;
import com.emc.storageos.model.file.FilePolicyParam;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * @author jainm15
 */
@Path("/file/filePolicies")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.TENANT_ADMIN })
public class FilePolicyService extends TaskResourceService {
    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "FilePolicyService";

    private static final String EVENT_SERVICE_TYPE = "FilePolicy";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected DataObject queryResource(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        // TODO Auto-generated method stub
        return null;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public FilePolicyParam createFilePolicy(FilePolicyParam param) {

        // Make policy name as mandatory field
        ArgValidator.checkFieldNotNull(param.getPolicyName(), "policyName");

        // Check for duplicate policy name
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }
        // check policy type is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getPolicyType(), "policy_type",
                EnumSet.allOf(FilePolicyType.class));

        _log.info("file policy creation started -- ");

        StringBuilder errorMsg = new StringBuilder();

        /**
         * Replication Policy Checks and creation.
         */
        if (param.getPolicyType().equals(FilePolicyType.file_replication.name())) {
            FilePolicy fileReplicationPolicy = new FilePolicy();

            // Validate replication policy schedule parameters
            boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
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
            fileReplicationPolicy.setLabel(param.getPolicyName());
            fileReplicationPolicy.setFilePolicyName(param.getPolicyName());
            fileReplicationPolicy.setFilePolicyType(param.getPolicyType());
            if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
                fileReplicationPolicy.setFilePolicyDescription(param.getPolicyDescription());
            }
            fileReplicationPolicy.setScheduleFrequency(param.getReplicationPolicyParams().getPolicySchedule().getScheduleFrequency());
            fileReplicationPolicy.setFileReplicationType(param.getReplicationPolicyParams().getReplicationType());
            fileReplicationPolicy.setFileReplicationCopyMode(param.getReplicationPolicyParams().getReplicationCopyMode());
            this._dbClient.createObject(fileReplicationPolicy);
            _log.info("Policy {} created successfully", fileReplicationPolicy);
        }

        /**
         * Snapshot Policy checks and creation.
         */
        else if (param.getPolicyType().equals(FilePolicyType.file_snapshot.name())) {
            FilePolicy fileSnapshotPolicy = new FilePolicy();

            // Validate snapshot policy schedule parameters
            boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                    param.getSnapshotPolicyPrams().getPolicySchedule(), fileSnapshotPolicy, errorMsg);
            if (!isValidSchedule && errorMsg.length() > 0) {
                _log.error("Failed to create file snapshot policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
            }

            // Validate snapshot policy expire parameters..
            if (param.getSnapshotPolicyPrams().getSnapshotExpireParams() != null) {
                FilePolicyServiceUtils.validateSnapshotPolicyParam(param.getSnapshotPolicyPrams());
            } else {
                errorMsg.append("Required parameter snapshot_expire was missing or empty");
                _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
                throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
            }
            fileSnapshotPolicy.setId(URIUtil.createId(FilePolicy.class));
            fileSnapshotPolicy.setLabel(param.getPolicyName());
            fileSnapshotPolicy.setFilePolicyType(param.getPolicyType());
            fileSnapshotPolicy.setFilePolicyName(param.getPolicyName());
            if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
                fileSnapshotPolicy.setFilePolicyDescription(param.getPolicyDescription());
            }
            fileSnapshotPolicy.setScheduleFrequency(param.getSnapshotPolicyPrams().getPolicySchedule().getScheduleFrequency());
            fileSnapshotPolicy.setSnapshotExpireType(param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType());
            if (!param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType()
                    .equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
                fileSnapshotPolicy.setSnapshotExpireTime((long) param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireValue());
            }
            this._dbClient.createObject(fileSnapshotPolicy);
            _log.info("Snapshot policy {} created successfully", fileSnapshotPolicy);
        }

        /**
         * Quota Policy Creation and params checks.
         */
        else if (param.getPolicyType().equals(FilePolicyType.file_quota.name())) {
            //
        }
        return param;
    }

    @PUT
    @Path("/{id}/assign-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public void assignFilePolicy(@PathParam("id") URI id, FilePolicyAssignParam param) {

        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filepolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkFieldValueFromEnum(param.getApplyAt(), "apply_at",
                EnumSet.allOf(FilePolicyApplyLevel.class));

        if (param.getApplyAt().equals(FilePolicyApplyLevel.vpool.name())) {
            assignFilePolicyToVpool(param, filepolicy);
        } else if (param.getApplyAt().equals(FilePolicyApplyLevel.project.name())) {
            assignFilePolicyToProject(param, filepolicy);
        } else if (param.getApplyAt().equals(FilePolicyApplyLevel.file_system.name())) {
            assignFilePolicyToFS(param, filepolicy);
        }
        this._dbClient.updateObject(filepolicy);
    }

    /**
     * Assigning policy at vpool level
     * 
     * @param param
     * @param filepolicy
     */
    private void assignFilePolicyToVpool(FilePolicyAssignParam param, FilePolicy filepolicy) {
        StringBuilder errorMsg = new StringBuilder();
        ArgValidator.checkFieldNotNull(param.getVpoolAssignParams(), "vpool_assign_param");

        if (param.getVpoolAssignParams().isAssigntoAll()) {
            // policy has to be applied on all applicable file vpools
            List<URI> vpoolIDs = this._dbClient.queryByType(VirtualPool.class, true);
            List<VirtualPool> virtualPools = this._dbClient.queryObject(VirtualPool.class, vpoolIDs);
            Set<String> assignedResources = new HashSet<String>();
            for (VirtualPool virtualPool : virtualPools) {
                if (virtualPool.getType().equals(VirtualPool.Type.file.name())
                        && FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, virtualPool)) {
                    assignedResources.add(virtualPool.getId().toString());
                }
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.vpool.name());
            filepolicy.setAssignedResources(assignedResources);

        } else {
            // Policy has to be applied on specified file vpools..
            Set<URI> vpoolURIs = param.getVpoolAssignParams().getAssigntoVpools();
            Set<String> assignedResources = new HashSet<String>();
            for (URI vpoolURI : vpoolURIs) {
                ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "vpool");
                VirtualPool virtualPool = this._dbClient.queryObject(VirtualPool.class, vpoolURI);
                ArgValidator.checkEntity(virtualPool, vpoolURI, false);
                if (FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, virtualPool)) {
                    assignedResources.add(virtualPool.getId().toString());
                } else {
                    errorMsg.append("Provided vpool :" + virtualPool.getLabel() + " doesn't support policy type:"
                            + filepolicy.getFilePolicyType());
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.vpool.name());
            filepolicy.setAssignedResources(assignedResources);
        }
    }

    /**
     * Assign policy at project level
     * 
     * @param param
     * @param filepolicy
     */
    private void assignFilePolicyToProject(FilePolicyAssignParam param, FilePolicy filepolicy) {
        StringBuilder errorMsg = new StringBuilder();
        ArgValidator.checkFieldNotNull(param.getProjectAssignParams(), "project_assign_param");
        ArgValidator.checkFieldUriType(param.getProjectAssignParams().getVpool(), VirtualPool.class, "vpool");
        VirtualPool vpool = this._dbClient.queryObject(VirtualPool.class, param.getProjectAssignParams().getVpool());
        ArgValidator.checkEntity(vpool, vpool.getId(), false);

        // Check if the vpool supports provided policy type..
        if (FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, vpool)) {
            errorMsg.append("Provided vpool :" + vpool.getLabel() + " doesn't support policy type:"
                    + filepolicy.getFilePolicyType());
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        // Check if the vpool supports policy at project level..
        if (!vpool.isFilePolicyAtProjectLevel()) {
            errorMsg.append("Provided vpool :" + vpool.getLabel() + " doesn't support policy at project level");
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        if (param.getProjectAssignParams().isAssigntoAll()) {
            // policy has to be applied on all projects.
            List<URI> projectIDs = this._dbClient.queryByType(Project.class, true);
            Set<String> assignedResources = new HashSet<String>();
            for (URI projectID : projectIDs) {
                assignedResources.add(projectID.toString());
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.project.name());
            filepolicy.setAssignedResources(assignedResources);
            filepolicy.setPolicyVpool(param.getProjectAssignParams().getVpool());
        } else {
            // policy has to be applied on specified projects
            Set<URI> projetcURIs = param.getProjectAssignParams().getAssigntoProjects();
            Set<String> assignedResources = new HashSet<String>();
            for (URI projetcURI : projetcURIs) {
                ArgValidator.checkFieldUriType(projetcURI, Project.class, "project");
                Project project = this._dbClient.queryObject(Project.class, projetcURI);
                ArgValidator.checkEntity(project, projetcURI, false);
                assignedResources.add(projetcURI.toString());
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.project.name());
            filepolicy.setAssignedResources(assignedResources);
            filepolicy.setPolicyVpool(param.getProjectAssignParams().getVpool());
        }
    }

    /**
     * Assign policy at File system level
     * 
     * @param param
     * @param filepolicy
     */
    private void assignFilePolicyToFS(FilePolicyAssignParam param, FilePolicy filepolicy) {
        StringBuilder errorMsg = new StringBuilder();
        // check the vpool parameter
        ArgValidator.checkFieldNotNull(param.getFileSystemAssignParams(), "filesystem_assign_param");
        ArgValidator.checkFieldUriType(param.getFileSystemAssignParams().getVpool(), VirtualPool.class, "vpool");
        VirtualPool vpool = this._dbClient.queryObject(VirtualPool.class, param.getFileSystemAssignParams().getVpool());
        ArgValidator.checkEntity(vpool, vpool.getId(), false);

        // Check if the vpool supports provided policy type
        if (FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, vpool)) {
            errorMsg.append("Provided vpool :" + vpool.getLabel() + " doesn't support policy type:"
                    + filepolicy.getFilePolicyType());
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        // Check if the vpool supports policy at project level..
        if (!vpool.isFilePolicyAtFSLevel()) {
            errorMsg.append("Provided vpool :" + vpool.getLabel() + " doesn't support policy at file system level");
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        // Check the project parameter
        ArgValidator.checkFieldUriType(param.getFileSystemAssignParams().getProject(), Project.class, "project");
        Project project = this._dbClient.queryObject(Project.class, param.getFileSystemAssignParams().getProject());
        ArgValidator.checkEntity(project, project.getId(), false);

        filepolicy.setApplyAt(FilePolicyApplyLevel.file_system.name());
        filepolicy.setPolicyVpool(param.getFileSystemAssignParams().getVpool());
        filepolicy.setPolicyProject(param.getFileSystemAssignParams().getProject());
    }
}
