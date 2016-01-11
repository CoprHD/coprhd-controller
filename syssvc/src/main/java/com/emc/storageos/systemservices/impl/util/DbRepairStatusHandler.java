package com.emc.storageos.systemservices.impl.util;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.db.server.impl.DbRepairRunnable;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.systemservices.impl.recovery.RecoveryManager;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Class for handle node DB repair status combination
 */
public class DbRepairStatusHandler {
    private static final Logger log = LoggerFactory.getLogger(DbRepairStatusHandler.class);

    private List<String> serviceNames = Arrays.asList(Constants.DBSVC_NAME, Constants.GEODBSVC_NAME);

    @Autowired
    private RecoveryManager recoveryManager;

    public DbRepairStatusHandler() {
    }

    private boolean isNodeRecoveryDbRepairInProgress() {
        RecoveryStatus recoveryStatus = recoveryManager.queryNodeRecoveryStatus();
        if (recoveryStatus != null && recoveryStatus.getStatus() != null) {
            return recoveryStatus.getStatus() == RecoveryStatus.Status.REPAIRING;
        }
        return false;
    }

    /**
     * Get node repair status(have combine db repair status and geodb repair status)
     * it's trick to combine local db and geo db repair together since they can be triggered
     * individually, lots for workaround needed to be done to ensure it works correctly.
     * we set IN_PROGRESS before perform actual db repair in DbRepairRunable(before get DB_REPAIR lock)
     * hence we can use the IN_PROGRESS here to determine if there is other pending db repair,
     * so we can determine whether we can merge them together or not. For db repair triggered by scheduler,
     * geo db repair doesn't know if there is local db finished it's work or not since IN_PROGRESS will be
     * set to DONE (which means geo db repair is not aware of it is triggered by restart geo service alone
     * or node restart), we use INTERVAL_TIME_IN_MINUTES to make the decision.
     * Generally we follow the below rules:
     * 1. node recovery: always merge the result such as: local db repair progress 50% itself, 25% will
     * be returned, geo db repair progress 50% itself, 75% will be returned. please
     * be aware of local db repair always come first.
     * 2. node restart: always merge the result, be aware of geo db repair by using IN_PROGRESS flag in
     * local db repair; be aware of local db repair by checking lastCompletionTime of
     * geo db repair against 3 hours
     * 3. restart one db service alone: if you restart db serivce alone, we will return local db repair
     * progress directly without any merge.
     * <p/>
     * Note: we use local db repair as the first instance to grap DB_REPAIR lock, the geo db repair is
     * the second one to run for simply introduction even if it's by chance to get DB_REPAIR lock based
     * on which one bootup first, but it doesn't affect the result.
     */
    public DbRepairStatus getDbRepairStatus() throws Exception {
        DbRepairStatus repairStatus = new DbRepairStatus();
        DbRepairStatus localDbState = queryDbRepairStatus(serviceNames.get(0));
        DbRepairStatus geoDbState = queryDbRepairStatus(serviceNames.get(1));
        boolean nodeRecovery = isNodeRecoveryDbRepairInProgress();
        log.info("Query repair status of dbsvc({}) and geodbsvc({}) successfully",
                (localDbState == null) ? localDbState : localDbState.toString(),
                (geoDbState == null) ? geoDbState : geoDbState.toString());
        log.info("db repair running in node recovery? {}", nodeRecovery);

        if (localDbState == null && geoDbState == null) {
            repairStatus.setStatus(DbRepairStatus.Status.NOT_STARTED);
            return repairStatus;
        }
        if (localDbState != null && geoDbState != null) {
            if (localDbState.getStatus() == DbRepairStatus.Status.IN_PROGRESS && geoDbState.getStatus() == DbRepairStatus.Status.IN_PROGRESS) {
                log.info("local/geo db repair are in progress both");
                repairStatus = getDualProgressStatus(localDbState, geoDbState);
            } else if (localDbState.getStatus() == DbRepairStatus.Status.IN_PROGRESS) {
                log.info("local db repair is in progress");
                repairStatus = getSingleProgressStatus(localDbState, geoDbState, nodeRecovery, false);
            } else if (geoDbState.getStatus() == DbRepairStatus.Status.IN_PROGRESS) {
                log.info("geo db repair is in progress");
                repairStatus = getSingleProgressStatus(geoDbState, localDbState, nodeRecovery, true);
            } else if (localDbState.getStatus() == DbRepairStatus.Status.FAILED || geoDbState.getStatus() == DbRepairStatus.Status.FAILED) {
                log.info("local or geo db repair failed");
                repairStatus = getFailStatus(localDbState, geoDbState);
            } else if (localDbState.getStatus() == DbRepairStatus.Status.SUCCESS && geoDbState.getStatus() == DbRepairStatus.Status.SUCCESS) {
                log.info("local and geo db repair success");
                repairStatus = getSuccessStatus(localDbState, geoDbState);
            }
        }

        if (localDbState == null) {
            repairStatus = geoDbState;
        } else if (geoDbState == null) {
            repairStatus = localDbState;
        }
        log.info("Repair status is: {}", repairStatus.toString());
        return repairStatus;
    }

