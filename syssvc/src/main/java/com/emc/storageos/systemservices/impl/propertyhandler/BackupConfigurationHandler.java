/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.ScheduleTimeRange;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.ScheduleTimeRange.ScheduleInterval;

/**
 * This class serves as an extra validator for the backup related properties
 */
public class BackupConfigurationHandler extends DefaultUpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(BackupConfigurationHandler.class);
    private ScheduleInterval intervalUnit;
    private int intervalValue;
    private int startTime;

    /**
     * Check if backup related configurations conformed to rules, if not fail the property update.
     *
     * @param oldProps
     * @param newProps
     */
    @Override
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        String newIntervalStr = newProps.getProperty(BackupConstants.SCHEDULE_INTERVAL);
        String newStartTimeStr = newProps.getProperty(BackupConstants.SCHEDULE_TIME);
        if ((newIntervalStr == null || newIntervalStr.isEmpty())
                && (newStartTimeStr == null || newStartTimeStr.isEmpty())){
            _log.info("No changes in backup interval and start time");
            return;
        }

        String intervalStr = (newIntervalStr == null || newIntervalStr.isEmpty())
                ? oldProps.getProperty(BackupConstants.SCHEDULE_INTERVAL) : newIntervalStr;
        String startTimeStr = (newStartTimeStr == null || newStartTimeStr.isEmpty())
                ? oldProps.getProperty(BackupConstants.SCHEDULE_TIME) : newStartTimeStr;

        _log.info("intervalStr={}, startTimeStr={}", intervalStr, startTimeStr);
        validateBackupIntervalAndStartTime(intervalStr, startTimeStr);
    }

    private void validateBackupInterval(String intervalStr) {
        if (intervalStr == null || intervalStr.isEmpty()) {
            _log.error("Backup interval string is null or empty");
            throw BadRequestException.badRequests.backupIntervalIsInvalid(intervalStr);
        }

        // Format is ###$$$, where $$$ is interval unit, and ### represents times of the interval unit
        // E.g. "5day", ###=5, $$$=day.
        int digitLen = 0;
        while (Character.isDigit(intervalStr.charAt(digitLen))) {
            digitLen++;
        }

        String intervalValueStr = intervalStr.substring(0, digitLen);
        this.intervalValue = digitLen > 0 ? Integer.parseInt(intervalValueStr) : 0;
        if (this.intervalValue <= 0) {
            _log.error("The interval value({}) parsed from string({}) is invalid", intervalValueStr, intervalStr);
            throw BadRequestException.badRequests.backupIntervalIsInvalid(intervalStr);
        }

        String intervalUnitStr = intervalStr.substring(digitLen);
        try {
            this.intervalUnit = ScheduleTimeRange.parseInterval(intervalUnitStr);
        } catch (Exception ex) {
            _log.error("The interval unit({}) parsed from string({}) is invalid", intervalUnitStr, intervalStr);
            throw BadRequestException.badRequests.backupIntervalIsInvalid(intervalStr);
        }
    }

    private void validateBackupStartTime(String startTimeStr) {
        if (startTimeStr == null || startTimeStr.isEmpty()) {
            _log.error("Backup start time string is null or empty");
            throw BadRequestException.badRequests.backupStartTimeIsInvalid(startTimeStr);
        }

        // Format is ...dddHHmm
        this.startTime = Integer.parseInt(startTimeStr);
        if (this.startTime < 0) {
            _log.error("The backup start time parsed from string({}) is invalid", startTime, startTimeStr);
            throw BadRequestException.badRequests.backupStartTimeIsInvalid(startTimeStr);
        }
    }

    private void validateBackupIntervalAndStartTime(String intervalStr, String startTimeStr) {
        boolean unsupportedInterval = false;
        boolean unsupportedStartTime = false;

        validateBackupInterval(intervalStr);
        validateBackupStartTime(startTimeStr);

        switch (intervalUnit) {
            case DAY:
                if (intervalValue != 1) {
                    unsupportedInterval = true;
                }
                if (getStartTimeInMins(startTime) >= intervalValue * TimeUtils.DAYS/TimeUtils.MINUTES) {
                    unsupportedStartTime = true;
                }
                break;
            case HOUR:
                if (intervalValue != 12) {
                    unsupportedInterval = true;
                }
                if (getStartTimeInMins(startTime) >= intervalValue * TimeUtils.HOURS/TimeUtils.MINUTES) {
                    unsupportedStartTime = true;
                }
                break;
            case MINUTE:
                if (intervalValue >= 60) {
                    unsupportedInterval = true;
                    _log.error("Please select proper unit, value should be less than 60 for unit MINUTE");
                } else {
                    _log.warn("Invalid scheduled backup interval left for internal test: {}", intervalStr);
                }

                if (getStartTimeInMins(startTime) >= intervalValue) {
                    unsupportedStartTime = true;
                }
                break;
            default:
                unsupportedInterval = true;
                _log.error("Currently we just support scheduled backup interval unit as DAY or HOUR");
        }

        if (unsupportedInterval) {
            _log.error("Invalid backup interval:{}, currently we only support '1day' and '12hour'", intervalStr);
            throw BadRequestException.badRequests.backupIntervalIsInvalid(intervalStr);
        }
        if (unsupportedStartTime) {
            _log.error("Start time({}) should be less than backup interval({})", startTimeStr, intervalStr);
            throw BadRequestException.badRequests.backupStartTimeIsInvalid(startTimeStr);
        }
    }

    private int getStartTimeInMins(int startTime) {
        int minute = startTime % 100;
        int startTimeInHour = startTime / 100;
        int hour = startTimeInHour % 100;
        int day = startTimeInHour / 100;

        return (day * 24 + hour) * 60 + minute;
    }

    /**
     * Do nothing
     *
     * @param oldProps
     * @param newProps
     */
    @Override
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
    }
}
