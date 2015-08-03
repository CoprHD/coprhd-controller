/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.workflow.WorkflowRestRep;
import com.emc.storageos.model.workflow.WorkflowStepRestRep;

public class WorkflowMapper {
    public static WorkflowRestRep map(Workflow from) {
        if (from == null) {
            return null;
        }
        WorkflowRestRep to = new WorkflowRestRep();
        mapDataObjectFields(from, to);
        to.setOrchestrationTaskId(from.getOrchTaskId());
        to.setOrchestrationControllerName(from.getOrchControllerName());
        to.setOrchestrationMethod(from.getOrchMethod());
        to.setCompletionMessage(from.getCompletionMessage());
        to.setCompletionState(from.getCompletionState());
        to.setCompleted(from.getCompleted());
        return to;
    }

    public static WorkflowStepRestRep map(WorkflowStep from) {
        if (from == null) {
            return null;
        }
        WorkflowStepRestRep to = new WorkflowStepRestRep();
        mapDataObjectFields(from, to);
        to.setControllerName(from.getControllerName());
        to.setDescription(from.getDescription());
        to.setSystemType(from.getSystemType());
        to.setExecuteMethod(from.getExecuteMethod());
        to.setMessage(from.getMessage());
        to.setState(from.getState());
        to.setStepGroup(from.getStepGroup());
        to.setStepId(from.getStepId());
        to.setWaitFor(from.getWaitFor());
        to.setSystem(from.getSystemId().toString());
        to.setWorkflow(toRelatedResource(ResourceTypeEnum.WORKFLOW, from.getWorkflowId()));
        to.setStartTime(from.getStartTime());
        to.setEndTime(from.getEndTime());
        return to;
    }

    public static WorkflowStepRestRep map(WorkflowStep from, List<URI> childWorkflows) {
        if (from == null) {
            return null;
        }
        WorkflowStepRestRep to = new WorkflowStepRestRep();
        mapDataObjectFields(from, to);
        to.setControllerName(from.getControllerName());
        to.setDescription(from.getDescription());
        to.setSystemType(from.getSystemType());
        to.setExecuteMethod(from.getExecuteMethod());
        to.setMessage(from.getMessage());
        to.setState(from.getState());
        to.setStepGroup(from.getStepGroup());
        to.setStepId(from.getStepId());
        to.setWaitFor(from.getWaitFor());
        if (false == from.getSystemId().equals(URIUtil.NULL_URI)) {
            to.setSystem(from.getSystemId().toString());
        } else {
            to.setSystem("");
        }
        to.setWorkflow(toRelatedResource(ResourceTypeEnum.WORKFLOW, from.getWorkflowId()));
        to.setStartTime(from.getStartTime());
        to.setEndTime(from.getEndTime());
        for (URI childWorkflow : childWorkflows) {
            to.getChildWorkflows().add(
                    toRelatedResource(ResourceTypeEnum.WORKFLOW, childWorkflow));
        }
        return to;
    }
}
