/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.toServiceCode;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceErrorFactory.toServiceErrorRestRep;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Workflow;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.Lists;

public class TaskMapper {
    private static Logger log = LoggerFactory.getLogger(TaskMapper.class);

    private static TaskMapperConfig configInstance = null;

    public static class TaskMapperConfig {
        private DbClient dbClient;

        public TaskMapperConfig() {
        }

        public DbClient getDbClient() {
            return dbClient;
        }

        public void setDbClient(DbClient dbClient) {
            this.dbClient = dbClient;
        }

        @PostConstruct
        public void setupStatic() {
            if (TaskMapper.configInstance != null) {
                log.warn("Updating TaskMapper configuration static when one is already set");
            }
            TaskMapper.configInstance = this;
        }

        @PreDestroy
        public void destroyStatic() {
            TaskMapper.configInstance = null;
        }
    }

    private static TaskMapperConfig getConfig() {
        if (configInstance == null) {
            throw new RuntimeException("TaskMapperConfiguration instance has not been set");
        }
        return configInstance;
    }

    public static TaskResourceRep toTask(DataObject resource, String taskId) {
        if (resource.getOpStatus() == null) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("status");
        }
        Operation op = resource.getOpStatus().get(taskId);

        if (op == null) {
            throw APIException.badRequests.invalidParameterNoOperationForTaskId(taskId);
        }

