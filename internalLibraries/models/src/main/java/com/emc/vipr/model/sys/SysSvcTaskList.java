/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
