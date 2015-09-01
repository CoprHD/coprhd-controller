package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.coordinator.client.service.DistributedLockQueueEventListener;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueManagerImpl.Event;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Collection;
import java.util.List;

/**
 * Asynchronous event handler for responding to addition/removal events from a running
 * {@link com.emc.storageos.coordinator.client.service.DistributedLockQueueManager} instance.
 *
 * @author Ian Bibby
 */
public class ControlRequestLockQueueListener implements DistributedLockQueueEventListener<ControlRequest> {
    private static final Logger log = LoggerFactory.getLogger(ControlRequestLockQueueListener.class);
    private static final int ORCHESTRATOR_TASK_ID_LENGTH = 36;
    private DbClient dbClient;
    private WorkflowService workflowService;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public void lockQueueEvent(ControlRequest request, Event event) {

        if (event == null) {
            return;
        }

        switch (event) {
            case ADDED:
                nodeAdded(request);
                break;
            case REMOVED:
                nodeRemoved(request);
                break;
        }
    }

    /**
     * Event handler for when a node has been added to the lock queue.
     * Based on the node data, this method attempts to update the status
     * of any associated resources, e.g. tasks and workflow steps.
     *
     * @param request An instance of ControlRequest.
     */
    public void nodeAdded(ControlRequest request) {
        log.info("ControlRequest has been queued");

        if (request == null) {
            log.warn("A null ControlRequest was added.  Ignoring it.");
            return; // nothing to do
        }
        String id = getLastArg(request.getArg());

        if (isWorkflowStepId(id)) {
            id = findTaskIdFromWorkflowStepId(id);
        }

        if (id != null) {
            List<Task> tasks = TaskUtils.findTasksForRequestId(dbClient, id);
            updateTasks(tasks, pendingTasksPredicate(), Task.Status.queued, request);
        }
    }

    public void nodeRemoved(ControlRequest request) {
        log.info("ControlRequest has been de-queued");

        if (request == null) {
            return; // nothing to do
        }
        String id = getLastArg(request.getArg());

        if (isWorkflowStepId(id)) {
            id = findTaskIdFromWorkflowStepId(id);
        }

        if (id != null) {
            request.setLockGroup(NullColumnValueGetter.getNullStr());
            List<Task> tasks = TaskUtils.findTasksForRequestId(dbClient, id);
            updateTasks(tasks, queuedTasksPredicate(), Task.Status.pending, request);
        }
    }

    private String getLastArg(Object[] args) {
        return (String) args[args.length-1];
    }

    /**
     * Updates tasks of a given status with queueing information.
     *
     * @param tasks     List of tasks.
     * @param filter    Filter to act only on tasks of a specific status.
     * @param status    New task status to update with.
     * @param request   ControlRequest instance.
     */
    private void updateTasks(List<Task> tasks, Predicate<Task> filter, Task.Status status, ControlRequest request) {
        Collection<Task> filteredTasks = Collections2.filter(tasks, filter);

        for (Task task : filteredTasks) {
            log.info("Found task {} with status {}", task.getId(), task.getStatus());
            task.setStatus(status.toString());

            if (NullColumnValueGetter.isNotNullValue(request.getLockGroup())) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(request.getTimestamp());
                task.setQueuedStartTime(cal);
                task.setQueueName(request.getLockGroup());
            } else {
                task.setQueueName(NullColumnValueGetter.getNullStr());
                task.setQueuedStartTime(null);
            }
        }
        dbClient.persistObject(tasks);
    }

    private boolean isWorkflowStepId(String id) {
        return id.length() > ORCHESTRATOR_TASK_ID_LENGTH;
    }

    private Predicate<Task> queuedTasksPredicate() {
        return new Predicate<Task>() {

            @Override
            public boolean apply(Task task) {
                return task.isQueued();
            }
        };
    }

    private Predicate<Task> pendingTasksPredicate() {
        return new Predicate<Task>() {

            @Override
            public boolean apply(Task task) {
                return task.isPending();
            }
        };
    }

    /**
     * Given a workflow step id, recursively use the WorkflowService
     * to find the top-most Workflow which would have the real Task id.
     *
     * @param stepId    Step id from a workflow that may potentially be nested itself.
     * @return          The real Task id
     */
    private String findTaskIdFromWorkflowStepId(String stepId) {
        Workflow workflow = workflowService.getWorkflowFromStepId(stepId);

        if (workflow != null && isWorkflowStepId(workflow.getOrchTaskId())) {
            return findTaskIdFromWorkflowStepId(workflow.getOrchTaskId());
        }

        return (workflow == null) ? null : workflow.getOrchTaskId();
    }
}