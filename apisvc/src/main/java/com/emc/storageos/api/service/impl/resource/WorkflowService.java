/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.api.mapper.WorkflowMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.workflow.StepList;
import com.emc.storageos.model.workflow.WorkflowList;
import com.emc.storageos.model.workflow.WorkflowRestRep;
import com.emc.storageos.model.workflow.WorkflowStepRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.workflow.WorkflowController;
import com.emc.storageos.workflow.WorkflowState;

/**
 * API interface for a Workflow and WorkflowStep.
 * This interface is read-only and returns historical information about
 * Workflow execution.
 * 
 * @author Watson
 */
@Path("/vdc/workflows")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
        Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.TENANT_ADMIN })
public class WorkflowService extends TaskResourceService {
    protected Workflow queryResource(URI id) {
        ArgValidator.checkUri(id);
        Workflow workflow = _dbClient.queryObject(Workflow.class, id);
        ArgValidator.checkEntityNotNull(workflow, id, isIdEmbeddedInURL(id));

        return workflow;
    }

    /**
     * Returns a list of all Workflows.
     * 
     * @brief List all workflows
     * @return WorkflowList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getWorkflows() {
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        WorkflowList list = new WorkflowList();
        for (URI workflowId : workflowIds) {
            Workflow workflow = _dbClient.queryObject(Workflow.class, workflowId);
            if (workflow == null) {
                continue;
            }
            list.getWorkflows().add(map(workflow));
            workflow = null;
        }
        return list;
    }

    /**
     * Returns the active workflows.
     * 
     * @brief List active workflows
     */
    @GET
    @Path("/active")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getActiveWorkflows() {
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        List<Workflow> workflows = _dbClient.queryObject(Workflow.class, workflowIds);
        WorkflowList list = new WorkflowList();
        for (Workflow workflow : workflows) {
            if (workflow.getCompleted() == false) {
                list.getWorkflows().add(map(workflow));
            }
        }
        return list;
    }

    /**
     * Returns the completed workflows.
     * 
     * @brief List completed workflows
     */
    @GET
    @Path("/completed")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getCompletedWorkflows() {
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        List<Workflow> workflows = _dbClient.queryObject(Workflow.class, workflowIds);
        WorkflowList list = new WorkflowList();
        for (Workflow workflow : workflows) {
            if (workflow.getCompleted() == true) {
                list.getWorkflows().add(map(workflow));
            }
        }
        return list;
    }

    /**
     * Returns workflows created in the last specified number of minutes.
     * 
     * @brief List workflows created in specified time period
     */
    @GET
    @Path("/recent")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowList getRecentWorkflows(@QueryParam("min") String minutes) {
        if (minutes == null) {
            minutes = "10";
        }
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        List<Workflow> workflows = _dbClient.queryObject(Workflow.class, workflowIds);
        Long timeDiff = new Long(minutes) * 1000 * 60;
        Long currentTime = System.currentTimeMillis();
        WorkflowList list = new WorkflowList();
        for (Workflow workflow : workflows) {
            // If created in the last n minutes
            if ((currentTime - workflow.getCreationTime().getTimeInMillis()) < timeDiff) {
                list.getWorkflows().add(map(workflow));
            }
        }
        return list;
    }

