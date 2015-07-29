/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.coordinator.client.model.ProductName;

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

/**
 * Class to manage backup file names (tags) using by Backup Scheduler
 */
public class ScheduledBackupTag {

    private static final Logger log = LoggerFactory.getLogger(ScheduledBackupTag.class);

    private static final String DATE_PATTERN = "yyyyMMddHHmmss";
    private static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DATE_PATTERN);
        }
    };
    private static final Date MIN_DATE = new Date(0);
    private static final String ZIP_FILE_SURFIX = ".zip";

    public static Date parseTimestamp(String timestampStr) throws ParseException {
        return dateFormat.get().parse(timestampStr);
    }

    public static String toTimestamp(Date dt) {
        return dateFormat.get().format(dt);
    }

    public static String toBackupTag(Date dt, String ver, int nodeCount) {
        String timestamp = toTimestamp(dt);
        return String.format("%s-%d-%s", ver, nodeCount, timestamp);
    }

    public static Date parseBackupTag(String tag) throws ParseException {
        if (tag == null) {
            throw new ParseException("Can't parse backup date because tag is null", -1);
        }

        int beginIndex = tag.length() - DATE_PATTERN.length();
        if (beginIndex < 0) {
            throw new ParseException("Can't parse backup date from wrong begin index for tag: " + tag, beginIndex);
        }

        return parseTimestamp(tag.substring(beginIndex));
    }

    public static List<String> pickScheduledBackupTags(Collection<String> tags) {
        ArrayList<String> scheduledTags = new ArrayList<>();
        StringBuilder backupNamePatternString = new StringBuilder();
        // Typically, this pattern String could match all tags produced by toBackupTag method
        // also in consideration of extension, version part could be longer and node count could bigger
        backupNamePatternString.append('^').append(ProductName.getName())
                .append("-(\\w+|\\.)*\\d+-\\d+-\\d{").append(DATE_PATTERN.length()).append("}$");
        Pattern backupNamePattern = Pattern.compile(backupNamePatternString.toString());
        for (String tag : tags) {
            if (backupNamePattern.matcher(tag).find()) {
                scheduledTags.add(tag);
            }
        }

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
                log.warn("{}, errorOffset is at {}, set backup date as minimal for comparison", e.getMessage(), e.getErrorOffset());
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

    public static String toZipFileName(String tag, int totalNodes, int backupNodes) {
        return String.format("%s-%s-%s%s", tag, totalNodes, backupNodes, ZIP_FILE_SURFIX);
    }
}
