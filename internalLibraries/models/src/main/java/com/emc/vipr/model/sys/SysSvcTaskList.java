/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "syssvc_tasks")
public class SysSvcTaskList {

    private List<SysSvcTask> taskList;

    public SysSvcTaskList() {
    }

    public SysSvcTaskList(List<SysSvcTask> taskList) {
        this.taskList = taskList;
    }

    @XmlElement(name = "syssvc_task")
    public List<SysSvcTask> getTaskList() {
        if (taskList == null) {
            taskList = new ArrayList<SysSvcTask>();
        }
        return taskList;
    }

    public void setTaskList(List<SysSvcTask> taskList) {
        this.taskList = taskList;
    }

    public void addTask(SysSvcTask task) {
        taskList.add(task);
    }
}
