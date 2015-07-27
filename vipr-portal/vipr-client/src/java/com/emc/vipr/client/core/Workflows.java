/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.workflow.StepList;
import com.emc.storageos.model.workflow.WorkflowList;
import com.emc.storageos.model.workflow.WorkflowRestRep;
import com.emc.storageos.model.workflow.WorkflowStepRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Workflows resources.
 * <p>
 * Base URL: <tt>/vdc/workflows</tt>
 */
public class Workflows extends AbstractCoreResources<WorkflowRestRep> {

    public Workflows(ViPRCoreClient parent, RestClient client) {
        super(parent, client, WorkflowRestRep.class, PathConstants.WORKFLOW_URL);
    }

    @Override
    public Workflows withInactive(boolean inactive) {
        return (Workflows) super.withInactive(inactive);
    }

    @Override
    public Workflows withInternal(boolean internal) {
        return (Workflows) super.withInternal(internal);
    }

    /**
     * Gets a list of workflows from the given path.
     * 
     * @param path
     *        the path to get.
     * @param args
     *        the path arguments.
     * @return the list of workflows.
     */
    protected List<WorkflowRestRep> getList(String path, Object... args) {
        WorkflowList response = client.get(WorkflowList.class, path, args);
        return defaultList(response.getWorkflows());
    }

    /**
     * Gets the list of all workflows.
     * <p>
     * API Call: <tt>GET /vdc/workflows</tt>
     *
     * @return the list of recent workflows.
     */
    public List<WorkflowRestRep> getAll() {
        return getList(baseUrl);
    }

    /**
     * Gets the list of active workflows.
     * <p>
     * API Call: <tt>GET /vdc/workflows/active</tt>
     * 
     * @return the list of active workflows.
     */
    public List<WorkflowRestRep> getActive() {
        return getList(baseUrl + "/active");
    }

    /**
     * Gets the list of completed workflows.
     * <p>
     * API Call: <tt>GET /vdc/workflows/completed</tt>
     * 
     * @return the list of completed workflows.
     */
    public List<WorkflowRestRep> getCompleted() {
        return getList(baseUrl + "/completed");
    }

    /**
     * Gets the list of recent workflows.
     * <p>
     * API Call: <tt>GET /vdc/workflows/recent</tt>
     * 
     * @return the list of recent workflows.
     */
    public List<WorkflowRestRep> getRecent() {
        return getList(baseUrl + "/recent");
    }

    /**
     * Gets the list of steps for the given workflow by ID.
     * <p>
     * API Call: <tt>GET /vdc/workflows/{id}/steps</tt>
     * 
     * @param id
     *        the ID of the workflow.
     * @return the list of workflow steps.
     */
    public List<WorkflowStepRestRep> getSteps(URI id) {
        StepList response = client.get(StepList.class, getIdUrl() + "/steps", id);
        return defaultList(response.getSteps());
    }

    /**
     * Gets a single workflow step by ID.
     * <p>
     * API Call: <tt>GET /vdc/workflows/steps/{id}</tt>
     * 
     * @param id
     *        the ID of the workflow step.
     * @return the workflow step.
     */
    public WorkflowStepRestRep getStep(URI id) {
        return client.get(WorkflowStepRestRep.class, PathConstants.WORKFLOW_STEP_URL + "/{id}", id);
    }
}
