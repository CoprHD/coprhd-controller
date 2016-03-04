/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import com.emc.storageos.services.util.JmxServerWrapper;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.service.ActiveRepairService;
import org.apache.cassandra.service.StorageServiceMBean;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.utils.concurrent.SimpleCondition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Condition;

/**
 * Class handles running repair job and listening for messages related modeled
 * after org.apache.cassandra.tools.NodeProbe.RepairRunner
 */
public class RepairJobRunner implements NotificationListener, AutoCloseable {

    public static interface ProgressNotificationListener {
        public void onStartToken(String token, int progress);
    }

    private static final Logger _log = LoggerFactory
            .getLogger(RepairJobRunner.class);
    private final SimpleDateFormat format = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss,SSS");

    private final Condition condition = new SimpleCondition();

    /**
     * Flag to indicate the job is successful or failed
     */
    private boolean _success = true;

    /**
     * Total number of repair sessions to be executed
     */
    private int _totalRepairSessions = 0;

    /**
     * Number of completed repair sessions
     */
    private int _completedRepairSessions = 0;

    /**
     * Max wait time in minutes for each range repair. Terminate the job
     */
    private int _maxWaitInMinutes = 30;

    /**
     * Start execution time of the job. For time estimation of remaining repair
     * sessions.
     */
    private long _startTimeInMillis = 0L;

    /**
     * Executor to schedule job monitor thread
     */
    private ScheduledExecutorService _exe;

    /**
     * Flag to indicate if current job is aborted
     */
    private boolean _aborted = false;

    /**
     * Token that is successfully repaired
     */
    private String _lastToken = null;

    private ProgressNotificationListener listener;

    private JmxServerWrapper jmxServer;

    private StorageServiceMBean svcProxy;

    private String keySpaceName;

    private String clusterStateDigest;

    /**
     * 
     * @param svcProxy
     *            Reference to Cassandra JMX bean
     * @param keySpaceName
     *            ViPR table name
     * @param exe
     * @param listener
     * @param startToken
     */
    public RepairJobRunner(JmxServerWrapper jmxServer, StorageServiceMBean svcProxy, String keySpaceName, ScheduledExecutorService exe,
            ProgressNotificationListener listener, String startToken, String clusterStateDigest) {
        this.jmxServer = jmxServer;
        this.svcProxy = svcProxy;
        this.keySpaceName = keySpaceName;
        _exe = exe;
        _lastToken = startToken;
        this.listener = listener;
        this.clusterStateDigest = clusterStateDigest;
    }

    public static class StringTokenRange {
        public final String begin;
        public final String end;

        public StringTokenRange(String begin, String end) {
            this.begin = begin;
            this.end = end;
        }
    }

    /**
     * Return sorted primary ranges on local node
     */
    public static List<StringTokenRange> getLocalRanges(String keyspace) {

        Collection<Range<Token>> ranges = StorageService.instance.getLocalRanges(keyspace);
        ArrayList<Range<Token>> sortedRanges = new ArrayList<>();
        sortedRanges.addAll(ranges);
        Collections.sort(sortedRanges);

        ArrayList<StringTokenRange> result = new ArrayList<>();
        Iterator<Range<Token>> iter = sortedRanges.iterator();
        while (iter.hasNext()) {
            Range<Token> range = iter.next();
            List<String> startAndEnd = range.asList();
            if (startAndEnd.size() != 2) {
                _log.error("Illegal local primary range found {}. Stop the db repair",
                        range);

                return null;
            }

            result.add(new StringTokenRange(startAndEnd.get(0), startAndEnd.get(1)));
        }

        return result;
    }

