/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.ScheduleFrequency;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * @author jainm15
 */
public final class FilePolicyServiceUtils {
    private static final Logger _log = LoggerFactory.getLogger(FilePolicyServiceUtils.class);
    private static final int MIN_SNAPSHOT_EXPIRE_TIME = 2;
    private static final int MAX_SNAPSHOT_EXPIRE_TIME = 10;
    private static final long MIN_SNAPSHOT_EXPIRE_SECONDS = 7200;
    private static final long MAX_SNAPSHOT_EXPIRE_SECONDS = 10 * 365 * 24 * 3600;

    private FilePolicyServiceUtils() {

    }

    /**
     * validates whether the schedule policy parameters are valid or not
     * 
     * @param policyScheduleparams - schedule policy parameters
     * @param schedulePolicy - schedulePolicy object to set schedule values
     * @param errorMsg - error message
     * @return true/false
     */
    public static boolean validatePolicySchdeuleParam(FilePolicyScheduleParams policyScheduleparams, FilePolicy schedulePolicy,
            StringBuilder errorMsg) {

        if (policyScheduleparams != null) {

            // check schedule frequency is valid or not
            ArgValidator.checkFieldValueFromEnum(policyScheduleparams.getScheduleFrequency().toUpperCase(), "schedule_frequency",
                    EnumSet.allOf(FilePolicy.ScheduleFrequency.class));

            // validating schedule repeat period
            if (policyScheduleparams.getScheduleRepeat() < 1) {
                errorMsg.append("required parameter schedule_repeat is missing or value: " + policyScheduleparams.getScheduleRepeat()
                        + " is invalid");
                return false;
            }

            // validating schedule time
            String period = " PM";
            int hour;
            int minute;
            boolean isValid = true;
            if (policyScheduleparams.getScheduleTime().contains(":")) {
                String splitTime[] = policyScheduleparams.getScheduleTime().split(":");
                hour = Integer.parseInt(splitTime[0]);
                minute = Integer.parseInt(splitTime[1]);
                if (splitTime[0].startsWith("-") || splitTime[1].startsWith("-")) {
                    isValid = false;
                }
            } else {
                hour = Integer.parseInt(policyScheduleparams.getScheduleTime());
                minute = 0;
            }
            if (isValid && (hour >= 0 && hour < 24) && (minute >= 0 && minute < 60)) {
                if (hour < 12) {
                    period = " AM";
                }
            } else {
                errorMsg.append("Schedule time: " + policyScheduleparams.getScheduleTime() + " is invalid");
                return false;
            }

            ScheduleFrequency scheduleFreq = ScheduleFrequency.valueOf(policyScheduleparams.getScheduleFrequency().toUpperCase());
            switch (scheduleFreq) {

                case DAYS:
                    schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                    schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime() + period);
                    if (schedulePolicy.getScheduleDayOfWeek() != null && !schedulePolicy.getScheduleDayOfWeek().isEmpty()) {
                        schedulePolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                    }
                    if (schedulePolicy.getScheduleDayOfMonth() != null) {
                        schedulePolicy.setScheduleDayOfMonth(0L);
                    }
                    break;
                case WEEKS:
                    schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                    if (policyScheduleparams.getScheduleDayOfWeek() != null && !policyScheduleparams.getScheduleDayOfWeek().isEmpty()) {
                        List<String> weeks = Arrays.asList("monday", "tuesday", "wednesday", "thursday", "friday",
                                "saturday", "sunday");
                        if (weeks.contains(policyScheduleparams.getScheduleDayOfWeek().toLowerCase())) {
                            schedulePolicy.setScheduleDayOfWeek(policyScheduleparams.getScheduleDayOfWeek().toLowerCase());
                        } else {
                            errorMsg.append("Schedule day of week: " + policyScheduleparams.getScheduleDayOfWeek() + " is invalid");
                            return false;
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_week is missing or empty");
                        return false;
                    }
                    schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime() + period);
                    if (schedulePolicy.getScheduleDayOfMonth() != null) {
                        schedulePolicy.setScheduleDayOfMonth(0L);
                    }
                    break;
                case MONTHS:
                    if (policyScheduleparams.getScheduleDayOfMonth() != null
                            && policyScheduleparams.getScheduleDayOfMonth() > 0 && policyScheduleparams.getScheduleDayOfMonth() <= 31) {
                        schedulePolicy.setScheduleDayOfMonth((long) policyScheduleparams.getScheduleDayOfMonth());
                        schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                        schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime() + period);
                        if (schedulePolicy.getScheduleDayOfWeek() != null) {
                            schedulePolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_month is missing or value is invalid");
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    public static void validateSnapshotPolicyParam(FileSnapshotPolicyParam param) {
        boolean isValidSnapshotExpire;

        // check snapshot expire type is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getSnapshotExpireParams().getExpireType().toUpperCase(), "expire_type",
                EnumSet.allOf(FilePolicy.SnapshotExpireType.class));

        isValidSnapshotExpire = validateSnapshotExpireParam(param.getSnapshotExpireParams());
        if (!isValidSnapshotExpire) {
            int expireTime = param.getSnapshotExpireParams().getExpireValue();
            _log.error("Invalid schedule snapshot expire time {}. Try an expire time between {} hours to {} years",
                    expireTime, MIN_SNAPSHOT_EXPIRE_TIME, MAX_SNAPSHOT_EXPIRE_TIME);
            throw APIException.badRequests.invalidScheduleSnapshotExpireValue(expireTime, MIN_SNAPSHOT_EXPIRE_TIME,
                    MAX_SNAPSHOT_EXPIRE_TIME);
        }
    }

    /**
     * validates whether the snapshot expire parameters are valid or not
     * 
     * @param expireParam - snapshot expire parameters
     * @return true/false
     */
    private static boolean validateSnapshotExpireParam(FileSnapshotPolicyExpireParam expireParam) {
        long seconds;
        int expireValue = expireParam.getExpireValue();
        SnapshotExpireType expireType = SnapshotExpireType.valueOf(expireParam.getExpireType().toUpperCase());
        switch (expireType) {
            case HOURS:
                seconds = TimeUnit.HOURS.toSeconds(expireValue);
                break;
            case DAYS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue);
                break;
            case WEEKS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue * 7);
                break;
            case MONTHS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue * 30);
                break;
            case NEVER:
                return true;
            default:
                return false;
        }
        if (seconds >= MIN_SNAPSHOT_EXPIRE_SECONDS && seconds <= MAX_SNAPSHOT_EXPIRE_SECONDS) {
            return true;
        }
        return false;
    }

    /**
     * Check if the vpool supports provided policy type
     * 
     * @param filepolicy
     * @param virtualPool
     * @return
     */
    public static void validateVpoolSupportPolicyType(FilePolicy filepolicy, VirtualPool virtualPool) {
        FilePolicyType policyType = FilePolicyType.valueOf(filepolicy.getFilePolicyType());
        StringBuilder errorMsg = new StringBuilder();
        switch (policyType) {
            case file_snapshot:
                if (virtualPool.isFileSnapshotSupported()) {
                    break;
                } else {
                    errorMsg.append("Provided vpool :" + virtualPool.getId().toString() + " doesn't support file snapshot policy.");
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            case file_replication:
                if (virtualPool.isFileReplicationSupported()) {
                    break;
                } else {
                    errorMsg.append("Provided vpool :" + virtualPool.getId().toString() + " doesn't support file replication policy");
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            default:
                return;
        }
    }

}
