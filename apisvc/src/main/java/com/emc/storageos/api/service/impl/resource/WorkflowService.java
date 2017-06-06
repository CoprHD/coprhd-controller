/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.api.mapper.WorkflowMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static java.util.Collections.disjoint;

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
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.workflow.StepList;
import com.emc.storageos.model.workflow.WorkflowList;
import com.emc.storageos.model.workflow.WorkflowRestRep;
import com.emc.storageos.model.workflow.WorkflowStepRestRep;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorExceptions;
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
        WorkflowList list = new WorkflowList();
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        Iterator<Workflow> workflowIter = _dbClient.queryIterativeObjects(Workflow.class, workflowIds);
        while (workflowIter.hasNext()) {           
            // A user that has one of the system roles can see any workflow.
            // Otherwise, the workflow must have the same tenant as the user.
            Workflow workflow = workflowIter.next();
            if (userIsOnlyTenantAdmin()) {
                // User is only tenant admin so only return workflows for that tenant.
                if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                    continue;
                }
            }

            list.getWorkflows().add(map(workflow));
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
        WorkflowList list = new WorkflowList();
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        Iterator<Workflow> workflowIter = _dbClient.queryIterativeObjects(Workflow.class, workflowIds);
        while (workflowIter.hasNext()) {
            // A user that has one of the system roles can see any workflow.
            // Otherwise, the workflow must have the same tenant as the user.
            Workflow workflow = workflowIter.next();
            if (userIsOnlyTenantAdmin()) {
                // User is only tenant admin so only return workflows for that tenant.
                if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                    continue;
                }
            }
            
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
        WorkflowList list = new WorkflowList();
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        Iterator<Workflow> workflowIter = _dbClient.queryIterativeObjects(Workflow.class, workflowIds);
        while (workflowIter.hasNext()) {
            // A user that has one of the system roles can see any workflow.
            // Otherwise, the workflow must have the same tenant as the user.
            Workflow workflow = workflowIter.next();
            if (userIsOnlyTenantAdmin()) {
                // User is only tenant admin so only return workflows for that tenant.
                if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                    continue;
                }
            }
            
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
        Long timeDiff = new Long(minutes) * 1000 * 60;
        Long currentTime = System.currentTimeMillis();
        
        WorkflowList list = new WorkflowList();
        List<URI> workflowIds = _dbClient.queryByType(Workflow.class, true);
        Iterator<Workflow> workflowIter = _dbClient.queryIterativeObjects(Workflow.class, workflowIds);
        while (workflowIter.hasNext()) {
            // A user that has one of the system roles can see any workflow.
            // Otherwise, the workflow must have the same tenant as the user.
            Workflow workflow = workflowIter.next();
            if (userIsOnlyTenantAdmin()) {
                // User is only tenant admin so only return workflows for that tenant.
                if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                    continue;
                }
            }
            
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
        if (userIsOnlyTenantAdmin()) {
            // User is only tenant admin so only return workflows for that tenant.
            if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                throw APIException.badRequests.userNotAuthorizedForWorkflow();
            }
        }
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
        ArgValidator.checkFieldUriType(id, Workflow.class, "id");
        Workflow workflow = queryResource(id);
        if (userIsOnlyTenantAdmin()) {
            // User is only tenant admin so only return steps for workflows for that tenant.
            if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                throw APIException.badRequests.userNotAuthorizedForWorkflow();
            }
        }

        StepList list = new StepList();
        URIQueryResultList stepURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getWorkflowWorkflowStepConstraint(id), stepURIs);
        Iterator<URI> iter = stepURIs.iterator();
        while (iter.hasNext()) {
            URI workflowStepURI = iter.next();
            WorkflowStep step = _dbClient.queryObject(WorkflowStep.class, workflowStepURI);
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
        Workflow workflow = queryResource(step.getWorkflowId());
        if (userIsOnlyTenantAdmin()) {
            // User is only tenant admin so only return workflow steps for that tenant.
            if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                throw APIException.badRequests.userNotAuthorizedForWorkflowStep();
            }
        }        
        
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
        if (userIsOnlyTenantAdmin()) {
            // User is only tenant admin so only allow rollback on workflows for that tenant.
            if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                throw APIException.badRequests.userNotAuthorizedForWorkflow();
            }
        }        
    	
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
        if (userIsOnlyTenantAdmin()) {
            // User is only tenant admin so only allow resume on workflows for that tenant.
            if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                throw APIException.badRequests.userNotAuthorizedForWorkflow();
            }
        }        
        
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
        return suspendWorkflowStep(uri, NullColumnValueGetter.getNullURI());
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
        if (userIsOnlyTenantAdmin()) {
            // User is only tenant admin so only allow rollback on workflows for that tenant.
            if (!isTopLevelWorkflowForUserTenant(getTopLevelWorkflow(workflow))) {
                throw APIException.badRequests.userNotAuthorizedForWorkflow();
            }
        }        

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
     * @param workflow export group 
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
	
	/**
	 * Determines if the user only has tenant admin roles, which is the case
	 * if it does not have any of the system level roles allowed by the service.
	 * 
	 * @return true if user had tenant admin role, but none of the supported system roles, otherwise false.
	 */
	private boolean userIsOnlyTenantAdmin() {
	    return !userHasRoles(Role.SYSTEM_ADMIN.name(), Role.RESTRICTED_SYSTEM_ADMIN.name(), Role.SYSTEM_MONITOR.name());
	}

	/**
	 * Determines if the user has one of the passed roles.
	 * 
	 * @param roles The roles to verify
	 * 
	 * @return true if the user has one of the passed roles, else false
	 */
	private boolean userHasRoles(String... roles) {
	    StorageOSUser user = getUserFromContext();
	    Set<String> userRoles = user.getRoles();
	    return !disjoint(userRoles, Arrays.asList(roles));	    
	}

	/**
	 * Gets the top-level workflow for the passed workflow. Returns the passed workflow
	 * if it has no parent workflow and is therefore a top-level workflow.
	 * 
	 * @param workflow A reference to the workflow
	 * 
	 * @return
	 */
    private Workflow getTopLevelWorkflow(Workflow workflow) {
        Workflow topLevelWorkflow = workflow;
        Workflow parentWorkflow = getParentWorkflow(workflow);
        if (parentWorkflow != null) {
            topLevelWorkflow = getTopLevelWorkflow(parentWorkflow);
        }
        
        return topLevelWorkflow;
    }

    /**
     * Gets the parent workflow for the passed workflow.
     * 
     * @param workflow A reference to the workflow
     * 
     * @return The parent workflow or null for a top-level workflow.
     */
    private Workflow getParentWorkflow(Workflow workflow) {
        Workflow parentWorkflow = null;
        if (workflow != null) {
            List<WorkflowStep> wfSteps = CustomQueryUtility.queryActiveResourcesByConstraint(
                    _dbClient, WorkflowStep.class, AlternateIdConstraint.Factory.getWorkflowStepByStepId(workflow.getOrchTaskId()));
            if (!wfSteps.isEmpty()) {
                // There should only be a single workflow step that has a step id that is equal
                // to the orchestration task id for a workflow. The workflow for that step is
                // the parent workflow for the passed workflow.
                URI parentWorkflowURI = wfSteps.get(0).getWorkflowId();
                parentWorkflow = queryResource(parentWorkflowURI);
            }
        }
        
        return parentWorkflow;
    }

    /**
     * Determines if the passed top-level workflow is valid for the user's tenant.
     * Child workflows should not be passed to this routine.
     * 
     * @param topLevelWorkflow A reference to a top-level workflow.
     * 
     * @return true if the user's tenant is the workflow tenant.
     */
	private boolean isTopLevelWorkflowForUserTenant(Workflow topLevelWorkflow) {
	    boolean workflowForTenant = false;
	    
	    // Since the tenant is not directly associated to a workflow, we find the 
	    // Task instance(s) whose request id is the same as the orchestration task
	    // id for the workflow. We can then get the tenant from the task and compare
	    // that tenant to the user tenant.
        String wfOrchTaskId = topLevelWorkflow.getOrchTaskId();
        List<Task> wfTasks = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, Task.class, AlternateIdConstraint.Factory.getTasksByRequestIdConstraint(wfOrchTaskId));
        if (!wfTasks.isEmpty()) {
            for (Task wfTask : wfTasks) {
                // There could actually be multiple tasks. For example, when creating a VPLEX
                // local volume, there will be a Task created for the VPLEX volume and another
                // Task for the backend volume and both will have the same request id. Additionally,
                // the tenant for backend volume Task will always be the root tenant rather than
                // tenant of the user creating the volume. Only the VPLEX volume Task will have that
                // tenant. So, if the tenant of any of the Task instances found with the workflow's
                // orchestration task id has the current user's tenant, then the workflow is for
                // the tenant.
                if (wfTask.getTenant().toString().equals(getUserFromContext().getTenantId())) {
                    workflowForTenant = true;
                    break;
                }
            }
        } 
        
        return workflowForTenant;
	}
}
