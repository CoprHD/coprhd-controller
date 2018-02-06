/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.job;

import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.emc.storageos.vplexcontroller.job.VPlexMigrationJob;

/**
 * QueueJobTracker tracks jobs in the job queue
 */
public class QueueJobTracker extends DistributedQueueConsumer<QueueJob> implements Runnable
{
    private static final Logger _logger = LoggerFactory.getLogger(QueueJobTracker.class);
    private long _trackingPeriodInMillis;
    private long _trackingTimeoutInMillis;

    private ExecutorService _trackerService = null;
    private ConcurrentLinkedQueue<JobWrapper> _activeJobs = new ConcurrentLinkedQueue<JobWrapper>();
    private JobContext _jobContext;

    private class JobWrapper
    {
        Job _job;
        DistributedQueueItemProcessedCallback _cb;

        public JobWrapper(Job job, DistributedQueueItemProcessedCallback cb) {
            _job = job;
            _cb = cb;
        }

        public Job getJob() {
            return _job;
        }

        public DistributedQueueItemProcessedCallback getJobDoneCallback() {
            return _cb;
        }
    }

    public void setJobContext(JobContext jobContext) {
        _jobContext = jobContext;
    }

    public void start() {
        _trackerService = Executors.newSingleThreadExecutor();
        _trackerService.submit(this);
    }

    public void consumeItem(QueueJob job, DistributedQueueItemProcessedCallback cb) {
        // If the job did not specify a timeout, use the default timeout for the tracking system,
        // which is set in startup in ControllerServiceImpl from a controllersvc config parameter.
        // Otherwise, use the timeout specified by the Job.
        if (job.getJob().getTimeoutTimeMsec() == null) {
            job.getJob().setTimeoutTimeMsec(getTrackingTimeoutInMillis());
        }
        _activeJobs.add(new JobWrapper(job.getJob(), cb));
    }

    public void run() {
        HashMap<String, HashMap<String, Integer>> jobProgressMap = new HashMap<String, HashMap<String, Integer>>();
        while (true) {
            JobWrapper jobWrapper = null;
            _logger.debug("Tracker: Will check job status after {} ms...", _trackingPeriodInMillis);
            try {
                ArrayList<String> completedJobs = new ArrayList<String>();
                Thread.sleep(_trackingPeriodInMillis);
                _logger.debug("Tracker: Checking status of {} jobs", _activeJobs.size());
                for (Iterator<JobWrapper> iter = _activeJobs.iterator(); iter.hasNext();) {
                    jobWrapper = iter.next();
                    Job job = jobWrapper.getJob();
                    try {
                        setPollingStartTime(job);
                        JobPollResult result = job.poll(_jobContext, _trackingPeriodInMillis);
                        updateJobProgress(jobProgressMap, result);

                        boolean stopJobTracking = false;
                        String msg = null;
                        // Check if we have to stop job tracking.
                        if (result.isJobInTerminalState()) {
                            // stop tracking jobs in final status and final post processing status
                            msg = String.format("Tracker: Stopping tracking job %s with status: %s and post-processing status %s",
                                    result.getJobId(), result.getJobStatus(), result.getJobPostProcessingStatus());
                            stopJobTracking = true;
                        } else {
                            long trackingTime = System.currentTimeMillis() - job.getPollingStartTime();
                            if (trackingTime > job.getTimeoutTimeMsec()) {
                                // Stop tracking job if maximum job tracking time was reached.
                                msg = String.format("Tracker: Stopping tracking job %s with status: %s and post-processing status %s .\n" +
                                        "The job tracking time reached job tracking time limit %d hours, job tracking time %d hours.",
                                        result.getJobId(), result.getJobStatus(), result.getJobPostProcessingStatus(),
                                         job.getTimeoutTimeMsec() / (60 * 60 * 1000),
                                        trackingTime / (60 * 60 * 1000));
                                _logger.info(msg);
                                String errorMsg = String.format(
                                        "Could not execute job %s on backend device. Exceeded time limit for job status tracking.",
                                        result.getJobName());
                                if (job instanceof VPlexMigrationJob) {
                                    errorMsg = String
                                            .format(
                                                    "Could not execute VPlex Migration Job %s on backend device. Exceeded time limit for VPLEX migration timeout.",
                                                    result.getJobName());
                                }
                                ServiceError error = DeviceControllerException.errors.unableToExecuteJob(errorMsg);
                                job.getTaskCompleter().error(_jobContext.getDbClient(), error);
                                stopJobTracking = true;
                            }
                        }
                        if (stopJobTracking) {
                            _logger.info(msg);
                            stopTrackingJob(jobWrapper);
                            completedJobs.add(result.getJobId());
                        }
                    } catch (Exception ex) {
                        _logger.error("Tracker: Unexpected exception.", ex);
                    }
                }
                if (!jobProgressMap.isEmpty()) {
                    _logger.info(String.format("Progress of jobs - %n %s", jobProgressMap.toString()));
                }
                removeCompletedJobProgressItems(jobProgressMap, completedJobs);
            } catch (InterruptedException ie) {
                _logger.info("Tracker: Unexpected Interrupted exception.", ie);
            } catch (Exception e) {
                _logger.info("Tracker: Unexpected exception.", e);
            }
        }
    }

    private void updateJobProgress(HashMap<String, HashMap<String, Integer>> jobProgressMap, JobPollResult result) {
        HashMap<String, Integer> jobInstancesForJobName = jobProgressMap.get(result.getJobName());
        if (jobInstancesForJobName == null) {
            jobInstancesForJobName = new HashMap<String, Integer>();
            jobProgressMap.put(result.getJobName(), jobInstancesForJobName);
        }
        jobInstancesForJobName.put(result.getJobId(), Integer.valueOf(result.getJobPercentComplete()));
    }

    private void removeCompletedJobProgressItems(HashMap<String, HashMap<String, Integer>> jobProgressMap,
            ArrayList<String> completedJobs) {
        for (String jobId : completedJobs) {
            Iterator<String> jobProgressMapIter = jobProgressMap.keySet().iterator();
            while (jobProgressMapIter.hasNext()) {
                HashMap<String, Integer> jobProgressItemMap = jobProgressMap.get(jobProgressMapIter.next());
                if (jobProgressItemMap.containsKey(jobId)) {
                    jobProgressItemMap.remove(jobId);
                    if (jobProgressItemMap.isEmpty()) {
                        jobProgressMapIter.remove();
                    }
                    break;
                }
            }
        }
    }

    private void stopTrackingJob(JobWrapper jobWrapper) {
        try {
            _activeJobs.remove(jobWrapper);
            jobWrapper.getJobDoneCallback().itemProcessed();
        } catch (Exception e) {
            _logger.info("Tracker: Problem while stopping job tracking.", e);
        }
    }

    public long getTrackingPeriodInMillis() {
        return _trackingPeriodInMillis;
    }

    public void setTrackingPeriodInMillis(long trackingPeriodInMillis) {
        this._trackingPeriodInMillis = trackingPeriodInMillis;
    }

    private void setPollingStartTime(Job job) {
        if (job.getPollingStartTime() == 0L) {
            // set job polling start time
            job.setPollingStartTime(System.currentTimeMillis());
        }
    }

    @Override
    public boolean isBusy(String queue) {
        return false;
    }

    public void setTrackingTimeout(long trackingTimeoutInMillis) {
        this._trackingTimeoutInMillis = trackingTimeoutInMillis;
    }

    public long getTrackingTimeoutInMillis() {
        return _trackingTimeoutInMillis;
    }
}