    /**
     * Returns information about the specified workflow.
     * 
     * @param id the URN of a ViPR workflow
     * @brief Show workflow
     * @return Information of specific workflow
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowRestRep getWorkflow(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Workflow.class, "id");
        Workflow workflow = queryResource(id);
        return map(workflow);
    }

    /**
     * Gets a list of all the steps in a particular workflow.
     * 
     * @param id the URN of a ViPR workflow
     * @brief List workflow steps
     * @return List of steps of a workflow
     */
    @GET
    @Path("/{id}/steps")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public StepList getStepList(@PathParam("id") URI id) {
        StepList list = new StepList();
        URIQueryResultList stepURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getWorkflowWorkflowStepConstraint(id),
                stepURIs);
        Iterator<URI> iter = stepURIs.iterator();
        while (iter.hasNext()) {
            URI workflowStepURI = iter.next();
            WorkflowStep step = _dbClient
                    .queryObject(WorkflowStep.class, workflowStepURI);
            list.getSteps().add(map(step, getChildWorkflows(step)));
        }
        return list;
    }

    /**
     * Returns a single WorkflowStep.
     * 
     * @param stepId
     * @brief Show workflow step
     * @return Single WorkflowStep
     */
    @GET
    @Path("/steps/{stepid}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public WorkflowStepRestRep getStep(@PathParam("stepid") URI stepId) {
        ArgValidator.checkFieldUriType(stepId, WorkflowStep.class, "stepid");
        WorkflowStep step = _dbClient.queryObject(WorkflowStep.class, stepId);
        ArgValidator.checkEntityNotNull(step, stepId, isIdEmbeddedInURL(stepId));
        return map(step, getChildWorkflows(step));
    }

    /**
     * Rolls back a suspended workflow.
     * @preq none
     * @brief Rolls back a suspended workflow
     * @param uri - URI of the suspended workflow.
     * @return - No data returned in response body
     */
    @PUT
    @Path("/{id}/rollback")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public TaskResourceRep rollbackWorkflow(@PathParam("id") URI uri) {
    	Workflow workflow = queryResource(uri);
    	verifySuspendedWorkflow(workflow);
    	String taskId = UUID.randomUUID().toString();
    	Operation op = initTaskStatus(_dbClient, workflow, taskId, Operation.Status.pending, ResourceOperationTypeEnum.WORKFLOW_ROLLBACK);
    	getController().rollbackWorkflow(uri, taskId);
    	return toTask(workflow,taskId, op);
    }
    
    /**
     * Resumes a suspended workflow.
     * @preq none
     * @brief Resumes a suspended workflow
     * @param uri - URI of the suspended workflow.
     * @return - No data returned in response body
     */
    @PUT
    @Path("/{id}/resume")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public TaskResourceRep resumeWorkflow(@PathParam("id") URI uri) {
    	Workflow workflow = queryResource(uri);
    	verifySuspendedWorkflow(workflow);
    	String taskId = UUID.randomUUID().toString();
        Operation op = initTaskStatus(_dbClient, workflow, taskId, Operation.Status.pending, ResourceOperationTypeEnum.WORKFLOW_RESUME);
        getController().resumeWorkflow(uri, taskId);
    	return toTask(workflow, taskId, op);
    }
    
    protected static void verifySuspendedWorkflow(Workflow workflow) {
        if (workflow.getCompletionState() == null) {
            throw APIException.badRequests.workflowCompletionStateNotFound(workflow.getId());
        }
        WorkflowState state = WorkflowState.valueOf(WorkflowState.class, workflow.getCompletionState());
        EnumSet<WorkflowState> expected = EnumSet.of(WorkflowState.SUSPENDED_NO_ERROR, WorkflowState.SUSPENDED_ERROR);
        ArgValidator.checkFieldForValueFromEnum(state, "Workflow completion state", expected);
    }
    
    /**
     * Suspends a workflow as soon as possible, which is when the next step completes and all
     * executing steps have completed. It is not possible to suspend in the middle of a step.
     * @param uri
     * @brief Suspends a workflow
     * @return
     */
    @PUT
    @Path("/{id}/suspend")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public Response suspendWorkflow(@PathParam("id") URI uri) {
        suspendWorkflowStep(uri, NullColumnValueGetter.getNullURI());
        return Response.ok().build();
    }
    
    /**
     * Suspends a workflow when it tries to execute a given step, and all other executing steps
     * have suspended.
     * @preq none
     * @brief Suspends a workflow
     * @param uri - URI of the workflow.
     * @param stepURI - URI of the workflow step 
     * @return - No data returned in response body
     */
    @PUT
    @Path("/{id}/suspend/{stepId}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
            Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public Response suspendWorkflowStep(@PathParam("id") URI uri, @PathParam("stepId") URI stepURI) {
        Workflow workflow = queryResource(uri);
        // Verify the workflow is either RUNNING or ROLLING_BACK
        EnumSet<WorkflowState> expected = 
                EnumSet.of(WorkflowState.RUNNING, WorkflowState.ROLLING_BACK);
        if (workflow.getCompletionState() == null) {
            throw APIException.badRequests.workflowCompletionStateNotFound(workflow.getId());
        }
        WorkflowState completionState = WorkflowState.valueOf(workflow.getCompletionState());
        ArgValidator.checkFieldForValueFromEnum(completionState, "Workflow State", expected);
        if (!NullColumnValueGetter.isNullURI(stepURI)) {
            // Validate step id.
            WorkflowStep step = _dbClient.queryObject(WorkflowStep.class, stepURI);
            ArgValidator.checkEntityNotNull(step, stepURI, isIdEmbeddedInURL(stepURI));
        } 
        String taskId = UUID.randomUUID().toString();
        getController().suspendWorkflowStep(uri, stepURI, taskId);
        return Response.ok().build();
    }
    
    private List<URI> getChildWorkflows(WorkflowStep step) {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getWorkflowByOrchTaskId(step.getStepId()), result);
        List<URI> childWorkflows = new ArrayList<URI>();
        while (result.iterator().hasNext()) {
            childWorkflows.add(result.iterator().next());
        }
        return childWorkflows;
    }
    
    private WorkflowController getController() {
        return getController(WorkflowController.class, WorkflowController.WORKFLOW_CONTROLLER_DEVICE);
    }
    
    /**
     * Convenience method for initializing a task object with a status
     * 
     * @paramworkflow export group 
     * @param task task ID
     * @param status status to initialize with
     * @param opType operation type
     * @return operation object
     */
    protected static Operation initTaskStatus(DbClient dbClient, Workflow workflow, String task, Operation.Status status, ResourceOperationTypeEnum opType) {
        if (workflow.getOpStatus() == null) {
            workflow.setOpStatus(new OpStatusMap());
        }
        Operation op = new Operation();
        op.setResourceType(opType);
        if (status == Operation.Status.ready) {
            op.ready();
        } 
        dbClient.createTaskOpStatus(Workflow.class, workflow.getId(), task, op);
        return op;
    }

	@Override
	protected URI getTenantOwner(URI id) {
		return null;
	}

	@Override
	protected ResourceTypeEnum getResourceType() {
		return ResourceTypeEnum.WORKFLOW;
	}
}
