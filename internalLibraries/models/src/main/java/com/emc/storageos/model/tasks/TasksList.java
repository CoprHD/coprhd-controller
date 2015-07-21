/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tasks;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name="tasks_ids")
public class TasksList {
    private List<NamedRelatedResourceRep> tasks;

    public TasksList() {}

    public TasksList(List<NamedRelatedResourceRep> tasks) {
        this.tasks = tasks;
    }

    /**
     * List of projects
     * @valid none
     * @return
     */
    @XmlElement(name = "task")
    public List<NamedRelatedResourceRep> getTasks() {
        if (tasks == null) {
            tasks = new ArrayList<NamedRelatedResourceRep>();
        }
        return tasks;
    }

    public void setTasks(List<NamedRelatedResourceRep> tasks) {
        this.tasks = tasks;
    }
}
