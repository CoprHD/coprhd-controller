package com.emc.storageos.systemservices.impl.dbrepair;

import java.util.Calendar;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.server.impl.DbRepairJobState;
import com.emc.storageos.db.server.impl.DbRepairRunnable;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.vipr.model.sys.recovery.RecoveryConstants;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;
import com.emc.vipr.model.sys.recovery.DbRepairStatus.Status;

public class RepairStatusManager {
    private static final String DB_REPAIR_ZPATH = "dbrepair";
    private static final String GEODB_REPAIR_ZPATH = "geodbrepair";
    private static final String LOCAL_DB_NAME = "StorageOS";
    private static final String GEO_DB_NAME = "GeoStorageOS";
    private static final int MAX_REPAIR_RETRY_TIME = 5;

    @Autowired
    private CoordinatorClientExt coordinatorExt;

    private static final Logger log = LoggerFactory.getLogger(RepairStatusManager.class);

    public DbRepairStatus getDbRepairStatus() throws Exception {
        try {
            DbRepairJobState localStatus = getLocalDBRepairStatus();
            DbRepairJobState geoStatus = getGeoDBRepairStatus();
            return mergeStatus(convertToRepairStatus(localStatus), convertToRepairStatus(geoStatus));
        } catch (Exception e) {
            log.error("Error happened when trying to fetch DB and GeoDB repari status", e);
            throw e;
        }
    }

    private DbRepairStatus convertToRepairStatus(DbRepairJobState state) {

        int progress = -1;
        DbRepairStatus.Status status = null;
        Date startTime = null;
        Date endTime = null;

        DbRepairStatus repairState = convertLastRepairStatus(state); // need combine
        if (repairState != null) {
            progress = repairState.getProgress();
            status = repairState.getStatus();
            startTime = repairState.getStartTime();
            endTime = repairState.getLastCompletionTime();
        }

        if (endTime != null) {
            return repairState;
        }

        repairState = convertLastSucceededRepairStatus(state);

        if (repairState != null) {
            progress = (progress == -1) ? repairState.getProgress() : progress;
            status = (status == null) ? repairState.getStatus() : status;
            startTime = (startTime == null) ? repairState.getStartTime() : startTime;
            endTime = (endTime == null) ? repairState.getLastCompletionTime() : endTime;
        }

        if (status != null) {
            return new DbRepairStatus(status, startTime, endTime, progress);
        }

        return null;
    }

    private RecoveryStatus queryNodeRecoveryStatus() {
        RecoveryStatus status = new RecoveryStatus();
        Configuration cfg = coordinatorExt.getCoordinatorClient().queryConfiguration(Constants.NODE_RECOVERY_STATUS,
                Constants.GLOBAL_ID);
        if (cfg != null) {
            String statusStr = cfg.getConfig(RecoveryConstants.RECOVERY_STATUS);
            status.setStatus(RecoveryStatus.Status.valueOf(statusStr));

            String startTimeStr = cfg.getConfig(RecoveryConstants.RECOVERY_STARTTIME);
            if (startTimeStr != null && startTimeStr.length() > 0) {
                status.setStartTime(new Date(Long.parseLong(startTimeStr)));
            }
            String endTimeStr = cfg.getConfig(RecoveryConstants.RECOVERY_ENDTIME);
            if (endTimeStr != null && endTimeStr.length() > 0) {
                status.setEndTime(new Date(Long.parseLong(endTimeStr)));
            }
            String errorCodeStr = cfg.getConfig(RecoveryConstants.RECOVERY_ERRCODE);
            if (errorCodeStr != null && errorCodeStr.length() > 0) {
                status.setErrorCode(RecoveryStatus.ErrorCode.valueOf(errorCodeStr));
            }
        }
        log.info("Recovery status is: {}", status);
        return status;
    }
    private boolean isNodeRecoveryDbRepairInProgress() {
        RecoveryStatus recoveryStatus = queryNodeRecoveryStatus();
        if (recoveryStatus != null && recoveryStatus.getStatus() != null) {
            return recoveryStatus.getStatus() == RecoveryStatus.Status.REPAIRING;
        }
        return false;
    }

