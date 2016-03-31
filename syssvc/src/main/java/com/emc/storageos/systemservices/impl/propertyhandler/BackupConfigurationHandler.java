/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.ScheduleTimeRange;

/**
 * This class serves as an extra validator for the backup related properties
 */
public class BackupConfigurationHandler extends DefaultUpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(BackupConfigurationHandler.class);

    /**
     * Check if backup related configurations conformed to rules, if not fail the property update.
     *
     * @param oldProps
     * @param newProps
     */
    @Override
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        String intervalStr = newProps.getProperty(BackupConstants.SCHEDULE_INTERVAL);
        checkBackupInterval(intervalStr);
    }

    private void checkBackupInterval(String intervalStr) {
        if (intervalStr == null || intervalStr.isEmpty()) {
            _log.warn("Backup interval string is null or empty");
            return;
        }

        // Format is ###$$$, where $$$ is interval unit, and ### represents times of the interval unit
        // E.g. "5day", ###=5, $$$=day.
        int digitLen = 0;
        while (Character.isDigit(intervalStr.charAt(digitLen))) {
            digitLen++;
        }

        String intervalValueStr = intervalStr.substring(0, digitLen);
        int intervalValue = digitLen > 0 ? Integer.parseInt(intervalValueStr) : 0;
        if (intervalValue <= 0) {
            _log.error("The interval value({}) parsed from string({}) is invalid", intervalValueStr, intervalStr);
            throw BadRequestException.badRequests.backupIntervalIsInvalid(intervalStr);
        }

        String intervalUnitStr = intervalStr.substring(digitLen);
        try {
            ScheduleTimeRange.ScheduleInterval intervalUnit = ScheduleTimeRange.parseInterval(intervalUnitStr);
        } catch (Exception ex) {
            _log.error("The interval unit({}) parsed from string({}) is invalid", intervalUnitStr, intervalStr);
            throw BadRequestException.badRequests.backupIntervalIsInvalid(intervalStr);
        }
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
