/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.io.Serializable;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalBlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceException;

/**
 * Job checks the progress of an external device driver operation.
 */
abstract public class ExternalDeviceJob extends Job implements Serializable {

    // For serialization interface.
    private static final long serialVersionUID = 1L;

    // The URI of the external storage system on which the task is running.
    private URI _storageSystemURI;
    
    // The id of the task monitored by the job.
    private String _driverTaskId;
    
    // A reference to the task completer
    private TaskCompleter _taskCompleter;
    
    // A reference to the poll result.
    private JobPollResult _pollResult = new JobPollResult();

    // A reference to the job status.
    protected JobStatus _status = JobStatus.IN_PROGRESS;

    // A reference to an error description.
    private String _errorDescription = null;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(ExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public ExternalDeviceJob(URI storageSystemURI, String driverTaskId, TaskCompleter taskCompleter) {
        _storageSystemURI = storageSystemURI;
        _driverTaskId = driverTaskId;
        _taskCompleter = taskCompleter;
    }

    /**
     * {@inheritDoc}
     */
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        s_logger.info("Polled external device job for driver task {} on storage system {}", _driverTaskId, _storageSystemURI);

        DriverTask driverTask = null;
        DbClient dbClient = jobContext.getDbClient();
        try {
            // Update the job info.
            _pollResult.setJobName(_driverTaskId);
            _pollResult.setJobId(_driverTaskId);
            _pollResult.setJobPercentComplete(0);
            
            // Get the external storage system on which the driver task is running.
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, _storageSystemURI);
            if (storageSystem == null) {
                s_logger.error("Could not find external storage system {}", _storageSystemURI);
                throw ExternalDeviceException.exceptions.cantFindStorageSystemForDriverTask(
                        _storageSystemURI.toString(), _driverTaskId);
            }
            String systemType = storageSystem.getSystemType();

            // Get the external storage driver for the system on which the task is executing.
            BlockStorageDriver driver = ExternalBlockStorageDevice.getBlockStorageDriver(systemType);
            if (driver == null) {
                s_logger.error("Could not find storage driver for system type {}", systemType);
                throw ExternalDeviceException.exceptions.noDriverDefinedForDevice(systemType);
            }
            
            // Get the storage driver task.
            driverTask = driver.getTask(_driverTaskId);
            if (driverTask == null) {
                s_logger.error("Could not find storage driver task {} for storage system {}", 
                        _driverTaskId, _storageSystemURI.toString());
                throw ExternalDeviceException.exceptions.cantFindDriverTaskOnSystem(
                        _driverTaskId, _storageSystemURI.toString());
            }
            
            TaskStatus taskStatus = driverTask.getStatus();
            String taskStatusMsg = driverTask.getMessage();
            if (isTaskSuccessful(taskStatus)) {
                // Completed successfully
                s_logger.info(String.format("Task %s completed successfully with status %s:%s",
                        _driverTaskId, taskStatus, taskStatusMsg));
                doTaskSucceeded(driverTask, dbClient);
                _pollResult.setJobPercentComplete(100);
                _status = JobStatus.SUCCESS;
            } else if (isTaskFailed(taskStatus)) {
                // Failed.
                s_logger.info(String.format("Task %s failed with status %s:%s", _driverTaskId,
                        taskStatus, taskStatusMsg));
                doTaskFailed(driverTask, dbClient);
                _pollResult.setJobPercentComplete(100);
                _errorDescription = taskStatusMsg;
                _status = JobStatus.FAILED;
            }
        } catch (Exception e) {
            _errorDescription = e.getMessage();
            s_logger.error(String.format(
                    "Unexpected error getting external driver task status for task %s on storage system %s: s",
                    _driverTaskId, _storageSystemURI.toString(), _errorDescription), e);
            try {
                doTaskFailed(driverTask, dbClient);
            } catch (Exception dtfe) {
                s_logger.error("Unexpected error handling task failed", e);
            }
            _status = JobStatus.FAILED;
        } finally {
            updateStatus(dbClient);
        }

        _pollResult.setJobStatus(_status);
        _pollResult.setErrorDescription(_errorDescription);

        return _pollResult;
    }
    
    /**
     * Determines if the passed status indicates the task was successful.
     * Override in derived classes as appropriate. 
     * 
     * @param taskStatus The task status.
     * 
     * @return true if the task is successful, false otherwise.
     */
    protected boolean isTaskSuccessful(TaskStatus taskStatus) {
        return (TaskStatus.READY == taskStatus || TaskStatus.PARTIALLY_FAILED == taskStatus);
    }
    
    /**
     * Determines if the passed status indicates the task failed.
     * Override in derived classes as appropriate. 
     * 
     * @param taskStatus The task status.
     * 
     * @return true if the task failed, false otherwise.
     */
    protected boolean isTaskFailed(TaskStatus taskStatus) {
        return (TaskStatus.FAILED == taskStatus
                ||TaskStatus.ABORTED == taskStatus
                || TaskStatus.WARNING == taskStatus);
    }    

    /**
     * If necessary, update the task completer after a poll.
     * 
     * @param dbClient A reference to a database client.
     */
    protected void updateStatus(DbClient dbClient) {
        try {
            if (_status == JobStatus.SUCCESS) {
                _taskCompleter.ready(dbClient);
            } else if (_status == JobStatus.FAILED) {
                ServiceCoded sc = ExternalDeviceException.errors.driverTaskFailed(
                        _driverTaskId, _storageSystemURI.toString(), _errorDescription);
                _taskCompleter.error(dbClient, sc);
            }
        } catch (Exception e) {
            s_logger.error(String.format("Unexpected error trying to update completer status for driver task %s on system %s",
                    _driverTaskId, _storageSystemURI.toString()), e);
        }
    }
    
    /**
     * Job specific actions to be done upon successful completion of a task.
     * 
     * @param driverTask A reference to the driver task.
     * @param dbClient A reference to a database client.
     * 
     * @throws Exception
     */
    protected abstract void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) throws Exception;

    /**
     * Job specific actions to be done when a task fails to complete successfully
     * 
     * @param taskStatus A reference to the driver task.
     * @param dbClient A reference to a database client.
     * 
     * @throws Exception
     */
    protected abstract void doTaskFailed(DriverTask driverTask, DbClient dbClient) throws Exception;

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskCompleter getTaskCompleter() {
        return _taskCompleter;
    }
}
