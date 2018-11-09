/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.TaskResourceRep;

import play.mvc.Router;
import util.TagUtils;
import util.TaskUtils;
import util.datatable.DataTable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class TasksDataTable extends DataTable {
    //Maximum latest tasks to be displayed in the table
    public static final int TASK_MAX_COUNT = 10000;

    // Currently the backend only shows progresses of 0 or 100, so for show this as the miminum progress
    private static final int MINIMUM_TASK_PROGRESS = 10;

    public TasksDataTable() {
        setupTable(false);
    }

    public TasksDataTable(boolean addResourceColumn) {
        setupTable(addResourceColumn);
    }

    private void setupTable(boolean addResourceColumn) {
        addColumn("creationTime").hidden().setSearchable(false);
        addColumn("systemName").hidden();
        addColumn("id").hidden().setSearchable(false);
        addColumn("orderNumber").hidden();
        addColumn("name");
        if (addResourceColumn) {
            addColumn("resourceId").setSearchable(false).setRenderFunction("render.taskResource");
            addColumn("resourceName").hidden();
        }
        addColumn("taskProgress").setSearchable(false).setRenderFunction("render.taskProgress");
        addColumn("state").setRenderFunction("render.taskState");
        addColumn("displayState").hidden();
        addColumn("start").setRenderFunction("render.taskStart");
        addColumn("elapsed").setRenderFunction("render.taskElapsed");
        setDefaultSort("start", "desc");

        setRowCallback("createRowLink");
    }

    public static List<Task> fetch(URI resourceId) {
        if (resourceId == null) {
            return Collections.EMPTY_LIST;
        }

        List<TaskResourceRep> clientTasks = TaskUtils.getTasks(resourceId);

        List<Task> dataTableTasks = Lists.newArrayList();
        if (clientTasks != null) {
            for (TaskResourceRep clientTask : clientTasks) {
                dataTableTasks.add(new Task(clientTask));
            }
        }
        return dataTableTasks;
    }

    public static class Task {
        public String name;
        public String rowLink;
        public String resourceId;
        public String resourceType;
        public String resourceName;
        public String id;
        public String state;
        public String displayState;
        public String description;
        public String message;
        public Long start;
        public Long end;
        public Long creationTime;
        public Integer progress = 0;
        public Integer errorCode;
        public String errorCodeDescription;
        public String errorDetails;
        public String orderId;
        public String orderNumber;

        public Task(TaskResourceRep taskResourceRep) {
            load(taskResourceRep);
        }

        public Task(com.emc.vipr.client.Task<?> clientTask) {
            load(clientTask.getTaskResource());
        }

        private void load(TaskResourceRep taskResourceRep) {
            if (taskResourceRep.getServiceError() != null) {
                this.errorCode = taskResourceRep.getServiceError().getCode();
                this.errorCodeDescription = taskResourceRep.getServiceError().getCodeDescription();
                this.errorDetails = taskResourceRep.getServiceError().getDetailedMessage();
            }
            this.name = taskResourceRep.getName();
            if (taskResourceRep.getCreationTime() != null) {
                this.creationTime = taskResourceRep.getCreationTime().getTimeInMillis();
            }
            this.id = taskResourceRep.getId().toString();
            if (taskResourceRep.getResource() != null && taskResourceRep.getResource().getId() != null) {
                this.resourceId = taskResourceRep.getResource().getId().toString();
                this.resourceType = ResourceType.fromResourceId(this.resourceId).toString();
                this.resourceName = taskResourceRep.getResource().getName();
            }
            this.state = taskResourceRep.getState();
            this.displayState = Objects.equals(this.state, "ready") ? "complete" : this.state;

            this.description = taskResourceRep.getDescription();
            this.message = taskResourceRep.getMessage();
            if (taskResourceRep.getStartTime() != null) {
                this.start = taskResourceRep.getStartTime().getTimeInMillis();
            }
            if (taskResourceRep.getEndTime() != null) {
                // Add 1 millisecond as some tasks start/end at the exact same time which doesn't show on UI
                this.end = taskResourceRep.getEndTime().getTimeInMillis() + 1;
            }

            if (taskResourceRep.getProgress() != null) {
                this.progress = Math.max(taskResourceRep.getProgress(), MINIMUM_TASK_PROGRESS);
            }

            this.orderId = TagUtils.getOrderIdTagValue(taskResourceRep);
            this.orderNumber = TagUtils.getOrderNumberTagValue(taskResourceRep);

            // Create Row Link
            Map<String, Object> args = Maps.newHashMap();
            args.put("taskId", id);
            this.rowLink = Router.reverse("Tasks.details", args).url;

            // Temporary Fix since ERROR tasks don't show as complete
            if (Objects.equals(taskResourceRep.getState(), "error")) {
                progress = 100;
            }
        }
    }
}
