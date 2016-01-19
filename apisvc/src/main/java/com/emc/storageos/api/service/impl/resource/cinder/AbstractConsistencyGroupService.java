/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.api.service.impl.resource.cinder;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;

import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.resource.utils.CinderApiUtils;
import com.emc.storageos.cinder.model.ConsistencyGroupDetail;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;

/**
 * Abstract class for Handling consistency group. This consistency group service is for handling consistency group CRUD
 * requests that are coming from Openstack.
 * 
 * @author singhc1
 * 
 */
public abstract class AbstractConsistencyGroupService extends TaskResourceService {

    @Override
    protected DataObject queryResource(URI id) {
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return null;
    }

    protected CinderHelpers getCinderHelper() {
        return CinderHelpers.getInstance(_dbClient, _permissionsHelper);
    }

    /**
     * This function returns consistency group
     * 
     * @param consistencyGroupId
     * @param openstackTenantId
     * @return BlockConsistencyGroup
     */
    protected BlockConsistencyGroup findConsistencyGroup(
            String consistencyGroupId, String openstackTenantId) {
        BlockConsistencyGroup blockConsistencyGroup = getCinderHelper().queryConsistencyGroupByTag(URI.create(consistencyGroupId),
                getUserFromContext());
        Project project = getCinderHelper().getProject(openstackTenantId, getUserFromContext());
        if (project == null) {
            CinderApiUtils.createErrorResponse(400, "Bad Request: Project not exist for the request");
        }
        if (blockConsistencyGroup != null) {
            if ((project != null) &&
                    (blockConsistencyGroup.getProject().getURI().toString().equalsIgnoreCase(project.getId().toString()))) {
                return blockConsistencyGroup;
            }
        }
        return null;
    }

    /**
     * This function return detail of consistency group in pojo class object
     * 
     * @param blockConsistencyGroup
     * @return ConsistencyGroupDetail
     */
    protected ConsistencyGroupDetail getConsistencyGroupDetail(BlockConsistencyGroup blockConsistencyGroup) {
        ConsistencyGroupDetail response = new ConsistencyGroupDetail();
        if (blockConsistencyGroup != null) {
            response.id = CinderApiUtils.splitString(blockConsistencyGroup.getId().toString(), ":", 3);
            response.name = blockConsistencyGroup.getLabel();
            response.created_at = CinderApiUtils.timeFormat(blockConsistencyGroup.getCreationTime());
            if (blockConsistencyGroup.getTag() != null) {
                for (ScopedLabel tag : blockConsistencyGroup.getTag()) {
                    switch (tag.getScope()) {
                        case "availability_zone":
                            response.availability_zone = tag.getLabel();
                        case "status":
                            response.status = tag.getLabel();
                        case "description":
                            response.description = tag.getLabel();
                    }
                }
            }
        }
        return response;

    }

    /**
     * Check to see if the consistency group is active and not created. In
     * this case we can delete the consistency group. Otherwise we should
     * not delete the consistency group.
     * 
     * @param consistencyGroup
     *            A reference to the CG.
     * 
     * @return True if the CG is active and not created.
     */
    protected boolean canDeleteConsistencyGroup(
            final BlockConsistencyGroup consistencyGroup) {
        return (!consistencyGroup.getInactive() && !consistencyGroup.created());
    }

    /**
     * Simply return a task that indicates that the operation completed.
     * 
     * @param consistencyGroup [in] BlockConsistencyGroup object
     * @param task [in] - Operation task ID
     * @return
     */
    protected TaskResourceRep finishDeactivateTask(BlockConsistencyGroup consistencyGroup, String task) {
        URI id = consistencyGroup.getId();
        Operation op = new Operation();
        op.ready();
        op.setProgress(100);
        op.setResourceType(ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP);
        Operation status = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, id, task, op);
        return toTask(consistencyGroup, task, status);
    }

}
