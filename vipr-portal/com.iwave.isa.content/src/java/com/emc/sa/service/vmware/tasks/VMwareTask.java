/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.Task;

public class VMwareTask<T> extends ExecutionTask<T> {

    protected void waitForTask(Task task) throws Exception {
        boolean cancel = false;
        long maxTime = System.currentTimeMillis() + (60 * 1000);
        while (!isComplete(task)) {
            Thread.sleep(5000);

            if (System.currentTimeMillis() > maxTime) {
                cancel = true;
                break;
            }
        }

        if (cancel) {
            cancelTask(task);
        }
    }

    private boolean isComplete(Task task) throws Exception {
        TaskInfo info = task.getTaskInfo();
        TaskInfoState state = info.getState();
        if (state == TaskInfoState.success) {
            return true;
        }
        else if (state == TaskInfoState.error) {
            String reason = info.getError().getLocalizedMessage();
            error("Task '%s' failed, reason: %s", getDetail(), StringUtils.defaultIfBlank(reason, "unknown"));
        }
        return false;
    }

    public void cancelTask(Task task) throws Exception {
        if (task == null || task.getTaskInfo() == null) {
            warn("VMware task is null or has no task info. Unable to cancel it.");
        } else {
            TaskInfoState state = task.getTaskInfo().getState();
            if (state == TaskInfoState.queued || state == TaskInfoState.running) {
                info("Cancelling task '%s'", getDetail());
                task.cancelTask();
            }
        }
    }

    public void cancelTaskNoException(Task task) {
        try {
            cancelTask(task);
        } catch (Exception e) {
            error(e, "Error when cancelling VMware task");
            logError("VMwareTask.detail.cancelError");
        }
    }

}