    private static int indexOfRange(List<StringTokenRange> ranges, String token) {
        for (int i = 0; i < ranges.size(); i++) {
            StringTokenRange range = ranges.get(i);
            if (range.end.equals(token)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Execute DB repair job for local primary ranges on DHT ring. If _lastToken is
     * not null, the repair job starts from _lastToken
     * 
     * It is supposed to execute this method on all nodes of the cluster so that
     * full DHT ring is repaired.
     * 
     * @return True for success. Otherwise failure
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean runRepair() throws IOException,
            InterruptedException {
        _startTimeInMillis = System.currentTimeMillis();

        List<StringTokenRange> localRanges = getLocalRanges(this.keySpaceName);
        if (localRanges == null) {
            _success = false;
            return false;
        }

        _totalRepairSessions = localRanges.size();
        if (_totalRepairSessions == 0) {
            _log.info("Nothing to repair for keyspace {}", this.keySpaceName);
            return _success;
        }

        _log.info("Run repair job for {}. Total # local ranges {}",
                this.keySpaceName, _totalRepairSessions);

        // Find start token, in case the token is no longer in ring, we have to start from beginning
        this._completedRepairSessions = this._lastToken == null ? 0 : indexOfRange(localRanges, this._lastToken);
        if (this._completedRepairSessions == -1) {
            _log.error("Recorded last working range \"{}\" is not found, starting from beginning", this._lastToken);
            this._completedRepairSessions = 0;
        } else {
            _log.info("Last token is {}, progress is {}%, starting repair from token #{}",
                    new Object[] { this._lastToken, getProgress(), this._completedRepairSessions });
        }

        ScheduledFuture<?> jobMonitorHandle = startMonitor(svcProxy);
        try {
            _aborted = false;
            _success = true;
            while (_completedRepairSessions < _totalRepairSessions) {

                String currentDigest = DbRepairRunnable.getClusterStateDigest();
                if (!this.clusterStateDigest.equals(currentDigest)) {
                    _log.error("Cluster state changed from {} to {}, repair failed", this.clusterStateDigest, currentDigest);
                    _success = false;
                    break;
                }

                StringTokenRange range = localRanges.get(this._completedRepairSessions);

                this.listener.onStartToken(range.end, getProgress());

                int cmd = svcProxy.forceRepairRangeAsync(range.begin, range.end, this.keySpaceName,
                        true, false, true);

                _log.info("Wait for repairing this range to be done cmd={}", cmd);
                if (cmd > 0) {
                    condition.await();
                }

                _log.info("Repair this range is done success={}", _success);

                if (!_success) {
                    _log.error("Fail to repair range {} {}. Stopping the job",
                            range.begin, range.end);
                    break;
                }

                _lastToken = range.end;
                _completedRepairSessions++;
                _log.info("{} repair sessions finished. Current progress {}%", _completedRepairSessions, getProgress());
            }
        } finally {
            jobMonitorHandle.cancel(false);
            _log.info("Stopped repair job monitor");
        }

        // Reset lastToken after a successful full repair of local primary ranges
        if (_success) {
            _lastToken = null;
        }

        long repairSeconds = (System.currentTimeMillis() - _startTimeInMillis) / 1000;
        _log.info("Db repair consumes {} ",repairSeconds > 5 * 60 ?
                repairSeconds / 60 + " minutes" : repairSeconds + " seconds");
        return _success;
    }

    /**
     * Start background task to monitor job progress. If job could not move
     * ahead for _maxWaitInMinutes, the job is thought as hanging and we force
     * to abort the whole repair.
     * 
     * @param svcProxy
     *            Reference to Cassandra JMX bean
     * @return Future object
     */
    protected ScheduledFuture<?> startMonitor(final StorageServiceMBean svcProxy) {
        ScheduledFuture<?> jobMonitorHandle = _exe.scheduleAtFixedRate(
                new Runnable() {
                    long _lastProgress = 0L;
                    long _lastCheckMillis = 0L;

                    @Override
                    public void run() {
                        int progress = getProgress();
                        long currentMillis = System.currentTimeMillis();
                        _log.info("Monitor repair job progress {} last progress {} ",
                                progress, _lastProgress);
                        if (_lastCheckMillis == 0L) {
                            _lastCheckMillis = currentMillis;
                        }
                        if (progress == _lastProgress) {
                            long delta = (currentMillis - _lastCheckMillis) / 60000;
                            if (delta > _maxWaitInMinutes) {
                                _log.info(
                                        "Repair job hangs for {} minutes. Abort it",
                                        delta);
                                svcProxy.forceTerminateAllRepairSessions();
                                _aborted = true;
                            }
                        } else {
                            _lastProgress = progress;
                            _lastCheckMillis = currentMillis;
                        }
                    }
                }, 1, 1, TimeUnit.MINUTES);
        return jobMonitorHandle;
    }

    /**
     * Get execution percentage of db repair job
     * 
     * @return 0 - 100 to indicate a job is running. -1 to indicate job not
     *         started
     */
    public int getProgress() {
        if (_totalRepairSessions > 0) {
            return _completedRepairSessions * 100 / _totalRepairSessions;
        }
        return -1;
    }

    /**
     * Get job start time in milliseconds since epoc.
     * 
     * @return
     */
    public long getStartTimeInMillis() {
        return this._startTimeInMillis;
    }

    /**
     * Handle DB repair request notification from Cassandra JMX bean
     */
    public void handleNotification(Notification notification, Object handback) {
        if ("repair".equals(notification.getType())) {
            int[] status = (int[]) notification.getUserData();
            if (status.length == 2) {
                String message = String.format("Repair notification [%s] %s",
                        format.format(notification.getTimeStamp()),
                        notification.getMessage());
                _log.info(message);
                // repair status is int array with [0] = cmd number, [1] =
                // status
                if (status[1] == ActiveRepairService.Status.SESSION_FAILED
                        .ordinal()) {
                    _log.info("Repair session failed");
                    _success = false;
                } else if (status[1] == ActiveRepairService.Status.FINISHED
                        .ordinal()) {

                    _log.info("Repair session finished");
                    if (_aborted) {
                        _success = false;
                    }
                    condition.signalAll();
                }
            } else {
                _log.error("Unexpected notification: status.length {}", status.length);
            }
        } else if (JMXConnectionNotification.NOTIFS_LOST.equals(notification.getType()))
        {
            String message = String.format("[%s] Lost notification. You should check server log for repair status of keyspace %s",
                                           format.format(notification.getTimeStamp()),
                                           keySpaceName);
            _log.error(message);
        }
        else if (JMXConnectionNotification.FAILED.equals(notification.getType())
                 || JMXConnectionNotification.CLOSED.equals(notification.getType()))
        {
            String message = String.format("JMX connection closed. You should check server log for repair status of keyspace %s"
                                           + "(Subsequent keyspaces are not going to be repaired).",
                                           keySpaceName);
            _log.error(message);
            condition.signalAll();
        }
    }

    @Override
    public void close() throws Exception {
        _log.info("remove listener");
        svcProxy.removeNotificationListener(this);
        jmxServer.removeConnectionNotificationListener(this);
    }
}