        return toTask(resource, taskId, op);
    }

    public static TaskResourceRep toTask(Task task) {
        TaskResourceRep taskResourceRep = new TaskResourceRep();
        mapDataObjectFields(task, taskResourceRep);

        taskResourceRep.setId(task.getId());
        taskResourceRep.setResource(toNamedRelatedResource(task.getResource()));

        // Check to see if there are any associated resources
        List<NamedRelatedResourceRep> associatedRefs = Lists.newArrayList();
        for (URI assocId : task.getAssociatedResourcesList()) {
            DataObject associatedObject = getConfig().getDbClient().queryObject(assocId);
            if (associatedObject != null) {
                associatedRefs.add(toNamedRelatedResource(associatedObject));
            } else {
                log.warn(String.format("For task %s could not find associated object %s", task.getId(), assocId));
            }
        }

        taskResourceRep.setAssociatedResources(associatedRefs);

        if (!StringUtils.isBlank(task.getRequestId())) {
            taskResourceRep.setOpId(task.getRequestId());
        }

        if (task.getWorkflow() != null) {
            taskResourceRep.setWorkflow(toRelatedResource(ResourceTypeEnum.WORKFLOW, task.getWorkflow()));
        }

        if (!task.getTenant().equals(TenantOrg.SYSTEM_TENANT)) {
            taskResourceRep.setTenant(DbObjectMapper.toRelatedResource(ResourceTypeEnum.TENANT, task.getTenant()));
        }

        // Operation
        taskResourceRep.setState(task.getStatus());
        if (task.getServiceCode() != null) {
            taskResourceRep.setServiceError(toServiceErrorRestRep(toServiceCode(task.getServiceCode()),
                    task.getMessage()));
        } else {
            taskResourceRep.setMessage(task.getMessage());
            if (!task.getWarningMessages().isEmpty()) {
            	taskResourceRep.setWarningMessages(new ArrayList<String>(task.getWarningMessages()));
            }
        }
        taskResourceRep.setDescription(task.getDescription());

        // COP-23486
        //
        // This is a workaround to migration post-commit delete source volumes. We would like to be able to
        // mark this Task as one that cannot be rolled back, however at the time there is no framework to
        // detect the state of not being able to rollback, so we will catch this specific situation from the
        // message so we can "flip the flag" of allowable operations by the UI.
        taskResourceRep.setAllowedOperations(Task.AllowedOperations.none_specified.name());
        if (task.getWorkflow() != null) {
            Workflow wf = configInstance.getDbClient().queryObject(Workflow.class, task.getWorkflow());
            if (wf != null && NullColumnValueGetter.isNotNullValue(wf.getCompletionMessage()) 
                    && wf.getCompletionMessage().contains("post-migration delete of original source backing volumes")) {
                taskResourceRep.setAllowedOperations(Task.AllowedOperations.retry_only.name());
            }
        }
        taskResourceRep.setStartTime(task.getStartTime());
        taskResourceRep.setEndTime(task.getEndTime());
        taskResourceRep.setProgress(task.getProgress() != null ? task.getProgress() : 0);
        taskResourceRep.setQueuedStartTime(task.getQueuedStartTime());
        taskResourceRep.setQueueName(task.getQueueName());

        // update migration status of the consistency group
        if (task.getResource() != null) {
            URI resourceId = task.getResource().getURI();
            if (URIUtil.isType(resourceId, Migration.class)) {
                Migration migration = getConfig().getDbClient().queryObject(Migration.class, resourceId);
                if (migration != null) {
                    taskResourceRep.setMigrationStatus(migration.getMigrationStatus());
                }
            }
        }

        return taskResourceRep;
    }

    /**
     * Generate a task that is a complete state. This could be used for cases where the operation
     * does not need to go the controller. That is, it's completed within in the API layer.
     * 
     * @param resource
     *            [in] - DataObject, ViPR model object
     * @param taskId
     *            [in] - String task identifier
     * @param operation
     *            [in] - Operation
     * @return TaskResourceRep representing a Task that is completed.
     */
    public static TaskResourceRep toCompletedTask(DataObject resource, String taskId, Operation operation) {
        Task task = operation.getTask(resource.getId());
        if (task != null) {
            task.setProgress(100);
            task.setStatus(Operation.Status.ready.name());
            getConfig().getDbClient().persistObject(task);
            return toTask(task);
        } else {
            // It wasn't recently serialized, so fallback to looking for the task in the DB
            task = TaskUtils.findTaskForRequestId(getConfig().getDbClient(), resource.getId(), taskId);
            if (task != null) {
                task.setProgress(100);
                task.setStatus(Operation.Status.ready.name());
                getConfig().getDbClient().persistObject(task);
                return toTask(task);
            } else {
                throw new IllegalStateException(String.format(
                        "Task not found for resource %s, op %s in either the operation or the database", resource.getId(), taskId));
            }
        }
    }

    public static TaskResourceRep toTask(DataObject resource, String taskId, Operation operation) {
        // If the Operation has been serialized in this request, then it should have the corresponding task embedded in
        // it
        Task task = operation.getTask(resource.getId());
        if (task != null) {
            return toTask(task);
        } else {
            // It wasn't recently serialized, so fallback to looking for the task in the DB
            task = TaskUtils.findTaskForRequestId(getConfig().getDbClient(), resource.getId(), taskId);
            if (task != null) {
                return toTask(task);
            } else {
                throw new IllegalStateException(String.format(
                        "Task not found for resource %s, op %s in either the operation or the database", resource.getId(), taskId));
            }
        }
    }

    public static TaskResourceRep toTask(DataObject resource,
            List<? extends DataObject> assocResources,
            String taskId,
            Operation operation) {
        TaskResourceRep task = toTask(resource, taskId, operation);
        List<NamedRelatedResourceRep> associatedReps = new ArrayList<NamedRelatedResourceRep>();
        for (DataObject assoc : assocResources) {
            associatedReps.add(toNamedRelatedResource(assoc));
        }
        task.setAssociatedResources(associatedReps);
        return task;
    }

    public static TaskList toTaskList(DataObject resource) {
        TaskList list = new TaskList();
        if (resource.getOpStatus() != null) {
            Set<String> task_set = resource.getOpStatus().keySet();
            for (String task : task_set) {
                list.getTaskList().add(toTask(resource, task));
            }
        }
        return list;
    }

    public static TaskList toTaskList(List<Task> tasks) {
        TaskList taskList = new TaskList();

        for (Task task : tasks) {
            taskList.addTask(toTask(task));
        }

        return taskList;
    }
}
