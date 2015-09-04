package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.coordinator.client.service.DistributedLockQueueEventListener;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueManagerImpl.Event;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isBlank;

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

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
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
            // TODO Find stepId -> taskId
            log.warn("Updating task statuses for queued workflow steps is not yet supported");
        }

        List<Task> tasks = TaskUtils.findTasksForRequestId(dbClient, id);
        updateTasks(tasks, pendingTasksPredicate(), Task.Status.queued, request.getTimestamp());
    }

    public void nodeRemoved(ControlRequest request) {
        log.info("ControlRequest has been de-queued");

        if (request == null) {
            return; // nothing to do
        }
        String id = getLastArg(request.getArg());

        if (isWorkflowStepId(id)) {
            // TODO Find stepId -> taskId
            log.warn("Updating task statuses for queued workflow steps is not yet supported");
        }

        List<Task> tasks = TaskUtils.findTasksForRequestId(dbClient, id);
        updateTasks(tasks, queuedTasksPredicate(), Task.Status.pending);
    }

    private String getLastArg(Object[] args) {
        return (String) args[args.length - 1];
    }

    private void updateTasks(List<Task> tasks, Predicate<Task> filter, Task.Status status) {
        updateTasks(tasks, filter, status, null);
    }

    /**
     * Updates tasks of a given status with queueing information. If a timestamp is provided, the message
     * property is updated to include the time a task was first queued.
     *
     * @param tasks List of tasks.
     * @param filter Filter to act only on tasks of a specific status.
     * @param status New task status to update with.
     * @param timestamp Timestamp of when these tasks were first queued (optional).
     */
    private void updateTasks(List<Task> tasks, Predicate<Task> filter, Task.Status status, Long timestamp) {
        Collection<Task> filteredTasks = Collections2.filter(tasks, filter);

        for (Task task : filteredTasks) {
            log.info("Found task {} with status {}", task.getId(), task.getStatus());
            task.setStatus(status.toString());

            if (timestamp != null) {
                String msg = task.getMessage();
                if (isBlank(msg)) {
                    task.setMessage(String.format("Queued at %d", timestamp));
                } else {
                    task.setMessage(String.format("%s (Queued at %d)", msg, timestamp));
                }
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
}