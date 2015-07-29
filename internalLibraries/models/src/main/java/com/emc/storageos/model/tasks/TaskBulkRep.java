/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tasks;

import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.TaskResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_tasks")
public class TaskBulkRep extends BulkRestRep {

    private List<TaskResourceRep> tasks;

    public TaskBulkRep() {
    }

    public TaskBulkRep(List<TaskResourceRep> tasks) {
        this.tasks = tasks;
    }

    /**
     * List of tasks
     */
    @XmlElement(name = "task")
    public List<TaskResourceRep> getTasks() {
        if (tasks == null) {
            tasks = new ArrayList<TaskResourceRep>();
        }
        return tasks;
    }

    public void setTasks(List<TaskResourceRep> tasks) {
        this.tasks = tasks;
    }
}
