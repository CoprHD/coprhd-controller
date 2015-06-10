/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="tasks")
public class TaskList {

    /**
     * A list of tasks, including the details of each task.
     * @valid none 
     */
    private List<TaskResourceRep> taskList;

    public TaskList() {}
    
    public TaskList(List<TaskResourceRep> taskList) {
        this.taskList = taskList; 
    }

    @XmlElement(name="task")
    public List<TaskResourceRep> getTaskList() {
        if (taskList == null) {
            taskList = new ArrayList<TaskResourceRep>();
        }
        return taskList;
    }

    public void setTaskList(List<TaskResourceRep> taskList) {
        this.taskList = taskList;
    }

    public void addTask(TaskResourceRep task) {
        getTaskList().add(task);
    }
}
