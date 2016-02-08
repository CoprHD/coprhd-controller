/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.management.backup.BackupConstants;

/**
 * Class to manage backup file names (tags) using by Backup Scheduler
 */
public class ScheduledBackupTag {

    private static final Logger log = LoggerFactory.getLogger(ScheduledBackupTag.class);

    private static final String BACKUP_TAG_TEMPLATE = "%s-%d-%s";
    private static final String UPLOAD_ZIP_FILENAME_FORMAT = "%s-%s-%s-%s%s";
    private static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(BackupConstants.SCHEDULED_BACKUP_DATE_PATTERN);
        }
    };
    private static final Date MIN_DATE = new Date(0);
    protected static final String ZIP_FILE_SURFIX = ".zip";
    protected static final String BACKUP_TAG_SEPERATOR = "-";
    private static final String INVALID_ZIP_FILE_SURFIX = "-invalid.zip";

    public static Date parseTimestamp(String timestampStr) throws ParseException {
        return dateFormat.get().parse(timestampStr);
    }

    public static String toTimestamp(Date dt) {
        return dateFormat.get().format(dt);
    }

    public static String toBackupTag(Date dt, String ver, int nodeCount) {
        String timestamp = toTimestamp(dt);
        return String.format(BACKUP_TAG_TEMPLATE, ver, nodeCount, timestamp);
    }

    public static Date parseBackupTag(String tag) throws ParseException {
        if (tag == null) {
            throw new ParseException("Can't parse backup date because tag is null", -1);
        }

        int beginIndex = tag.length() - BackupConstants.SCHEDULED_BACKUP_DATE_PATTERN.length();
        if (beginIndex < 0) {
            throw new ParseException("Can't parse backup date from wrong begin index for tag: " + tag, beginIndex);
        }

        return parseTimestamp(tag.substring(beginIndex));
    }

    public static List<String> pickScheduledBackupTags(Collection<String> tags) {
        ArrayList<String> scheduledTags = new ArrayList<>();
        // Typically, this pattern String could match all tags produced by toBackupTag method
        // also in consideration of extension, version part could be longer and node count could bigger
        String regex = String.format(BackupConstants.SCHEDULED_BACKUP_TAG_REGEX_PATTERN, ProductName.getName(),
                BackupConstants.SCHEDULED_BACKUP_DATE_PATTERN.length());
        Pattern backupNamePattern = Pattern.compile(regex);
        for (String tag : tags) {
            if (backupNamePattern.matcher(tag).find()) {
                scheduledTags.add(tag);
            }
        }
        log.info("Scheduled backup tags: {}", scheduledTags);
        return scheduledTags;
    }

    public static class TagComparator implements Comparator<String> {
        private Date parseTagFallback(String tag) {
            // If we cannot extract timestamp from tag, it must be non-scheduled backup. We
            // just treat all manual backups as MIN_DATE, anyway they will be further ordered
            // by raw string value if the timestamps are equal.
            try {
                return ScheduledBackupTag.parseBackupTag(tag);
            } catch (ParseException e) {
                log.info("Can't parse timestamp from the tag of non-scheduled backup({}, errorOffset is at {})",
                        e.getMessage(), e.getErrorOffset());
                return MIN_DATE;
            }
        }

        @Override
        public int compare(String o1, String o2) {
            Date d1 = parseTagFallback(o1);
            Date d2 = parseTagFallback(o2);

            int ret = d1.compareTo(d2);
            return ret != 0 ? ret : o1.compareTo(o2);
        }
    }

    public static String toZipFileName(String tag, int totalNodes, int backupNodes, String siteName) {
        return String.format(UPLOAD_ZIP_FILENAME_FORMAT, tag, totalNodes, backupNodes, siteName, ZIP_FILE_SURFIX);
    }

    public static String toInvalidFileName(String fileName) {
        return fileName.replaceFirst(ZIP_FILE_SURFIX + "$", INVALID_ZIP_FILE_SURFIX);
    }
}
