/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: alaplante
 * Date: 9/23/14
 * Time: 2:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class VcenterTaskMonitor {

    enum TaskStatus {
        ERROR, SUCCESS, TIMED_OUT
    }

    private final static Logger _log = LoggerFactory.getLogger(VcenterHostCertificateGetter.class);

    String errorDescription; // only set on error
    int progressPercent; // only set on timeout

    int statefulTimeout = 0;
    int count = 0;

    // Stateful within instance - Count is not reset on each invocation
    public VcenterTaskMonitor(int aTimeout) {
        statefulTimeout = aTimeout;
    }

    /*
     * Call will block until terminal state or timeout is met
     */
    public TaskStatus monitor(Task task) throws Exception {
        TaskInfo taskInfo = null;
        try {
            _log.info("Monitor task " + task);
            while (count < statefulTimeout) {
                count += 1;
                taskInfo = task.getTaskInfo(); // MUST DO THIS IN LOOP SINCE THIS FETCHES LATEST INFO FROM VCENTER
                // Super verbose so dont _log
                // _log.debug("${taskInfo.getName()} task state is " + taskInfo.getState())
                // _log.debug("${taskInfo.getName()} task progress is " + taskInfo.getProgress())
                if (taskInfo.getState() == TaskInfoState.error) {
                    if (taskInfo.getError() != null) {
                        errorDescription = taskInfo.getError().getLocalizedMessage();
                    } else {
                        errorDescription = "No description provided";
                    }
                    _log.error("error description " + errorDescription);
                    return TaskStatus.ERROR;
                } else if (taskInfo.getState() == TaskInfoState.success) {
                    return TaskStatus.SUCCESS;
                } else {
                    if (taskInfo.getProgress() != null) {
                        progressPercent = taskInfo.getProgress();
                    } else {
                        progressPercent = 0;
                    }
                }
                Thread.sleep(1000);
            }
            _log.error(taskInfo.getName() + " task timed out at state " + taskInfo.getState());
            return TaskStatus.TIMED_OUT;
        } catch (Exception ex) {
            _log.error("Error occurred in task monitor ", ex);
            _log.info("task " + task);
            _log.info("taskInfo " + taskInfo);
            throw new Exception("Error occurred in task monitor " + ex);
        }
    }

    // Used to wait for a number of tasks within a set of time
    public void waitForTask(Task task) throws Exception {
        TaskInfo taskInfo = null;
        try {
            _log.info("Monitor task " + task);
            taskInfo = task.getTaskInfo();  // accessing it.taskInfo refreshes
            while ((taskInfo.state == TaskInfoState.running || taskInfo.state == TaskInfoState.queued) && count < statefulTimeout) {
                _log.info("Wait for " + taskInfo.getState() + " task " + taskInfo.getName() + " " + taskInfo.getDescription()
                        + " to reach terminal state");
                Thread.sleep(1000); // check state every second, wait up until afterCompletionWait
                taskInfo = task.getTaskInfo();
                count = count + 1;
            }
        } catch (Exception ex) {
            _log.error("Error occurred in task waitForTask ", ex);
            _log.info("task " + task);
            _log.info("taskInfo " + taskInfo);
            throw new Exception("Error occurred in task monitor waitForTask " + ex);
        }
    }

    public int getCount() {
        return count;
    }
}
