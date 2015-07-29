/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.audit;

import java.text.MessageFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import com.emc.storageos.services.util.AlertsLogger;

/**
 * Utility class to track various stats regarding requests. This class
 * is thread safe.
 */
public class RequestStatTracker {

    private static final Logger _log = LoggerFactory.getLogger(RequestStatTracker.class);
    private AlertsLogger _alertLog = AlertsLogger.getAlertsLogger();
    private static final int SUSPISCIOUS_RSP_TIME_MS = 5 * 1000;
    private static final int HIGH_NUMBER_REQUESTS = 10;
    private static final String STAT_THREAD_NAME = "RequestStats";
    private ScheduledExecutorService executor;

    /**
     * terminates scheduled threads executor
     */
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
            _log.debug("Shutting down request stat tracker threads");
        }
    }

    // COUNTERS AND AVG/SAMPLES -------------------------------------------------------------
    // Conventions to help read the code and variable names:
    // "lst" == last == element as it is being computed so far since the last reset
    // "prv" == previous == element as it has been computed at the end of the cycle that completed
    // For example: - #request last minute means == # requests since the last time we reset the
    // minute counter. This value changes every time a request comes in.
    // - #request previous minutes means == we reset the minute counter. In the previous
    // minute, there is now a finite #requests that occurred there. This value is
    // unchanged until the next reset. (i.e for the first minute of life, this value
    // will be zero.
    private AtomicInteger _activeRequests = new AtomicInteger(0); // active requests at any point in time
    private AtomicInteger _reqsPrevMin = new AtomicInteger(0);
    private AtomicInteger _reqsLstMin = new AtomicInteger(0);
    private AtomicInteger _500ErrorsLstHr = new AtomicInteger(0); // # 500 and 503 errors since service startup
    private ThreadLocal<Long> _currentRequestStartTime = new ThreadLocal<Long>() {
        @Override
        protected Long initialValue() {
            return new Long(0);
        }
    };

    /**
     * helper class to hold a running total of all the occurences of the item to average,
     * the number of samples, and the avg of all the items, computed at the end of the cycle(minute, hour or day)
     */
    private class SampledValues {
        public float _total = 0;
        public int _samples = 0;
        public float _avg = 0;
    }

    // request response time avg/min
    private volatile SampledValues _reqRspPerMin = new SampledValues();

    // request response time avg of the (avg/min) in previous hr
    private volatile SampledValues _reqRspPerMinHr = new SampledValues();

    // # requests per minute avg in previous hr
    private volatile SampledValues _reqPerMinHr = new SampledValues();

    // # requests per minute, avg per hour, avg for the previous day
    private volatile SampledValues _reqPerMinHrDay = new SampledValues();

    // min/max response time since last service startup
    private volatile int _minReqRspMs = 0;
    private volatile int _maxReqRspMs = 0;

    private boolean _statsOn = false;
    private int _displayRateInMins = 15; // every 15 minutes by default

    /**
     * Determines how frequently the stats are displayed in the logs.
     * This is independant from how the stats are captured/computed.
     * 
     * @param seconds
     */
    public void setDisplayRateInMins(int mins) {
        _displayRateInMins = mins;
    }

    // STATS INIT AND THREADS ---------------------------------------------------------------

    /**
     * Turns the stat engine on
     */
    public synchronized void init() {
        if (_statsOn) {
            return;
        }
        _statsOn = true;

        // scheduled threads
        executor = Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory(STAT_THREAD_NAME));
        executor.scheduleAtFixedRate(new StatLogger(), _displayRateInMins, _displayRateInMins,
                TimeUnit.MINUTES);
        // add one second initial delay to all processing jobs to avoid overlaps with
        // display job.
        executor.scheduleAtFixedRate(new PerMinuteProcessing(), 61, 60, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(new PerHourProcessing(), 3601, 3600, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(new PerDayProcessing(), 86401, 86400, TimeUnit.SECONDS);
    }

    /**
     * Main stat logger thread. Will log stats at intervals defined by _displayRateInMls
     */
    private class StatLogger implements Runnable {
        @Override
        public void run() {
            _log.info("BEGIN REQUEST STATS: ");
            _log.info("# of active requests: {}", _activeRequests.get());
            _log.info("# of requests previous minute: {}", _reqsPrevMin.get());
            _log.info("# of requests last minute: {}", _reqsLstMin.get());
            _log.info("Request avg response time previous minute: {} ms", _reqRspPerMin._avg);
            _log.info("Request avg response time previous hour: {} ms", _reqRspPerMinHr._avg);
            _log.info("Min/max response time since startup: {}ms/{}ms", _minReqRspMs, _maxReqRspMs);
            _log.info("Avg request per minute in the previous hour: {}", _reqPerMinHr._avg);
            _log.info("Avg request per minute avg per hour in the previous day: {}", _reqPerMinHrDay._avg);
            _log.info("# of 500 and 503 errors in the last hour: {}", _500ErrorsLstHr.get());
            _log.info("END REQUEST STATS: ");
        }
    }

    /**
     * Thread that performs the following each minute:
     * - Updates request per minute in the last hour with the latest
     * count of request per minute
     * - Resets the requests per minute counter
     * - Resets the request response time average in the last minute
     */
    private class PerMinuteProcessing implements Runnable {
        @Override
        public void run() {
            _reqsPrevMin.set(_reqsLstMin.get());
            updateOrResetReqPerMinLstHr(_reqsPrevMin.get(), false);
            _reqsLstMin.set(0);
            updateOrResetReqRspAvgLstMin(0, true);
        }
    }

    /**
     * Thread that performs the following every hour:
     * - Resets request response time average in the last hour
     * - Resets request per minute in the last hour, and updates the request per minute per hour per day counter
     * - Resets the 500/503 error counter
     */
    private class PerHourProcessing implements Runnable {
        @Override
        public void run() {
            updateOrResetReqRspAvgLstHr(0, true);
            updateOrResetReqPerMinPerHrLstDay(updateOrResetReqPerMinLstHr(0, true), false);
            _500ErrorsLstHr.set(0);
        }
    }

    /**
     * Thread that performs the following every day:
     * - resets request per minute per hour in the last day
     */
    private class PerDayProcessing implements Runnable {
        @Override
        public void run() {
            updateOrResetReqPerMinPerHrLstDay(0, true);
        }
    }

    // PUBLIC CALLS -------------------------------------------------------------------
    /**
     * Increments the number of active requests.
     * If the number of active requests gets above HIGH_NUMBER_REQUESTS, logs a warning.
     * This usually indicates overload or an exception situation that wasn't handled
     * properly.
     */
    public void incrementActiveRequests() {
        if (!_statsOn) {
            return;
        }
        if (_activeRequests.incrementAndGet() >= HIGH_NUMBER_REQUESTS) {
            _alertLog.warn(MessageFormat.format("Number of concurrent requests is high: {0}.  Avg response time recently is: {1}ms",
                    _activeRequests, _reqRspPerMin._avg));
        }
        _reqsLstMin.incrementAndGet();
    }

    /**
     * Decrements the active requests counter.
     */
    public void decrementActiveRequests() {
        if (!_statsOn) {
            return;
        }
        _activeRequests.decrementAndGet();
    }

    /**
     * Increment the 500/503 error counter.
     */
    public void flag500Error() {
        if (!_statsOn) {
            return;
        }
        _500ErrorsLstHr.incrementAndGet();
    }

    /**
     * returns the number of concurrently active requests.
     * 
     * @return
     */
    public int getActiveRequests() {
        return _activeRequests.get();
    }

    /**
     * returns the number of 500/503 errors in the last hour.
     * 
     * @return
     */
    public int get500Errors() {
        return _500ErrorsLstHr.get();
    }

    /**
     * Saves the start time of the request for the local thread
     */
    public void recordStartTime() {
        _currentRequestStartTime.set(System.currentTimeMillis());
    }

    /**
     * Computes the response time of the request that just finished for the local thread
     * and updates the request response average in the last minute, and propagates to the
     * request response per minute per hour counter.
     * Updates min/max response time.
     * If the response is above SUSPICIOUS_RESP_TIME_MS, log a warning.
     */
    public void recordEndTime() {
        int msDiff = (int) (System.currentTimeMillis() - _currentRequestStartTime.get());
        _log.info("Request response time: {} ms", msDiff);
        updateOrResetReqRspAvgLstHr(updateOrResetReqRspAvgLstMin(msDiff, false), false);
        updateMinMaxReqRspMs(msDiff);
        if (msDiff >= SUSPISCIOUS_RSP_TIME_MS) {
            _alertLog.warn(MessageFormat.format("Request took an unusually long time to complete: {0}ms", msDiff));
        }
        _currentRequestStartTime.remove();
    }

    // INTERNAL COUNTER COMPUTE METHODS --------------------------------------------------

    /**
     * Updates the min and max response time indicators
     * 
     * @param val
     */
    private synchronized void updateMinMaxReqRspMs(int val) {
        if (_minReqRspMs == 0 || _maxReqRspMs == 0) {
            _minReqRspMs = _minReqRspMs == 0 ? val : _minReqRspMs;
            _maxReqRspMs = _maxReqRspMs == 0 ? val : _maxReqRspMs;
        } else if (val < _minReqRspMs) {
            _minReqRspMs = val;
        } else if (val > _maxReqRspMs) {
            _maxReqRspMs = val;
        }
    }

    /**
     * Utility function that resets a SampledValues internal members to start a new cycle, or
     * Increments its holding total and samples counter.
     * 
     * @param val the value to increment
     * @param reset true to reset, false otherwise (val is ignored then)
     * @param smpl (the SampledValues object onwhich to perform the update or reset)
     * @return
     */
    private float updateOrResetSampledValues(float val, boolean reset, SampledValues smpl) {
        if (!reset) {
            smpl._total += val;
            smpl._samples++;
        } else {
            // compute the average then reset average
            if (smpl._samples > 0) {
                smpl._avg = smpl._total / smpl._samples;
                smpl._total = 0f;
                smpl._samples = 0;
            } else {
                smpl._avg = 0f;
            }
        }
        return smpl._avg;
    }

    /**
     * Updates or resets request response time per minute
     * 
     * @param diff response time
     * @param reset whether we are resetting the counter or not
     * @return in case of a reset, we are returning the new average
     */
    private synchronized float updateOrResetReqRspAvgLstMin(int diff, boolean reset) {
        return updateOrResetSampledValues(diff, reset, _reqRspPerMin);
    }

    /**
     * Updates or resets the request response time average per minute in last hour
     * 
     * @param avg request resp avg per minute
     * @param reset whether we are resetting the counter or not
     */
    private synchronized void updateOrResetReqRspAvgLstHr(float avg, boolean reset) {
        updateOrResetSampledValues(avg, reset, _reqRspPerMinHr);
    }

    /**
     * Updates or resets the request per minute in the last hour counter
     * 
     * @param incr number of requests per minute in the minute that just finished
     * @param reset whether we are resetting the counter or not
     * @return the average of request per minute in the previous hour
     */
    private synchronized float updateOrResetReqPerMinLstHr(float incr, boolean reset) {
        return updateOrResetSampledValues(incr, reset, _reqPerMinHr);
    }

    /**
     * Updates or resets the request per minute per hour in the last day counter
     * 
     * @param incr number of request per minute avg per hour in the last day
     * @param reset
     */
    private synchronized void updateOrResetReqPerMinPerHrLstDay(float incr, boolean reset) {
        updateOrResetSampledValues(incr, reset, _reqPerMinHrDay);
    }
}
