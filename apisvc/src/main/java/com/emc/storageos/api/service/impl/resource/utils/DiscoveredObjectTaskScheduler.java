/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * Template base object that encapsulate scheduling of Scan/Discover tasks.
 * Task can be scheduled in 2 ways. All at once as required for scan jobs.
 * One at the time distributed across multiple nodes as for discover jobs.
 */
public class DiscoveredObjectTaskScheduler {

    private DbClient _dbClient;
    private AsyncTaskExecutorIntf _taskExecutor;

    public DiscoveredObjectTaskScheduler(DbClient dbClient, AsyncTaskExecutorIntf taskExec) {
        _dbClient = dbClient;
        _taskExecutor = taskExec;
    }

    public TaskList scheduleAsyncTasks(List<AsyncTask> tasks) {

        TaskList list = new TaskList();
        for (AsyncTask task : tasks) {
            DataObject discoveredObject =
                    (DataObject) _dbClient.queryObject(task._clazz, task._id);
            Operation op = new Operation();
            op.setResourceType(_taskExecutor.getOperation());
            _dbClient.createTaskOpStatus(task._clazz, task._id, task._opId, op);
            list.getTaskList().add(toTask(discoveredObject, task._opId, op));
        }
        try {
            _taskExecutor.executeTasks(tasks.toArray(new AsyncTask[tasks.size()]));
        } catch (ControllerException | APIException ex) {
            for (AsyncTask task : tasks) {
                DataObject discoveredObject =
                        (DataObject) _dbClient.queryObject(task._clazz, task._id);
                Operation op = _dbClient.error(task._clazz, task._id, task._opId, ex);
                list.getTaskList().add(toTask(discoveredObject, task._opId, op));
            }
        }
        return list;
    }
}