    private DbRepairStatus getFailStatus(DbRepairStatus localDbState, DbRepairStatus geoDbState) {
        Date startTime;
        if (localDbState.getStatus() == DbRepairStatus.Status.FAILED && geoDbState.getStatus() == DbRepairStatus.Status.FAILED) {
            startTime = getOldestTime(localDbState.getStartTime(), geoDbState.getStartTime());
        } else if (localDbState.getStatus() == DbRepairStatus.Status.FAILED) {
            startTime = localDbState.getStartTime();
        } else {
            startTime = geoDbState.getStartTime();
        }
        return new DbRepairStatus(DbRepairStatus.Status.FAILED, startTime, 100);
    }

    private DbRepairStatus getSuccessStatus(DbRepairStatus localDbState, DbRepairStatus geoDbState) {
        Date completionTime = null;
        if (localDbState.getLastCompletionTime() == null) {
            completionTime = geoDbState.getLastCompletionTime();
        } else if (geoDbState.getLastCompletionTime() == null) {
            completionTime = localDbState.getLastCompletionTime();
        } else {
            completionTime = getLatestTime(localDbState.getLastCompletionTime(), geoDbState.getLastCompletionTime());
        }

        Date startTime = getOldestTime(localDbState.getStartTime(), geoDbState.getStartTime());
        return new DbRepairStatus(DbRepairStatus.Status.SUCCESS, startTime, completionTime, 100);

    }

    /*
     * it's tricky to check isNodeRecovery and isGeoDb, we need this to
     * merge progress in different way between node recovery and normal db repair
     */
    private DbRepairStatus getSingleProgressStatus(DbRepairStatus status, DbRepairStatus otherStatus, boolean isNodeRecovery,
                                                   boolean isGeoDb) {
        Date completionTime = null;
        if (status.getLastCompletionTime() != null && otherStatus.getLastCompletionTime() != null) {
            completionTime = getLatestTime(status.getLastCompletionTime(), otherStatus.getLastCompletionTime());
        }
        int progress = status.getProgress();
        Date startTime = status.getStartTime();
        if (isNodeRecovery) {
            progress = isGeoDb ? (status.getProgress() + 100) / 2 : status.getProgress() / 2;
            startTime = isGeoDb ? otherStatus.getStartTime() : startTime;
        } else if (needMergeWith(otherStatus.getLastCompletionTime())) {
            progress = (status.getProgress() + 100) / 2;
            startTime = otherStatus.getStartTime();
        }

        return new DbRepairStatus(DbRepairStatus.Status.IN_PROGRESS, startTime, completionTime, progress);
    }

    /*
     * we check if db repair need to merge with the other(the other means that geo db if it's a local db)
     * we use 3 hours as the minimum interval, so we view the other as the whole progress of db repair if
     * happened within 3 hours.
     */
    private boolean needMergeWith(Date otherCompletionTime) {
        if (otherCompletionTime == null) {
            return false;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -DbRepairRunnable.INTERVAL_TIME_IN_MINUTES);
        return cal.getTime().before(otherCompletionTime);
    }

    private DbRepairStatus getDualProgressStatus(DbRepairStatus localStatus, DbRepairStatus geoStatus) {
        Date completionTime = null;
        if (localStatus.getLastCompletionTime() != null && geoStatus.getLastCompletionTime() != null) {
            completionTime = getLatestTime(localStatus.getLastCompletionTime(), geoStatus.getLastCompletionTime());
        }
        Date startTime = getOldestTime(localStatus.getStartTime(), geoStatus.getStartTime());
        int progress = (localStatus.getProgress() + geoStatus.getProgress()) / 2;
        return new DbRepairStatus(DbRepairStatus.Status.IN_PROGRESS, startTime, completionTime, progress);
    }

    private Date getOldestTime(Date one, Date another) {
        return one.before(another) ? one : another;
    }

    private Date getLatestTime(Date one, Date another) {
        return one.after(another) ? one : another;

    }

    /**
     * Query repair status of dbsvc or geodbsvc from DB
     */
    private DbRepairStatus queryDbRepairStatus(String svcName) throws Exception {
        int progress = -1;
        DbRepairStatus.Status status = null;
        Date startTime = null;
        Date endTime = null;

        log.info("Try to get repair status of {}", svcName);
        try (DbManagerOps dbManagerOps = new DbManagerOps(svcName)) {
            DbRepairStatus repairState = dbManagerOps.getLastRepairStatus(true);

            if (repairState != null) {
                log.info("Current repair status of {} is: {}", svcName, repairState.toString());
                progress = repairState.getProgress();
                status = repairState.getStatus();
                startTime = repairState.getStartTime();
                endTime = repairState.getLastCompletionTime();
            }

            if (endTime != null) {
                return repairState;
            }

            repairState = dbManagerOps.getLastSucceededRepairStatus(true);

            if (repairState != null) {
                log.info("Last successful repair status of {} is: {}", svcName, repairState.toString());
                progress = (progress == -1) ? repairState.getProgress() : progress;
                status = (status == null) ? repairState.getStatus() : status;
                startTime = (startTime == null) ? repairState.getStartTime() : startTime;
                endTime = (endTime == null) ? repairState.getLastCompletionTime() : endTime;
            }
        }

        if (status != null) {
            return new DbRepairStatus(status, startTime, endTime, progress);
        }

        return null;
    }
}