    /*
     * Merge local db repaire status and geo db repair status
     * */
    private DbRepairStatus mergeStatus(DbRepairStatus localDbState, DbRepairStatus geoDbState) {
        DbRepairStatus repairStatus = new DbRepairStatus();
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
            if (localDbState.getStatus() == Status.IN_PROGRESS && geoDbState.getStatus() == Status.IN_PROGRESS) {
                log.info("local/geo db repair are in progress both");
                repairStatus = getDualProgressStatus(localDbState, geoDbState);
            } else if (localDbState.getStatus() == Status.IN_PROGRESS) {
                log.info("local db repair is in progress");
                repairStatus = getSingleProgressStatus(localDbState, geoDbState, nodeRecovery, false);
            } else if (geoDbState.getStatus() == Status.IN_PROGRESS) {
                log.info("geo db repair is in progress");
                repairStatus = getSingleProgressStatus(geoDbState, localDbState, nodeRecovery, true);
            } else if (localDbState.getStatus() == Status.FAILED || geoDbState.getStatus() == Status.FAILED) {
                log.info("local or geo db repair failed");
                repairStatus = getFailStatus(localDbState, geoDbState);
            } else if (localDbState.getStatus() == Status.SUCCESS && geoDbState.getStatus() == Status.SUCCESS) {
                log.info("local and geo db repair failed");
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

    private DbRepairStatus getDualProgressStatus(DbRepairStatus localStatus, DbRepairStatus geoStatus) {
        Date completionTime = null;
        if (localStatus.getLastCompletionTime() != null && geoStatus.getLastCompletionTime() != null) {
            completionTime = getLatestTime(localStatus.getLastCompletionTime(), geoStatus.getLastCompletionTime());
        }
        Date startTime = getOldestTime(localStatus.getStartTime(), geoStatus.getStartTime());
        int progress = (localStatus.getProgress() + geoStatus.getProgress()) / 2;
        return new DbRepairStatus(Status.IN_PROGRESS, startTime, completionTime, progress);
    }

    private Date getOldestTime(Date one, Date another) {
        return one.before(another) ? one : another;
    }

    private Date getLatestTime(Date one, Date another) {
        return one.after(another) ? one : another;
    }

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

        return new DbRepairStatus(Status.IN_PROGRESS, startTime, completionTime, progress);
    }
    
    private boolean needMergeWith(Date otherCompletionTime) {
        if (otherCompletionTime == null) {
            return false;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -DbRepairRunnable.INTERVAL_TIME_IN_MINUTES);
        return cal.getTime().before(otherCompletionTime);
    }
    
    private DbRepairStatus getFailStatus(DbRepairStatus localDbState, DbRepairStatus geoDbState) {
        Date startTime;
        if (localDbState.getStatus() == Status.FAILED && geoDbState.getStatus() == Status.FAILED) {
            startTime = getOldestTime(localDbState.getStartTime(), geoDbState.getStartTime());
        } else if (localDbState.getStatus() == Status.FAILED) {
            startTime = localDbState.getStartTime();
        } else {
            startTime = geoDbState.getStartTime();
        }
        return new DbRepairStatus(Status.FAILED, startTime, 100);
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
        return new DbRepairStatus(Status.SUCCESS, startTime, completionTime, 100);

    }

    private DbRepairJobState getLocalDBRepairStatus() {
        String stateKey = String.format("%s-%s", DB_REPAIR_ZPATH, LOCAL_DB_NAME);
        DbRepairJobState state = coordinatorExt.getCoordinatorClient().queryRuntimeState(stateKey, DbRepairJobState.class);
        if (state != null) {
            return state;
        }
        return new DbRepairJobState();
    }

    private DbRepairJobState getGeoDBRepairStatus() {
        String stateKey = String.format("%s-%s", GEODB_REPAIR_ZPATH, GEO_DB_NAME);
        DbRepairJobState state = coordinatorExt.getCoordinatorClient().queryRuntimeState(stateKey, DbRepairJobState.class);
        if (state != null) {
            return state;
        }
        return new DbRepairJobState();
    }

    private DbRepairStatus convertLastRepairStatus(DbRepairJobState state) {
        if (state.getCurrentDigest() != null) {
            if (state.getCurrentRetry() <= MAX_REPAIR_RETRY_TIME) {
                return new DbRepairStatus(DbRepairStatus.Status.IN_PROGRESS,
                        new Date(state.getCurrentStartTime()), null, state.getCurrentProgress());
            } else {
                return new DbRepairStatus(DbRepairStatus.Status.FAILED,
                        new Date(state.getCurrentStartTime()), new Date(state.getCurrentUpdateTime()),
                        state.getCurrentProgress());
            }
        }
        return convertLastSucceededRepairStatus(state);
    }

    private DbRepairStatus convertLastSucceededRepairStatus(DbRepairJobState state) {
        if (state.getLastSuccessDigest() != null) {
            return new DbRepairStatus(DbRepairStatus.Status.SUCCESS,
                    new Date(state.getLastSuccessStartTime()), new Date(state.getLastSuccessEndTime()), 100);
        }
        return null;
    }
}
